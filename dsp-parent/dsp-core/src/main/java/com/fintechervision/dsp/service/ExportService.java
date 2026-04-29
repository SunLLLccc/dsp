package com.fintechervision.dsp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fintechervision.dsp.entity.ExportTask;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

public interface ExportService {
    void onlineExport(String transno, Map<String, Object> params, String format, HttpServletResponse response);
    ExportTask submitOfflineExport(String transno, Map<String, Object> params, String format, String applyUser);
    ExportTask getExportTask(Long taskId);
    Page<ExportTask> listExportTask(Integer pageNum, Integer pageSize, String transno, Integer status);
    void downloadExportFile(Long taskId, HttpServletResponse response);
}
