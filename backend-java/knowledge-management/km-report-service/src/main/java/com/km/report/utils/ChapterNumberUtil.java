package com.km.report.utils;

import com.km.report.entity.ReportOutlineItem;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ChapterNumberUtil {

    private ChapterNumberUtil() {
    }

    public static void renumberOutline(List<ReportOutlineItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Map<Long, List<ReportOutlineItem>> childrenMap = items.stream()
                .collect(Collectors.groupingBy(item -> item.getParentId() == null ? 0L : item.getParentId()));
        renumberChildren(childrenMap, 0L, "");
    }

    private static void renumberChildren(Map<Long, List<ReportOutlineItem>> childrenMap, Long parentId, String parentNo) {
        List<ReportOutlineItem> children = childrenMap.get(parentId);
        if (children == null || children.isEmpty()) {
            return;
        }
        children.sort(Comparator
                .comparing((ReportOutlineItem item) -> item.getSort() == null ? 0 : item.getSort())
                .thenComparing(item -> item.getId() == null ? 0L : item.getId()));
        for (int index = 0; index < children.size(); index++) {
            ReportOutlineItem child = children.get(index);
            String chapterNo = parentNo.length() == 0 ? String.valueOf(index + 1) : parentNo + "." + (index + 1);
            child.setChapterNo(chapterNo);
            child.setLevel(chapterNo.split("\\.").length);
            child.setSort(index + 1);
            renumberChildren(childrenMap, child.getId(), chapterNo);
        }
    }
}
