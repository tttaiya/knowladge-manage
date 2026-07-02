// controller/StatsController.java
package com.km.admin.stats.controller;

import com.km.admin.common.ApiResponse;
import com.km.admin.stats.dto.StatsOverviewDTO;
import com.km.admin.stats.service.StatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private static final Logger log = LoggerFactory.getLogger(StatsController.class);

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    public ApiResponse<StatsOverviewDTO> getStatsOverview(
            @RequestParam(defaultValue = "30") Integer days,
            HttpServletRequest request) {

        // 从网关注入的header中获取用户信息（仅用于日志）
        String userId = request.getHeader("X-User-Id");
        String userName = request.getHeader("X-User-Name");

        // 记录请求日志，便于追踪
        log.info("统计概览请求 - 用户: {} (ID: {}), 天数: {}",
                userName != null ? userName : "未知用户",
                userId != null ? userId : "未认证",
                days);

        // 验证参数
        if (days == null || days < 1) {
            days = 30; // 默认30天
        }
        if (days > 365) {
            return ApiResponse.fail(400, "参数错误：天数不能超过365天");
        }

        try {
            StatsOverviewDTO data = statsService.getStatsOverview(days);
            log.info("统计概览数据获取成功 - 文档总数: {}, 知识库总数: {}",
                    data.getDocumentTotal(), data.getKnowledgeBaseTotal());
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取统计概览数据失败", e);
            return ApiResponse.fail(500, "获取统计概览数据失败：" + e.getMessage());
        }
    }
}