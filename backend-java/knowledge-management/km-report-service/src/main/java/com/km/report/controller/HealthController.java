package com.km.report.controller;

import com.km.report.common.context.LoginUserContext;
import com.km.report.common.result.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/health")
public class HealthController {

    @GetMapping
    public ApiResult<String> health() {
        return ApiResult.ok("report-service is running");
    }

    @GetMapping("/user")
    public ApiResult<String> user() {
        return ApiResult.ok("userId=" + LoginUserContext.getUserId());
    }
}
