package com.km.report.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.km.report.entity.ReportOutlineItem;
import com.km.report.mapper.ReportOutlineItemMapper;
import com.km.report.service.ReportOutlineItemService;
import org.springframework.stereotype.Service;

@Service
public class ReportOutlineItemServiceImpl extends ServiceImpl<ReportOutlineItemMapper, ReportOutlineItem> implements ReportOutlineItemService {
}
