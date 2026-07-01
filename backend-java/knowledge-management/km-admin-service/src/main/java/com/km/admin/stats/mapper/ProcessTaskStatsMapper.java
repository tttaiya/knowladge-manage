// mapper/ProcessTaskStatsMapper.java
package com.km.admin.stats.mapper;

import com.km.admin.stats.entity.ProcessTaskStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProcessTaskStatsMapper {

    /**
     * 获取排队任务数
     */
    Long getQueuedTaskCount();

    /**
     * 获取运行中任务数
     */
    Long getRunningTaskCount();

    /**
     * 获取成功任务数
     */
    Long getSuccessTaskCount();

    /**
     * 获取失败任务数
     */
    Long getFailedTaskCount();

    /**
     * 获取任务趋势
     */
    List<ProcessTaskStats> getTaskTrend(@Param("days") Integer days);

    /**
     * 获取任务状态分布
     */
    List<ProcessTaskStats> getTaskStatusDistribution();
}
