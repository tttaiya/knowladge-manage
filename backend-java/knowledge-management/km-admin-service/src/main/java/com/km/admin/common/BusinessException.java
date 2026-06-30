package com.km.admin.common;

/**
 * 项目级业务异常。
 * F2 v1.0 文档附录 A 错误码：1001/2001/2002/2004/2005/3001/5001。
 *
 * 注：F2 commit #28 引入；原 IllegalArgumentException 仍由 GlobalExceptionHandler 处理（1001）。
 *     IllegalStateException 仍由 GlobalExceptionHandler 处理（2004）。
 *     本类承载 2005（在途任务）、3001（任务已存在）、5001（事务异常）等专属业务错误。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 2005：在途任务不允许触发该操作 */
    public static BusinessException inFlightTask(Long kbId) {
        return new BusinessException(2005,
            "知识库 " + kbId + " 存在未完成任务，请等待任务结束再试");
    }

    /** 3001：任务已存在（重入保护） */
    public static BusinessException taskAlreadyExists(Long kbId) {
        return new BusinessException(3001,
            "知识库 " + kbId + " 已有待执行的策略变更任务，请勿重复触发");
    }

    /** 5001：事务异常（批量操作任一失败则整批失败） */
    public static BusinessException transactionFailed(String message) {
        return new BusinessException(5001, message == null ? "事务执行失败" : message);
    }
}
