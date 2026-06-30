package com.km.admin.config;

import com.km.admin.config.dto.EmbeddingConfigDTO;
import com.km.admin.config.dto.ParserConfigDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * F6 系统配置事务一致性集成测试（R24 + R25 + R26）。
 *
 * 关键检查：
 * - secondUpdateFails_rollbackAndNoPublish: 第二条 update 抛异常 → 数据库整体回滚 + MQ 未被调用
 * - embeddingUpdate_publishWithNullValues: embedding 事件 values=null
 * - parserUpdate_publishWithoutApiKey: parser 事件 values 不含 *api_key*
 */
@SpringBootTest
class ConfigServiceTransactionalTest {

    @Autowired
    private ConfigService configService;

    @Autowired
    private ConfigMapper configMapper;

    @MockBean
    private ConfigChangedProducer producer;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * R24 + R26：第二条 update 失败时，数据库整体回滚且 MQ 不发布。
     * v3/v4 文档建议用 SQL BEGIN/ROLLBACK 是错的，正确做法是模拟 Service 层抛异常。
     */
    @Test
    @Transactional
    void secondUpdateFails_rollbackAndNoPublish() {
        // 让 dimension 这条 update 抛异常（模拟 DB 错误）
        doThrow(new RuntimeException("simulated DB error"))
                .when(configMapper).updateConfigValue(eq("embedding.dimension"), anyString());

        EmbeddingConfigDTO dto = new EmbeddingConfigDTO();
        dto.setModel("tmp-model");
        dto.setApiBase("http://tmp");
        dto.setApiKey("tmp-key");
        dto.setDimension(999);

        assertThrows(RuntimeException.class, () -> configService.updateEmbeddingConfig(dto));

        // 断言：上一条 model 的修改也被回滚（事务整体回滚）
        String modelValue = jdbc.queryForObject(
                "SELECT config_value FROM km_system_config WHERE config_key='embedding.model'", String.class);
        assertEquals("text-embedding-v1", modelValue,
                "embedding.model 应回滚到 seed 默认值，实际: " + modelValue);

        String dimensionValue = jdbc.queryForObject(
                "SELECT config_value FROM km_system_config WHERE config_key='embedding.dimension'", String.class);
        assertEquals("1024", dimensionValue,
                "embedding.dimension 应保持 seed 默认值 1024，实际: " + dimensionValue);

        // 断言：MQ 未被调用（afterCommit 只在事务真正提交后触发）
        verify(producer, never()).publishConfigChanged(anyString(), any());
    }

    /**
     * v5/v6 修正：embedding 事件按规则 values=null，不检查 keySet。
     */
    @Test
    void embeddingUpdate_publishWithNullValues() {
        EmbeddingConfigDTO dto = new EmbeddingConfigDTO();
        dto.setModel("new-embedding-model");
        dto.setApiBase("");
        dto.setApiKey("");
        dto.setDimension(512);

        configService.updateEmbeddingConfig(dto);

        verify(producer, times(1)).publishConfigChanged(eq("embedding"), eq(null));
    }

    /**
     * v4/v6 修正：parser 事件 values 不包含 *api_key*。
     */
    @Test
    @SuppressWarnings("unchecked")
    void parserUpdate_publishWithoutApiKey() {
        ParserConfigDTO dto = new ParserConfigDTO();
        dto.setPaddleocrEnabled(false);
        dto.setMaxConcurrentTasks(8);
        dto.setMaxRetryCount(3);
        dto.setTimeoutSeconds(30);

        configService.updateParserConfig(dto);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(producer, times(1)).publishConfigChanged(eq("parser"), captor.capture());

        Map<String, String> values = captor.getValue();
        assertNotNull(values, "parser 事件 values 不应为 null（与 embedding 不同）");
        assertFalse(
                values.keySet().stream().anyMatch(k -> k.endsWith(".api_key")),
                "parser 事件 values 不得包含 *api_key，实际 keys: " + values.keySet()
        );
        assertEquals("8", values.get("parser.max_concurrent_tasks"),
                "parser.max_concurrent_tasks 应为新值 8，实际: " + values.get("parser.max_concurrent_tasks"));
    }
}