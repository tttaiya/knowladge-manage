package com.km.admin.stats.controller;

import com.km.admin.common.ApiResponse;
import com.km.admin.stats.dto.StatsOverviewDTO;
import com.km.admin.stats.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * F8 数据统计接口。
 *
 * <p>端点：
 * <ul>
 *   <li>GET /api/v1/stats/overview?days=N → 统计概览（days 默认 30，范围 1~365）</li>
 * </ul>
 *
 * <p>鉴权：Gateway 拦截注入用户上下文；本 Controller 不再读取 X-User-Id。
 */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    public ApiResponse<StatsOverviewDTO> overview(
            @RequestParam(name = "days", required = false, defaultValue = "30") int days) {
        // days=0 或负数 / 超过 365 都会被 Service 抛 BusinessException(1001) → 400
        return ApiResponse.success(statsService.getOverview(days));
    }
}