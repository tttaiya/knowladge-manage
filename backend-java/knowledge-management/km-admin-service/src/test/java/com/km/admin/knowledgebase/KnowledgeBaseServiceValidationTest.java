package com.km.admin.knowledgebase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.km.admin.knowledgebase.entity.KnowledgeBase;
import com.km.admin.knowledgebase.mapper.KnowledgeBaseMapper;
import com.km.admin.knowledgebase.service.KnowledgeBaseDeleteFacade;
import com.km.admin.knowledgebase.service.KnowledgeBaseService;
import com.km.admin.knowledgebase.service.KnowledgeBaseTaskFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F2 commit #32：KnowledgeBaseService 字段校验 + 策略变更 confirmation 路由。
 *
 * <p>验证：
 * <ul>
 *   <li>非法 category 抛 IllegalArgumentException（1001 → 400）
 *   <li>策略字段组合非法（FIXED 时 chunkOverlap >= chunkSize）抛 IllegalArgumentException
 *   <li>separatorsJson 解析失败抛 IllegalArgumentException
 *   <li>策略变更无 confirmation 抛 IllegalStateException（2004 → 409）
 *   <li>策略变更带 confirmation=true 通过
 * </ul>
 */
class KnowledgeBaseServiceValidationTest {

    private KnowledgeBaseService service;
    private KnowledgeBaseMapper kbMapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        kbMapper = mock(KnowledgeBaseMapper.class);
        objectMapper = new ObjectMapper();
        service = new KnowledgeBaseService();
        try {
            java.lang.reflect.Field f = KnowledgeBaseService.class.getDeclaredField("kbMapper");
            f.setAccessible(true);
            f.set(service, kbMapper);
            java.lang.reflect.Field f2 = KnowledgeBaseService.class.getDeclaredField("objectMapper");
            f2.setAccessible(true);
            f2.set(service, objectMapper);
            java.lang.reflect.Field f3 = KnowledgeBaseService.class.getDeclaredField("taskFacade");
            f3.setAccessible(true);
            f3.set(service, null);
            java.lang.reflect.Field f4 = KnowledgeBaseService.class.getDeclaredField("deleteFacade");
            f4.setAccessible(true);
            f4.set(service, mock(KnowledgeBaseDeleteFacade.class));
            java.lang.reflect.Field f5 = KnowledgeBaseService.class.getDeclaredField("deleteFacade");
            f5.setAccessible(true);
            f5.set(service, mock(KnowledgeBaseDeleteFacade.class));
            // taskFacade 保持 null（commit #28 兜底）
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ----- category 校验 -----

    @Test
    void create_invalidCategory_throwsIllegalArgument() {
        com.km.admin.knowledgebase.dto.CreateKnowledgeBaseRequest req = new com.km.admin.knowledgebase.dto.CreateKnowledgeBaseRequest();
        req.setName("kb-test");
        req.setCategory("INVALID_CATEGORY");
        req.setRetrievalStrategy("VECTOR_RERANK");
        req.setChunkStrategy("HEADING");
        req.setChunkSize(500);
        req.setChunkOverlap(50);
        req.setSeparatorsJson("[]");

        assertThrows(IllegalArgumentException.class,
            () -> service.create(req, "user-uuid-001", "alice"));
    }

    @Test
    void create_validCategoryGENERAL_succeeds() {
        com.km.admin.knowledgebase.dto.CreateKnowledgeBaseRequest req = new com.km.admin.knowledgebase.dto.CreateKnowledgeBaseRequest();
        req.setName("kb-valid");
        req.setCategory("GENERAL");
        req.setRetrievalStrategy("VECTOR_RERANK");
        req.setChunkStrategy("HEADING");
        req.setChunkSize(500);
        req.setChunkOverlap(50);
        req.setSeparatorsJson("[]");

        when(kbMapper.insert(any(KnowledgeBase.class))).thenAnswer(inv -> {
            KnowledgeBase arg = inv.getArgument(0);
            arg.setId(100L);
            return 1;
        });

        com.km.admin.knowledgebase.vo.KnowledgeBaseVO vo = service.create(req, "user-uuid-001", "alice");
        assertEquals(100L, vo.getId());
        assertEquals("kb-valid", vo.getName());
        assertEquals("GENERAL", vo.getCategory());
    }

    // ----- 策略字段校验 -----

    @Test
    void create_fixedStrategy_overlapGreaterThanSize_throws() {
        com.km.admin.knowledgebase.dto.CreateKnowledgeBaseRequest req = new com.km.admin.knowledgebase.dto.CreateKnowledgeBaseRequest();
        req.setName("kb-bad-strategy");
        req.setCategory("GENERAL");
        req.setRetrievalStrategy("VECTOR_RERANK");
        req.setChunkStrategy("FIXED");
        req.setChunkSize(100);
        req.setChunkOverlap(150); // 非法：overlap >= size
        req.setSeparatorsJson("[]");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.create(req, "user-uuid", "alice"));
        assertTrue(e.getMessage().contains("chunkOverlap") || e.getMessage().contains("FIXED"));
    }

    @Test
    void create_invalidSeparatorsJson_throws() {
        com.km.admin.knowledgebase.dto.CreateKnowledgeBaseRequest req = new com.km.admin.knowledgebase.dto.CreateKnowledgeBaseRequest();
        req.setName("kb-bad-separators");
        req.setCategory("GENERAL");
        req.setRetrievalStrategy("VECTOR_RERANK");
        req.setChunkStrategy("HEADING");
        req.setChunkSize(500);
        req.setChunkOverlap(50);
        req.setSeparatorsJson("not-a-json"); // 非法

        assertThrows(IllegalArgumentException.class,
            () -> service.create(req, "user-uuid", "alice"));
    }

    // ----- 策略变更 confirmation 路由 -----

