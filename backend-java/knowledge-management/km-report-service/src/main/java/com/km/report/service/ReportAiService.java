package com.km.report.service;

import com.km.report.dto.AiGenerateRequest;
import com.km.report.dto.AiGenerateResponse;

public interface ReportAiService {
    boolean enabled();
    AiGenerateResponse generate(AiGenerateRequest request);
}
