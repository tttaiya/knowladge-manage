package com.km.report.service;

import com.km.report.dto.GenerateReportRequest;
import com.km.report.vo.ReportGenerationProgressVO;

public interface ReportGenerationService {
    ReportGenerationProgressVO startGenerate(GenerateReportRequest request);

    ReportGenerationProgressVO getProgress(Long reportId);
}
