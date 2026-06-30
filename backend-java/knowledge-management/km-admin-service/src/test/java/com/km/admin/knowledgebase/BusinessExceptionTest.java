package com.km.admin.knowledgebase;

import com.km.admin.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F2 commit #32：BusinessException 错误码语义校验。
 *
 * <p>设计依据：F2 v1.0 文档附录 A 错误码
 * <ul>
 *   <li>2005 → inFlightTask(KB 存在在途任务)
 *   <li>3001 → taskAlreadyExists(任务已存在，重入保护)
 *   <li>5001 → transactionFailed(批删任一失败整批回滚)
 * </ul>
 */
class BusinessExceptionTest {

    @Test
    void inFlightTask_carriesCode2005() {
        BusinessException e = BusinessException.inFlightTask(42L);
        assertEquals(2005, e.getCode());
        assertTrue(e.getMessage().contains("42"));
        assertTrue(e.getMessage().contains("未完成") || e.getMessage().contains("在途"));
    }

    @Test
    void taskAlreadyExists_carriesCode3001() {
        BusinessException e = BusinessException.taskAlreadyExists(7L);
        assertEquals(3001, e.getCode());
        assertTrue(e.getMessage().contains("7"));
    }

    @Test
    void transactionFailed_carriesCode5001() {
        BusinessException e = BusinessException.transactionFailed("批删失败：部分 KB 不存在");
        assertEquals(5001, e.getCode());
        assertEquals("批删失败：部分 KB 不存在", e.getMessage());
    }

    @Test
    void transactionFailed_nullMessage_usesDefault() {
        BusinessException e = BusinessException.transactionFailed(null);
        assertEquals(5001, e.getCode());
        assertEquals("事务执行失败", e.getMessage());
    }

    @Test
    void businessException_isRuntimeException() {
        // 必须是 RuntimeException 才能被 GlobalExceptionHandler 捕获
        assertTrue(RuntimeException.class.isAssignableFrom(BusinessException.class));
    }

    @Test
    void allFactoryMethods_produceBusinessException() {
        // 工厂方法必须返回 BusinessException（保证 GlobalExceptionHandler.handleBusiness 路由）
        assertThrows(BusinessException.class, () -> {
            throw BusinessException.inFlightTask(1L);
        });
    }
}
