package com.sunlc.dsp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunlc.dsp.entity.ExportTask;
import com.sunlc.dsp.export.FileFormat;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

public interface ExportService {
    void onlineExport(String transno, Map<String, Object> params, String format, HttpServletResponse response);
    ExportTask submitOfflineExport(String transno, Map<String, Object> params, String format, String applyUser);
    ExportTask getExportTask(Long taskId);
    Page<ExportTask> listExportTask(Integer pageNum, Integer pageSize, String transno, Integer status);
    void downloadExportFile(Long taskId, HttpServletResponse response);
    void executeOfflineExport(Long taskId, String transno, Map<String, Object> params, FileFormat format);
}
