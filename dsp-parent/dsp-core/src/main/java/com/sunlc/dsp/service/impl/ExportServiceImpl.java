package com.sunlc.dsp.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.common.model.ApiRequest;
import com.sunlc.dsp.common.model.RequestHead;
import com.sunlc.dsp.common.service.DataQueryService;
import com.sunlc.dsp.entity.ExportTask;
import com.sunlc.dsp.export.ExportTaskStatus;
import com.sunlc.dsp.export.ExportType;
import com.sunlc.dsp.export.FileFormat;
import com.sunlc.dsp.mapper.ExportTaskMapper;
import com.sunlc.dsp.service.ExportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class ExportServiceImpl extends ServiceImpl<ExportTaskMapper, ExportTask>
        implements ExportService {

    private final DataQueryService dataQueryService;

    @Lazy
    @Autowired
    private ExportService selfProxy;

    public ExportServiceImpl(DataQueryService dataQueryService) {
        this.dataQueryService = dataQueryService;
    }

    @Value("${dsp.export.base-dir:./export-files}")
    private String exportBaseDir;

    @Value("${dsp.export.online-max-rows:100000}")
    private int onlineMaxRows;

    @Value("${dsp.export.chunk-size:10000}")
    private int chunkSize;

    @Override
    public void onlineExport(String transno, Map<String, Object> params, String format, HttpServletResponse response) {
        FileFormat fileFormat = FileFormat.fromExtension(format);
        List<Map<String, Object>> dataList = queryData(transno, params);
        if (dataList.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "查询结果为空，无法导出");
        }
        if (dataList.size() > onlineMaxRows) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "数据量超过" + onlineMaxRows + "行，请使用离线导出");
        }
        try {
            setExportResponseHeader(response, transno, fileFormat);
            switch (fileFormat) {
                case XLSX: writeExcel(dataList, response.getOutputStream()); break;
                case CSV: writeCsv(dataList, response.getOutputStream()); break;
                case TXT: writeTxt(dataList, response.getOutputStream()); break;
            }
        } catch (IOException e) {
            log.error("在线导出写入失败: transno={}", transno, e);
            throw new BusinessException(ErrorCode.EXPORT_ERROR, "导出文件写入失败");
        }
    }

    @Override
    public ExportTask submitOfflineExport(String transno, Map<String, Object> params, String format, String applyUser) {
        ExportTask task = new ExportTask();
        task.setTransno(transno);
        task.setParamsSnapshot(JSONUtil.toJsonStr(params));
        task.setExportType(ExportType.OFFLINE.getCode());
        task.setFileFormat(format.toUpperCase());
        task.setStatus(ExportTaskStatus.PENDING.getCode());
        task.setProgress(0);
        task.setTotalRows(0L);
        task.setApplyUser(applyUser);
        task.setCreatedTime(LocalDateTime.now());
        save(task);
        selfProxy.executeOfflineExport(task.getId(), transno, params, FileFormat.fromExtension(format));
        return task;
    }

    @Override
    public ExportTask getExportTask(Long taskId) { return getById(taskId); }

    @Override
    public Page<ExportTask> listExportTask(Integer pageNum, Integer pageSize, String transno, Integer status) {
        Page<ExportTask> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ExportTask> wrapper = new LambdaQueryWrapper<>();
        if (transno != null && !transno.isEmpty()) {
            wrapper.like(ExportTask::getTransno, transno);
        }
        if (status != null) {
            wrapper.eq(ExportTask::getStatus, status);
        }
        wrapper.orderByDesc(ExportTask::getCreatedTime);
        return page(page, wrapper);
    }

    @Override
    public void downloadExportFile(Long taskId, HttpServletResponse response) {
        ExportTask task = getById(taskId);
        if (task == null) throw new BusinessException(ErrorCode.INTERFACE_NOT_FOUND, "导出任务不存在");
        if (task.getStatus() != ExportTaskStatus.COMPLETED.getCode()) throw new BusinessException(ErrorCode.SYSTEM_ERROR, "导出任务尚未完成");
        File file = new File(task.getFilePath());
        if (!file.exists()) throw new BusinessException(ErrorCode.SYSTEM_ERROR, "导出文件已过期或不存在");
        try {
            setExportResponseHeader(response, task.getTransno(), FileFormat.fromExtension(task.getFileFormat()));
            try (InputStream in = new FileInputStream(file); OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) { out.write(buffer, 0, len); }
                out.flush();
            }
        } catch (IOException e) {
            log.error("导出文件下载失败: taskId={}", taskId, e);
            throw new BusinessException(ErrorCode.EXPORT_ERROR, "文件下载失败");
        }
    }

    @Async
    public void executeOfflineExport(Long taskId, String transno, Map<String, Object> params, FileFormat format) {
        ExportTask task = getById(taskId);
        if (task == null) return;
        task.setStatus(ExportTaskStatus.PROCESSING.getCode());
        updateById(task);
        try {
            FileUtil.mkdir(exportBaseDir);
            String fileName = transno + "_" + taskId + "_" + System.currentTimeMillis();
            String filePath = exportBaseDir + File.separator + fileName + "." + format.getExtension();
            List<Map<String, Object>> dataList = queryData(transno, params);
            writeFile(dataList, filePath, format, task);
            task.setStatus(ExportTaskStatus.COMPLETED.getCode());
            task.setFilePath(filePath);
            task.setTotalRows((long) dataList.size());
            task.setProgress(100);
            task.setFinishedTime(LocalDateTime.now());
            updateById(task);
            log.info("离线导出完成: taskId={}, transno={}, rows={}", taskId, transno, dataList.size());
        } catch (Exception e) {
            log.error("离线导出失败: taskId={}, transno={}", taskId, transno, e);
            task.setStatus(ExportTaskStatus.FAILED.getCode());
            task.setErrorMsg(e.getMessage());
            task.setFinishedTime(LocalDateTime.now());
            updateById(task);
        }
    }

    private void writeExcel(List<Map<String, Object>> dataList, OutputStream outputStream) {
        if (dataList.isEmpty()) return;
        List<String> headers = new ArrayList<>(dataList.get(0).keySet());
        List<List<String>> headList = new ArrayList<>();
        for (String header : headers) headList.add(Collections.singletonList(header));
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : dataList) {
            List<Object> rowData = new ArrayList<>();
            for (String header : headers) rowData.add(row.get(header));
            rows.add(rowData);
        }
        EasyExcel.write(outputStream).head(headList).sheet("数据").doWrite(rows);
    }

    private void writeCsv(List<Map<String, Object>> dataList, OutputStream outputStream) throws IOException {
        if (dataList.isEmpty()) return;
        outputStream.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        List<String> headers = new ArrayList<>(dataList.get(0).keySet());
        writer.println(String.join(",", headers));
        for (Map<String, Object> row : dataList) {
            List<String> values = new ArrayList<>();
            for (String header : headers) {
                Object val = row.get(header);
                String strVal = val != null ? val.toString() : "";
                if (strVal.contains(",") || strVal.contains("\n") || strVal.contains("\""))
                    strVal = "\"" + strVal.replace("\"", "\"\"") + "\"";
                values.add(strVal);
            }
            writer.println(String.join(",", values));
        }
        writer.flush();
    }

    private void writeTxt(List<Map<String, Object>> dataList, OutputStream outputStream) throws IOException {
        if (dataList.isEmpty()) return;
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        List<String> headers = new ArrayList<>(dataList.get(0).keySet());
        writer.println(String.join("\t", headers));
        for (Map<String, Object> row : dataList) {
            List<String> values = new ArrayList<>();
            for (String header : headers) values.add(row.get(header) != null ? row.get(header).toString() : "");
            writer.println(String.join("\t", values));
        }
        writer.flush();
    }

    private void writeFile(List<Map<String, Object>> dataList, String filePath, FileFormat format, ExportTask task) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            switch (format) { case XLSX: writeExcel(dataList, fos); break; case CSV: writeCsv(dataList, fos); break; case TXT: writeTxt(dataList, fos); break; }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> queryData(String transno, Map<String, Object> params) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        RequestHead head = new RequestHead();
        head.setTransno(transno);
        request.setHead(head);
        request.setRequestData(params);
        Object result = dataQueryService.execute(request, transno).getData();
        if (result instanceof List) return (List<Map<String, Object>>) result;
        else if (result instanceof Map) { List<Map<String, Object>> list = new ArrayList<>(); list.add((Map<String, Object>) result); return list; }
        else { List<Map<String, Object>> list = new ArrayList<>(); Map<String, Object> row = new LinkedHashMap<>(); row.put("value", result); list.add(row); return list; }
    }

    private void setExportResponseHeader(HttpServletResponse response, String transno, FileFormat format) throws UnsupportedEncodingException {
        String fileName = URLEncoder.encode(transno + "_" + System.currentTimeMillis(), "UTF-8") + "." + format.getExtension();
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
    }
}
