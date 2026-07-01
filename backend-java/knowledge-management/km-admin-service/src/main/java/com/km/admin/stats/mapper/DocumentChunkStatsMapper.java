// mapper/DocumentChunkStatsMapper.java
package com.km.admin.stats.mapper;

import com.km.admin.stats.entity.DocumentChunkStats;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentChunkStatsMapper {

    /**
     * 获取切片统计信息
     */
    DocumentChunkStats getChunkStats();
}
