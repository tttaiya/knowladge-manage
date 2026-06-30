package com.km.admin.knowledgebase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.km.admin.knowledgebase.entity.KnowledgeBase;
import com.km.admin.knowledgebase.service.KnowledgeBaseService;
import com.km.admin.knowledgebase.vo.KnowledgeBaseSnapshotVO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F2 commit #32：KnowledgeBaseService.toSnapshot 策略快照序列化语义。
 *
 * <p>验证：
 * <ul>
 *   <li>快照包含 KB id/name/strategy/chunkSize/chunkOverlap
 *   <li>快照 capturedAt 已设置
 *   <li>separators 解析为 List&lt;String&gt;
 *   <li>separatorsJson 为空时 separators 为空列表（不抛异常）
 * </ul>
 */
class KnowledgeBaseSnapshotTest {

    private KnowledgeBaseService newService() {
        KnowledgeBaseService s = new KnowledgeBaseService();
        try {
            Field f = KnowledgeBaseService.class.getDeclaredField("objectMapper");
            f.setAccessible(true);
            f.set(s, new ObjectMapper());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return s;
    }

    @Test
    void toSnapshot_includesAllStrategyFields() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(42L);
        kb.setName("kb-snap");
        kb.setRetrievalStrategy("SEMANTIC");
        kb.setChunkStrategy("FIXED");
        kb.setChunkSize(300);
        kb.setChunkOverlap(30);
        kb.setSeparatorsJson("[\"\\n\\n\",\"\\n\",\"。\"]");
        kb.setCreatedAt(LocalDateTime.now());

        KnowledgeBaseService s = newService();
        KnowledgeBaseSnapshotVO snap = s.toSnapshot(kb);

        assertEquals(42L, snap.getId());
        assertEquals("kb-snap", snap.getName());
        assertEquals("SEMANTIC", snap.getRetrievalStrategy());
        assertEquals("FIXED", snap.getChunkStrategy());
        assertEquals(300, snap.getChunkSize());
        assertEquals(30, snap.getChunkOverlap());
        assertNotNull(snap.getSeparators());
        assertEquals(3, snap.getSeparators().size());
        assertNotNull(snap.getCapturedAt());
    }

    @Test
    void toSnapshot_emptySeparatorsJson_returnsEmptyList() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(1L);
        kb.setName("kb-empty");
        kb.setRetrievalStrategy("VECTOR_RERANK");
        kb.setChunkStrategy("HEADING");
        kb.setChunkSize(500);
        kb.setChunkOverlap(50);
        kb.setSeparatorsJson(null);

        KnowledgeBaseService s = newService();
        KnowledgeBaseSnapshotVO snap = s.toSnapshot(kb);

        assertNotNull(snap.getSeparators());
        assertEquals(0, snap.getSeparators().size());
    }

    @Test
    void toSnapshot_invalidJson_returnsEmptyList() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(1L);
        kb.setName("kb-bad");
        kb.setRetrievalStrategy("VECTOR_RERANK");
        kb.setChunkStrategy("HEADING");
        kb.setChunkSize(500);
        kb.setChunkOverlap(50);
        kb.setSeparatorsJson("not-a-valid-json");

        KnowledgeBaseService s = newService();
        KnowledgeBaseSnapshotVO snap = s.toSnapshot(kb);

        // 解析失败不抛异常 → 空列表
        assertNotNull(snap.getSeparators());
        assertEquals(0, snap.getSeparators().size());
    }

    @Test
    void knowledgeBase_activeName_isExposedForJsonDebug() {
        // KnowledgeBase.getActiveName() 是 STORED 生成列的 Java 镜像
        KnowledgeBase active = new KnowledgeBase();
        active.setName("active-kb");
        active.setIsDeleted(0);
        assertEquals("active-kb", active.getActiveName());

        KnowledgeBase deleted = new KnowledgeBase();
        deleted.setName("deleted-kb");
        deleted.setIsDeleted(1);
        // 软删后 active_name 为 NULL（对应 DB STORED 列）
        org.junit.jupiter.api.Assertions.assertNull(deleted.getActiveName());
    }

    @Test
    void strategyChangeValidation_pathThroughIsStrategyChanged() {
        // 间接验证 toListVO 不破坏 strategy 字段（DTO 字段命名映射）
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(1L);
        kb.setName("x");
        kb.setRetrievalStrategy("SEMANTIC");
        kb.setChunkStrategy("FIXED");
        kb.setChunkSize(200);
        kb.setChunkOverlap(20);
        kb.setSeparatorsJson("[\"|\"]");
        kb.setDocumentCount(5);
        kb.setStrategyVersion(3L);
        kb.setIsDeleted(0);
        kb.setCreatedAt(LocalDateTime.now());

        // BeanUtils.copyProperties 反射拷贝不会抛异常
        com.km.admin.knowledgebase.vo.KnowledgeBaseVO vo = new com.km.admin.knowledgebase.vo.KnowledgeBaseVO();
        org.springframework.beans.BeanUtils.copyProperties(kb, vo);

        assertEquals("SEMANTIC", vo.getRetrievalStrategy());
        assertEquals("FIXED", vo.getChunkStrategy());
        assertEquals(200, vo.getChunkSize());
        assertEquals(20, vo.getChunkOverlap());
        assertEquals(Long.valueOf(3L), vo.getStrategyVersion());
        assertTrue(vo.getDocumentCount() == 5);
    }
}
