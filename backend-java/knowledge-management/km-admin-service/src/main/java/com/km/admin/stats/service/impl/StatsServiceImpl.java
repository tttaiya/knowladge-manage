package com.km.admin.stats.service.impl;

import com.km.admin.common.BusinessException;
import com.km.admin.stats.dto.StatsOverviewDTO;
import com.km.admin.stats.dto.TrendDataDTO;
import com.km.admin.stats.mapper.StatsMapper;
import com.km.admin.stats.service.StatsService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * F8 数据统计服务实现。
 *
 * <p>日期补零策略：
 * <ol>
 *   <li>以服务端当前日期为结束日（inclusive），向前生成 days-1 天，合计连续 days 条</li>
 *   <li>Mapper 返回的 date → count 仅覆盖有数据的日期，缺失日期在内存补 0</li>
 *   <li>输出按 date 升序，date 格式 yyyy-MM-dd</li>
 * </ol>
 *
 * <p>days 范围校验：1 ≤ days ≤ 365，非法抛 {@link BusinessException}（1001）。
 */
@Service
public class StatsServiceImpl implements StatsService {

    /** 接口约定的默认天数 */
    private static final int DEFAULT_DAYS = 30;
    /** days 下界 */
    private static final int MIN_DAYS = 1;
    /** days 上界 */
    private static final int MAX_DAYS = 365;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final StatsMapper statsMapper;

    public StatsServiceImpl(StatsMapper statsMapper) {
        this.statsMapper = statsMapper;
    }

    @Override
    public StatsOverviewDTO getOverview(int days) {
        // days 范围校验（与接口契约一致：1~365，默认 30 由 Controller 处理）
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new BusinessException(1001,
                    "days 参数非法，必须在 [" + MIN_DAYS + ", " + MAX_DAYS + "] 之间");
        }

        StatsOverviewDTO dto = new StatsOverviewDTO();
        dto.setKnowledgeBaseTotal(statsMapper.countKnowledgeBaseTotal());
        dto.setDocumentTotal(statsMapper.countDocumentTotal());
        dto.setChunkTotal(statsMapper.countChunkTotal());
        dto.setDocumentReady(statsMapper.countDocumentByStatus("READY"));
        dto.setDocumentPendingReview(statsMapper.countDocumentByStatus("PENDING_REVIEW"));
        dto.setDocumentFailed(statsMapper.countDocumentByStatus("FAILED"));
        dto.setTaskProcessing(statsMapper.countProcessingTask());
        dto.setDocumentTrend(buildTrendWithZeroFill(days));

        return dto;
    }

    /**
     * 生成连续 days 天的趋势数据，缺失日期补 0。
     *
     * @param days 窗口长度
     * @return 升序排列的长度 = days 的列表
     */
    private List<TrendDataDTO> buildTrendWithZeroFill(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1L);

        String fromDate = startDate.format(DATE_FMT);
        String toDate = endDate.format(DATE_FMT);

        // Mapper 仅返回有数据的日期；缺失日期在内存补 0
        List<TrendDataDTO> rawRows = statsMapper.selectDocumentTrend(fromDate, toDate);
        Map<String, Long> countByDate = new HashMap<>();
        if (rawRows != null) {
            for (TrendDataDTO row : rawRows) {
                if (row.getDate() != null) {
                    countByDate.put(row.getDate(), row.getCount());
                }
            }
        }

        List<TrendDataDTO> result = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            String date = startDate.plusDays(i).format(DATE_FMT);
            long count = countByDate.getOrDefault(date, 0L);
            result.add(new TrendDataDTO(date, count));
        }
        return result;
    }

    /** 默认天数，供 Controller 兜底 */
    static int defaultDays() {
        return DEFAULT_DAYS;
    }
}