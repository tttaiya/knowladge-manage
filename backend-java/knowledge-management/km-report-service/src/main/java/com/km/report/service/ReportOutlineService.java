package com.km.report.service;

import com.km.report.dto.GenerateOutlineRequest;
import com.km.report.entity.ReportOutlineItem;

import java.util.List;

public interface ReportOutlineService {
    Long createDraft(GenerateOutlineRequest request);

    List<ReportOutlineItem> getOutline(Long reportId);

    ReportOutlineItem getItem(Long itemId);

    ReportOutlineItem addItem(Long reportId, ReportOutlineItem item);

    ReportOutlineItem updateItem(Long itemId, ReportOutlineItem item);

    List<ReportOutlineItem> regenerateOutline(Long reportId);

    List<ReportOutlineItem> updateOutline(Long reportId, List<ReportOutlineItem> items);

    void deleteOutlineItem(Long itemId);

    List<ReportOutlineItem> moveItem(Long reportId, Long itemId, Integer sort, Long parentId);
}
