// mapper/DocumentStatsMapper.java
package com.km.admin.stats.mapper;

import com.km.admin.stats.entity.DocumentStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DocumentStatsMapper {

    /**
     * 获取文档总数
     */
    Long getDocumentTotal();

    /**
     * 获取文档状态分布
     */
    List<DocumentStats> getDocumentStatusDistribution();

    /**
     * 获取文档上传趋势
     */
    List<DocumentStats> getDocumentTrend(@Param("days") Integer days);

    /**
     * 获取失败文档数
     */
    Long getFailedDocumentCount();

    /**
     * 获取已就绪和待审核文档数
     */
    List<DocumentStats> getReadyAndPendingReviewCounts();
}
