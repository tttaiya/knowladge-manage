// mapper/KnowledgeBaseStatsMapper.java
package com.km.admin.stats.mapper;

import com.km.admin.stats.entity.KnowledgeBaseStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface KnowledgeBaseStatsMapper {

    /**
     * 获取知识库统计信息
     */
    KnowledgeBaseStats getKnowledgeBaseStats();

    /**
     * 获取知识库分类分布
     */
    List<KnowledgeBaseStats> getKnowledgeBaseDistribution();
}