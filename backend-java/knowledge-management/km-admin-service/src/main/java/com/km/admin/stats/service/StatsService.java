// service/StatsService.java
package com.km.admin.stats.service;

import com.km.admin.stats.dto.StatsOverviewDTO;

public interface StatsService {
    StatsOverviewDTO getStatsOverview(Integer days);
}
