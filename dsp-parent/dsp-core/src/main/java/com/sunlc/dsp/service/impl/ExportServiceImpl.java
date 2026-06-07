package com.sunlc.dsp.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.common.model.ApiRequest;
import com.sunlc.dsp.common.model.PaginationExportInfo;
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
        task.setProgress(0);
        task.setTotalRows(0L);
        updateById(task);

        String filePath = null;
        Object writer = null;

        try {
            FileUtil.mkdir(exportBaseDir);
            String fileName = transno + "_" + taskId + "_" + System.currentTimeMillis();
            filePath = exportBaseDir + File.separator + fileName + "." + format.getExtension();

            // 通过引擎获取接口主导出查询的分页配置
            PaginationExportInfo pageInfo = dataQueryService.getExportPaginationInfo(transno);
            boolean isCursorMode = pageInfo != null && "cursor".equals(pageInfo.getMode());
            String cursorColumn = isCursorMode ? stripTablePrefix(pageInfo.getOrderBy()) : null;

            long totalRows = 0;
            int pageNum = 1;
            List<String> headers = null;
            Object exportLastId = null;
            Object lastCursorValue = null;
            boolean cursorStalled = false;

            while (true) {
                List<Map<String, Object>> batch = queryDataPage(transno, params, pageNum, chunkSize, exportLastId);
                if (batch.isEmpty()) break;

                if (headers == null) {
                    headers = new ArrayList<>(batch.get(0).keySet());
                    writer = createWriter(filePath, format, headers);
                }

                // Cursor 模式：校验 orderBy 字段存在于结果集，否则任务失败
                if (isCursorMode) {
                    if (!batch.get(0).containsKey(cursorColumn)) {
                        if (writer != null) {
                            try { closeWriter(writer, format); } catch (Exception ignored) {}
                        }
                        task.setStatus(ExportTaskStatus.FAILED.getCode());
                        task.setErrorMsg("游标分页(orderBy=" + pageInfo.getOrderBy() + ")字段\""
                                + cursorColumn + "\"未出现在查询结果中，无法推进游标");
                        task.setFinishedTime(LocalDateTime.now());
                        updateById(task);
                        return;
                    }
                    // 检测 cursor 未推进 → 任务失败（非 break 后 COMPLETED）
                    Object currentCursorValue = batch.get(0).get(cursorColumn);
                    if (lastCursorValue != null && lastCursorValue.equals(currentCursorValue)) {
                        log.warn("导出cursor未推进: taskId={}, orderBy={}, cursorValue={}",
                                taskId, cursorColumn, currentCursorValue);
                        cursorStalled = true;
                        break;
                    }
                    lastCursorValue = currentCursorValue;
                }

                appendBatch(writer, batch, headers, format);
                totalRows += batch.size();

                // 记录 cursor：用 orderBy 字段的末行值推进下一批
                if (isCursorMode) {
                    exportLastId = batch.get(batch.size() - 1).get(cursorColumn);
                }

                // 按批次更新进度
                task.setTotalRows(totalRows);
                task.setProgress(1);
                updateById(task);

                log.debug("导出批次完成: taskId={}, pageNum={}, batchSize={}, totalRows={}",
                        taskId, pageNum, batch.size(), totalRows);

                pageNum++;
            }

            if (totalRows == 0 || cursorStalled) {
                task.setStatus(ExportTaskStatus.FAILED.getCode());
                task.setErrorMsg(cursorStalled
                        ? "游标分页未推进(orderBy=" + cursorColumn + ")，导出中断，已导出" + totalRows + "行"
                        : "查询结果为空，无法导出");
                task.setFinishedTime(LocalDateTime.now());
                updateById(task);
                return;
            }

            closeWriter(writer, format);

            task.setStatus(ExportTaskStatus.COMPLETED.getCode());
            task.setFilePath(filePath);
            task.setTotalRows(totalRows);
            task.setProgress(100);
            task.setFinishedTime(LocalDateTime.now());
            updateById(task);
            log.info("离线导出完成: taskId={}, transno={}, rows={}", taskId, transno, totalRows);

        } catch (Exception e) {
            log.error("离线导出失败: taskId={}, transno={}", taskId, transno, e);
            if (writer != null) {
                try { closeWriter(writer, format); } catch (Exception ignored) {}
            }
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

    /**
     * 分页查询数据 — 注入 _exportPageSize/_exportPageNum/_exportLastId
     * 引擎根据是否有 paginationConfig 自动选择覆盖参数或追加 LIMIT/OFFSET
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> queryDataPage(String transno, Map<String, Object> params, int pageNum, int pageSize, Object exportLastId) {
        Map<String, Object> pagedParams = new LinkedHashMap<>(params);
        pagedParams.put("_exportPageSize", pageSize);
        pagedParams.put("_exportPageNum", pageNum);
        if (exportLastId != null) {
            pagedParams.put("_exportLastId", exportLastId);
        }
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        RequestHead head = new RequestHead();
        head.setTransno(transno);
        request.setHead(head);
        request.setRequestData(pagedParams);
        Object result = dataQueryService.execute(request, transno).getData();
        if (result instanceof List) return (List<Map<String, Object>>) result;
        else if (result instanceof Map) {
            List<Map<String, Object>> list = new ArrayList<>();
            list.add((Map<String, Object>) result);
            return list;
        } else {
            List<Map<String, Object>> list = new ArrayList<>();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("value", result);
            list.add(row);
            return list;
        }
    }

    /** 去掉表前缀：t.id → id，id → id */
    static String stripTablePrefix(String columnName) {
        if (columnName == null) return null;
        int dotIndex = columnName.lastIndexOf('.');
        return dotIndex >= 0 ? columnName.substring(dotIndex + 1) : columnName;
    }

    // ==================== 流式写入方法（离线导出用） ====================

    private Object createWriter(String filePath, FileFormat format, List<String> headers) throws IOException {
        switch (format) {
            case XLSX:
                List<List<String>> headList = new ArrayList<>();
                for (String header : headers) headList.add(Collections.singletonList(header));
                return EasyExcel.write(filePath).head(headList).build();
            case CSV:
                BufferedWriter csvWriter = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(filePath), StandardCharsets.UTF_8));
                csvWriter.write("﻿");
                csvWriter.write(String.join(",", headers));
                csvWriter.newLine();
                return csvWriter;
            case TXT:
                BufferedWriter txtWriter = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(filePath), StandardCharsets.UTF_8));
                txtWriter.write(String.join("\t", headers));
                txtWriter.newLine();
                return txtWriter;
            default:
                throw new IllegalArgumentException("不支持的导出格式: " + format);
        }
    }

    private void appendBatch(Object writer, List<Map<String, Object>> batch, List<String> headers, FileFormat format) throws IOException {
        switch (format) {
            case XLSX:
                ExcelWriter excelWriter = (ExcelWriter) writer;
                List<List<Object>> rows = new ArrayList<>(batch.size());
                for (Map<String, Object> row : batch) {
                    List<Object> rowData = new ArrayList<>(headers.size());
                    for (String header : headers) rowData.add(row.get(header));
                    rows.add(rowData);
                }
                WriteSheet sheet = EasyExcel.writerSheet("数据").build();
                excelWriter.write(rows, sheet);
                break;
            case CSV:
                BufferedWriter csvWriter = (BufferedWriter) writer;
                for (Map<String, Object> row : batch) {
                    List<String> values = new ArrayList<>(headers.size());
                    for (String header : headers) {
                        Object val = row.get(header);
                        String strVal = val != null ? val.toString() : "";
                        if (strVal.contains(",") || strVal.contains("\n") || strVal.contains("\""))
                            strVal = "\"" + strVal.replace("\"", "\"\"") + "\"";
                        values.add(strVal);
                    }
                    csvWriter.write(String.join(",", values));
                    csvWriter.newLine();
                }
                csvWriter.flush();
                break;
            case TXT:
                BufferedWriter txtWriter = (BufferedWriter) writer;
                for (Map<String, Object> row : batch) {
                    List<String> values = new ArrayList<>(headers.size());
                    for (String header : headers)
                        values.add(row.get(header) != null ? row.get(header).toString() : "");
                    txtWriter.write(String.join("\t", values));
                    txtWriter.newLine();
                }
                txtWriter.flush();
                break;
        }
    }

    private void closeWriter(Object writer, FileFormat format) throws IOException {
        switch (format) {
            case XLSX:
                ((ExcelWriter) writer).finish();
                break;
            case CSV:
            case TXT:
                ((BufferedWriter) writer).close();
                break;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> queryData(String transno, Map<String, Object> params) {
        // 过滤导出专用参数，防止在线查询路径被污染
        Map<String, Object> cleanParams = new LinkedHashMap<>(params);
        cleanParams.keySet().removeIf(key -> key.startsWith("_export"));
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        RequestHead head = new RequestHead();
        head.setTransno(transno);
        request.setHead(head);
        request.setRequestData(cleanParams);
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
