package com.km.admin.config;

import com.km.admin.config.dto.ConnectionTestRequest;
import com.km.admin.config.dto.ConnectionTestResult;
import com.km.admin.config.dto.EmbeddingConfigDTO;
import com.km.admin.config.dto.ParserConfigDTO;
import com.km.admin.config.dto.RerankConfigDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 系统配置 Mapper（XML：classpath:mapper/ConfigMapper.xml）。
 * R11：字段名严格对齐 km_system_config 表。
 */
@Mapper
public interface ConfigMapper {

    String selectValue(@Param("configKey") String configKey);

    List<Map<String, String>> selectAllAsMap();

    int updateConfigValue(@Param("configKey") String configKey,
                          @Param("configValue") String configValue);

    // 内部 dto 复用
    EmbeddingConfigDTO loadEmbeddingConfig();

    RerankConfigDTO loadRerankConfig();

    ParserConfigDTO loadParserConfig();
}