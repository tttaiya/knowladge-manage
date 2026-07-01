// mapper/ReviewRecordStatsMapper.java
package com.km.admin.stats.mapper;

import com.km.admin.stats.entity.ReviewRecordStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ReviewRecordStatsMapper {

    /**
     * 获取审核通过数
     */
    Long getApprovedCount();

    /**
     * 获取审核驳回数
     */
    Long getRejectedCount();

    /**
     * 获取审核趋势
     */
    List<ReviewRecordStats> getReviewTrend(@Param("days") Integer days);
}