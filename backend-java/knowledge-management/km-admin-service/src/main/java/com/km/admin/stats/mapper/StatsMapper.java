package com.km.admin.stats.mapper;

import com.km.admin.stats.dto.TrendDataDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * F8 数据统计 Mapper。
 *
 * <p>所有查询均基于实时聚合（不维护统计物化表），保证文档/任务变更后下一次查询即反映最新值。
 *
 * <p>XML：{@code classpath:mapper/StatsMapper.xml}。
 * 本类显式标注 {@link Mapper}，对应 {@code AdminApplication} 中追加的扫描路径
 * {@code com.km.admin.stats.mapper}（约定同 {@code com.km.admin.document.mapper}）。
 */
@Mapper
public interface StatsMapper {

    /** 知识库总数（is_deleted=0） */
    long countKnowledgeBaseTotal();

    /** 文档总数（is_deleted=0） */
    long countDocumentTotal();

    /**
     * 活跃切片总数：chunk join document，chunk.is_active=1 且 doc.is_deleted=0
     * （注：若使用 is_active 索引，可继续过滤当前激活版本；本接口按冻结口径不强制 version 维度）
     */
    long countChunkTotal();

    /** 状态计数 — READY / PENDING_REVIEW / FAILED */
    long countDocumentByStatus(@Param("status") String status);

    /** 处理中任务数：task_status in ('QUEUED','RUNNING') */
    long countProcessingTask();

    /**
     * 文档创建趋势：按 date(created_at) 分组聚合。
     * 返回 [{date, count}, ...]，date 为 yyyy-MM-dd 字符串。
     *
     * <p>日期范围：[fromDate, toDate]，闭区间。逻辑删除不影响 created_at，保留历史。
     *
     * @param fromDate 起始日（含）yyyy-MM-dd
     * @param toDate 结束日（含）yyyy-MM-dd
     */
    List<TrendDataDTO> selectDocumentTrend(@Param("fromDate") String fromDate,
                                            @Param("toDate") String toDate);
}