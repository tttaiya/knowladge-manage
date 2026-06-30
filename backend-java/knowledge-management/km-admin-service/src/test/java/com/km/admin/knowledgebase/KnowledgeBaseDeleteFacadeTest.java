package com.km.admin.knowledgebase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.km.admin.knowledgebase.entity.KnowledgeBase;
import com.km.admin.knowledgebase.mapper.KnowledgeBaseMapper;
import com.km.admin.knowledgebase.service.KnowledgeBaseDeleteFacade;
import com.km.admin.knowledgebase.service.KnowledgeBaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2 commit #32：KnowledgeBaseDeleteFacade 业务规则。
 *
 * <p>验证：
 * <ul>
 *   <li>单删：知识库存在且无在途任务 → 软删除 + 级联
 *   <li>单删：知识库存在但有在途任务 → 抛 BusinessException(2005)
 *   <li>批删：任一 KB 不存在 → 整批失败（5001）且不删除任何
 *   <li>批删：任一 KB 在途任务 → 整批失败（2005）且不删除任何
 *   <li>批删：全前置通过 → 全删除 + 全级联
 * </ul>
 *
 * <p>本测试只 mock JdbcTemplate 和 KnowledgeBaseMapper，不连真实 MySQL。
 */
class KnowledgeBaseDeleteFacadeTest {

    private KnowledgeBaseDeleteFacade facade;
    private KnowledgeBaseMapper kbMapper;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        kbMapper = mock(KnowledgeBaseMapper.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        facade = new KnowledgeBaseDeleteFacade();
        try {
            java.lang.reflect.Field f = KnowledgeBaseDeleteFacade.class.getDeclaredField("kbMapper");
            f.setAccessible(true);
            f.set(facade, kbMapper);
            java.lang.reflect.Field f2 = KnowledgeBaseDeleteFacade.class.getDeclaredField("jdbcTemplate");
            f2.setAccessible(true);
            f2.set(facade, jdbcTemplate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private KnowledgeBase kb(Long id, String name, int isDeleted) {
        KnowledgeBase k = new KnowledgeBase();
        k.setId(id);
        k.setName(name);
        k.setIsDeleted(isDeleted);
        k.setStrategyVersion(1L);
        k.setDocumentCount(0);
        k.setCreatedAt(LocalDateTime.now());
        return k;
    }

    @Test
    void deleteSingle_existingNoInFlight_softDeletes() {
        when(kbMapper.getById(1L)).thenReturn(kb(1L, "kb1", 0));
        when(kbMapper.softDeleteById(1L)).thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(1L))).thenReturn(new ArrayList<>());

        facade.deleteSingle(1L, "user-uuid");

        verify(kbMapper, times(1)).softDeleteById(1L);
        verify(jdbcTemplate, times(1)).update(
            org.mockito.ArgumentMatchers.contains("update km_document set is_deleted=1"),
            eq(1L)
        );
    }

    @Test
    void deleteSingle_inFlightTask_throwsBusinessException2005() {
        when(kbMapper.getById(1L)).thenReturn(kb(1L, "kb1", 0));
        // 模拟在途任务：返回 docId 列表非空
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(1L)))
            .thenReturn(Arrays.asList(100L, 101L));

        com.km.admin.common.BusinessException e = org.junit.jupiter.api.Assertions.assertThrows(
            com.km.admin.common.BusinessException.class,
            () -> facade.deleteSingle(1L, "user-uuid"));

        assertEquals(2005, e.getCode());
        assertTrue(e.getMessage().contains("1"));

        // 不应软删除
        verify(kbMapper, never()).softDeleteById(1L);
    }

    @Test
    void deleteSingle_alreadyDeleted_throwsIllegalArgument() {
        when(kbMapper.getById(1L)).thenReturn(kb(1L, "kb1", 1));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> facade.deleteSingle(1L, "user-uuid"));

        verify(kbMapper, never()).softDeleteById(1L);
    }

    @Test
    void deleteSingle_notFound_throwsIllegalArgument() {
        when(kbMapper.getById(999L)).thenReturn(null);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> facade.deleteSingle(999L, "user-uuid"));
    }

    @Test
    void deleteBatch_allValidNoInFlight_softDeletesAll() {
        when(kbMapper.listByIds(any())).thenReturn(Arrays.asList(
            kb(1L, "kb1", 0),
            kb(2L, "kb2", 0),
            kb(3L, "kb3", 0)
        ));
        // 三个 KB 都无在途任务
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(1L))).thenReturn(new ArrayList<>());
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(2L))).thenReturn(new ArrayList<>());
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(3L))).thenReturn(new ArrayList<>());
        when(kbMapper.softDeleteById(any())).thenReturn(1);

        facade.deleteBatch(Arrays.asList(1L, 2L, 3L), "user-uuid");

        verify(kbMapper, times(3)).softDeleteById(any());
        verify(jdbcTemplate, times(3)).update(
            argThat(sql -> sql.contains("update km_document set is_deleted=1")),
            anyLong()
        );
    }

    @Test
    void deleteBatch_partialMissing_throws5001AndNoDelete() {
        // 传 3 个 ID，但 listByIds 只返回 2 个
        when(kbMapper.listByIds(any())).thenReturn(Arrays.asList(
            kb(1L, "kb1", 0),
            kb(2L, "kb2", 0)
        ));

        com.km.admin.common.BusinessException e = org.junit.jupiter.api.Assertions.assertThrows(
            com.km.admin.common.BusinessException.class,
            () -> facade.deleteBatch(Arrays.asList(1L, 2L, 3L), "user-uuid"));

        assertEquals(5001, e.getCode());
        assertTrue(e.getMessage().contains("3"));

        // 整批失败 → 一个也不应删
        verify(kbMapper, never()).softDeleteById(any());
    }

    @Test
    void deleteBatch_anyInFlight_throws2005AndNoDelete() {
        when(kbMapper.listByIds(any())).thenReturn(Arrays.asList(
            kb(1L, "kb1", 0),
            kb(2L, "kb2", 0)
        ));
        // kb1 无在途任务，kb2 有
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(1L)))
            .thenReturn(new ArrayList<>());
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(2L)))
            .thenReturn(Arrays.asList(200L));

        com.km.admin.common.BusinessException e = org.junit.jupiter.api.Assertions.assertThrows(
            com.km.admin.common.BusinessException.class,
            () -> facade.deleteBatch(Arrays.asList(1L, 2L), "user-uuid"));

        assertEquals(2005, e.getCode());
        assertTrue(e.getMessage().contains("2"));

        // 整批失败 → 一个也不应删
        verify(kbMapper, never()).softDeleteById(any());
    }

    @Test
    void deleteBatch_emptyList_throwsIllegalArgument() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> facade.deleteBatch(new ArrayList<>(), "user-uuid"));
    }
}
