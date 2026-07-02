package com.km.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.km.report.service.ReportAccessService;
import com.km.report.dto.ReportPageQueryDTO;
import com.km.report.entity.ReportRecord;
import com.km.report.mapper.ReportRecordMapper;
import com.km.report.service.ReportRecordService;
import com.km.report.vo.PageResultVO;
import javax.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReportRecordServiceImpl extends ServiceImpl<ReportRecordMapper, ReportRecord> implements ReportRecordService {

    @Resource
    private ReportAccessService reportAccessService;

    @Override
    public PageResultVO<ReportRecord> pageRecords(ReportPageQueryDTO queryDTO) {
        ReportPageQueryDTO query = queryDTO == null ? new ReportPageQueryDTO() : queryDTO;
        long pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        long pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 100);
        Page<ReportRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ReportRecord> wrapper = new LambdaQueryWrapper<ReportRecord>()
                .eq(ReportRecord::getUserId, reportAccessService.currentUserId())
                .orderByDesc(ReportRecord::getCreateTime);
        if (query.getKeyword() != null && query.getKeyword().trim().length() > 0) {
            wrapper.like(ReportRecord::getReportName, query.getKeyword());
        }
        if (query.getReportType() != null && query.getReportType().trim().length() > 0) {
            wrapper.eq(ReportRecord::getReportType, query.getReportType());
        }
        if (query.getStatus() != null) {
            wrapper.eq(ReportRecord::getStatus, query.getStatus());
        }
        Page<ReportRecord> result = this.page(page, wrapper);
        PageResultVO<ReportRecord> vo = new PageResultVO<>();
        vo.setTotal(result.getTotal());
        vo.setPageNum(result.getCurrent());
        vo.setPageSize(result.getSize());
        vo.setRecords(result.getRecords());
        return vo;
    }

    @Override
    public List<ReportRecord> listOwned() {
        return this.list(new LambdaQueryWrapper<ReportRecord>()
                .eq(ReportRecord::getUserId, reportAccessService.currentUserId())
                .orderByDesc(ReportRecord::getCreateTime));
    }
}
