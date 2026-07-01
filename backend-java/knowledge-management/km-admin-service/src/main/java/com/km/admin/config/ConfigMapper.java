package com.km.admin.config;

import com.km.admin.config.dto.EmbeddingConfigDTO;
import com.km.admin.config.dto.ParserConfigDTO;
import com.km.admin.config.dto.RerankConfigDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ConfigMapper {

    String selectValue(@Param("configKey") String configKey);

    List<Map<String, String>> selectAllAsMap();

    int updateConfigValue(@Param("configKey") String configKey,
                          @Param("configValue") String configValue);

    int insertConfigChangeLog(@Param("operatorId") String operatorId,
                              @Param("operatorName") String operatorName,
                              @Param("configGroup") String configGroup,
                              @Param("configVersion") Long configVersion,
                              @Param("changeSummary") String changeSummary);

    EmbeddingConfigDTO loadEmbeddingConfig();

    RerankConfigDTO loadRerankConfig();

    ParserConfigDTO loadParserConfig();
}
