package com.km.admin.stats.service;

import com.km.admin.stats.dto.StatsOverviewDTO;

/**
 * F8 数据统计服务接口。
 *
 * <p>由 Gateway 注入鉴权上下文，本服务不读取 X-User-Id。
 */
public interface StatsService {

    /**
     * 拉取统计概览。
     *
     * @param days 趋势窗口天数（1~365）
     * @return 概览 DTO，包含 6 个总量字段 + documentTrend（已服务端补零）
     * @throws com.km.admin.common.BusinessException days 非法
     */
    StatsOverviewDTO getOverview(int days);
}