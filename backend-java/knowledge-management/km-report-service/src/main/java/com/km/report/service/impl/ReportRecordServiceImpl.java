package com.km.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.km.report.dto.ReportPageQueryDTO;
import com.km.report.entity.ReportRecord;
import com.km.report.mapper.ReportRecordMapper;
import com.km.report.service.ReportRecordService;
import com.km.report.vo.PageResultVO;
import org.springframework.stereotype.Service;

@Service
public class ReportRecordServiceImpl extends ServiceImpl<ReportRecordMapper, ReportRecord> implements ReportRecordService {

    @Override
    public PageResultVO<ReportRecord> pageRecords(ReportPageQueryDTO queryDTO) {
        Page<ReportRecord> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        LambdaQueryWrapper<ReportRecord> wrapper = new LambdaQueryWrapper<ReportRecord>().orderByDesc(ReportRecord::getCreateTime);
        if (queryDTO.getKeyword() != null && queryDTO.getKeyword().trim().length() > 0) {
            wrapper.like(ReportRecord::getReportName, queryDTO.getKeyword());
        }
        if (queryDTO.getReportType() != null && queryDTO.getReportType().trim().length() > 0) {
            wrapper.eq(ReportRecord::getReportType, queryDTO.getReportType());
        }
        if (queryDTO.getStatus() != null) {
            wrapper.eq(ReportRecord::getStatus, queryDTO.getStatus());
        }
        Page<ReportRecord> result = this.page(page, wrapper);
        PageResultVO<ReportRecord> vo = new PageResultVO<>();
        vo.setTotal(result.getTotal());
        vo.setPageNum(result.getCurrent());
        vo.setPageSize(result.getSize());
        vo.setRecords(result.getRecords());
        return vo;
    }
}
