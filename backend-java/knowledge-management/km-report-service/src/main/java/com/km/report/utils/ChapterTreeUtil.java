package com.km.report.utils;

import com.km.report.entity.ReportTemplateChapter;
import com.km.report.vo.ChapterTreeVO;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChapterTreeUtil {

    private ChapterTreeUtil() {
    }

    public static List<ChapterTreeVO> buildTree(List<ReportTemplateChapter> chapterList) {
        if (chapterList == null || chapterList.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, ChapterTreeVO> nodeMap = chapterList.stream()
                .map(chapter -> {
                    ChapterTreeVO vo = new ChapterTreeVO();
                    BeanUtils.copyProperties(chapter, vo);
                    vo.setChildren(new ArrayList<>());
                    return vo;
                })
                .collect(Collectors.toMap(ChapterTreeVO::getId, vo -> vo));

        List<ChapterTreeVO> rootList = new ArrayList<>();
        for (ChapterTreeVO node : nodeMap.values()) {
            if (node.getParentId() == null || node.getParentId() == 0) {
                rootList.add(node);
            } else {
                ChapterTreeVO parent = nodeMap.get(node.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                }
            }
        }

        sortTree(rootList);
        generateChapterNo(rootList, "");
        return rootList;
    }

    private static void sortTree(List<ChapterTreeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        nodes.sort(Comparator.comparingInt(node -> node.getSort() == null ? 0 : node.getSort()));
        for (ChapterTreeVO node : nodes) {
            sortTree(node.getChildren());
        }
    }

    private static void generateChapterNo(List<ChapterTreeVO> nodes, String parentNo) {
        for (int index = 0; index < nodes.size(); index++) {
            ChapterTreeVO node = nodes.get(index);
            String currentNo = parentNo.isEmpty() ? String.valueOf(index + 1) : parentNo + "." + (index + 1);
            node.setChapterNo(currentNo);
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                generateChapterNo(node.getChildren(), currentNo);
            }
        }
    }
}