    @Test
    void update_strategyChangeWithoutConfirmation_throwsIllegalState() {
        // mock 现有 KB
        KnowledgeBase existing = new KnowledgeBase();
        existing.setId(1L);
        existing.setName("old");
        existing.setDescription("desc");
        existing.setCategory("GENERAL");
        existing.setRetrievalStrategy("VECTOR_RERANK");
        existing.setChunkStrategy("HEADING");
        existing.setChunkSize(500);
        existing.setChunkOverlap(50);
        existing.setSeparatorsJson("[]");
        existing.setIsDeleted(0);
        existing.setStrategyVersion(1L);
        existing.setDocumentCount(0);
        existing.setCreatedAt(LocalDateTime.now());

        when(kbMapper.getById(1L)).thenReturn(existing);
        when(kbMapper.updateById(any(KnowledgeBase.class))).thenReturn(1);

        com.km.admin.knowledgebase.dto.UpdateKnowledgeBaseRequest req = new com.km.admin.knowledgebase.dto.UpdateKnowledgeBaseRequest();
        req.setRetrievalStrategy("SEMANTIC"); // 策略变更

        // confirmation=null → 应抛 IllegalStateException
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> service.update(req, "user-uuid", "alice", null));
        assertTrue(e.getMessage().contains("confirmation"));
    }

    @Test
    void update_strategyChangeWithConfirmationFalse_throwsIllegalState() {
        KnowledgeBase existing = new KnowledgeBase();
        existing.setId(1L);
        existing.setName("old");
        existing.setCategory("GENERAL");
        existing.setRetrievalStrategy("VECTOR_RERANK");
        existing.setChunkStrategy("HEADING");
        existing.setChunkSize(500);
        existing.setChunkOverlap(50);
        existing.setSeparatorsJson("[]");
        existing.setIsDeleted(0);
        existing.setStrategyVersion(1L);
        existing.setDocumentCount(0);
        existing.setCreatedAt(LocalDateTime.now());

        when(kbMapper.getById(1L)).thenReturn(existing);

        com.km.admin.knowledgebase.dto.UpdateKnowledgeBaseRequest req = new com.km.admin.knowledgebase.dto.UpdateKnowledgeBaseRequest();
        req.setRetrievalStrategy("SEMANTIC");

        // confirmation=false → 仍抛 IllegalStateException
        assertThrows(IllegalStateException.class,
            () -> service.update(req, "user-uuid", "alice", false));
    }

    @Test
    void update_strategyChangeWithConfirmationTrue_succeedsAndBumpsVersion() {
        KnowledgeBase existing = new KnowledgeBase();
        existing.setId(1L);
        existing.setName("old");
        existing.setCategory("GENERAL");
        existing.setRetrievalStrategy("VECTOR_RERANK");
        existing.setChunkStrategy("HEADING");
        existing.setChunkSize(500);
        existing.setChunkOverlap(50);
        existing.setSeparatorsJson("[]");
        existing.setIsDeleted(0);
        existing.setStrategyVersion(1L);
        existing.setDocumentCount(0);
        existing.setCreatedAt(LocalDateTime.now());

        when(kbMapper.getById(1L)).thenReturn(existing);
        when(kbMapper.updateById(any(KnowledgeBase.class))).thenReturn(1);

        com.km.admin.knowledgebase.dto.UpdateKnowledgeBaseRequest req = new com.km.admin.knowledgebase.dto.UpdateKnowledgeBaseRequest();
        req.setRetrievalStrategy("SEMANTIC");

        com.km.admin.knowledgebase.vo.KnowledgeBaseVO vo = service.update(req, "user-uuid", "alice", true);

        ArgumentCaptor<KnowledgeBase> captor = ArgumentCaptor.forClass(KnowledgeBase.class);
        org.mockito.Mockito.verify(kbMapper).updateById(captor.capture());
        KnowledgeBase updated = captor.getValue();

        assertEquals("SEMANTIC", updated.getRetrievalStrategy());
        assertEquals(Long.valueOf(2L), updated.getStrategyVersion()); // 1 → 2 单调递增
        assertNotNull(vo);
    }

    @Test
    void update_nonStrategyChange_doesNotBumpVersion() {
        KnowledgeBase existing = new KnowledgeBase();
        existing.setId(1L);
        existing.setName("old-name");
        existing.setDescription("old-desc");
        existing.setCategory("GENERAL");
        existing.setRetrievalStrategy("VECTOR_RERANK");
        existing.setChunkStrategy("HEADING");
        existing.setChunkSize(500);
        existing.setChunkOverlap(50);
        existing.setSeparatorsJson("[]");
        existing.setIsDeleted(0);
        existing.setStrategyVersion(1L);
        existing.setDocumentCount(0);
        existing.setCreatedAt(LocalDateTime.now());

        when(kbMapper.getById(1L)).thenReturn(existing);
        when(kbMapper.updateById(any(KnowledgeBase.class))).thenReturn(1);

        com.km.admin.knowledgebase.dto.UpdateKnowledgeBaseRequest req = new com.km.admin.knowledgebase.dto.UpdateKnowledgeBaseRequest();
        req.setName("new-name");
        req.setDescription("new-desc");

        // 改 name/desc（非策略字段）→ 不需要 confirmation
        com.km.admin.knowledgebase.vo.KnowledgeBaseVO vo = service.update(req, "user-uuid", "alice", null);

        ArgumentCaptor<KnowledgeBase> captor = ArgumentCaptor.forClass(KnowledgeBase.class);
        org.mockito.Mockito.verify(kbMapper).updateById(captor.capture());
        assertEquals(Long.valueOf(1L), captor.getValue().getStrategyVersion()); // 不递增
        assertEquals("new-name", vo.getName());
    }
}
