package com.km.report.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.km.report.entity.ReportTemplate;
import com.km.report.entity.ReportTemplateChapter;
import com.km.report.service.ReportTemplateChapterService;
import com.km.report.service.ReportTemplateService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
public class ReportTemplateInitializer implements CommandLineRunner {

    public static final String TYPE_SUMMER_PEAK = "SUMMER_PEAK_CHECK";
    public static final String TYPE_COAL_INVENTORY = "COAL_INVENTORY_AUDIT";

    @Resource
    private ReportTemplateService reportTemplateService;

    @Resource
    private ReportTemplateChapterService reportTemplateChapterService;

    @Override
    public void run(String... args) {
        initSummerPeakTemplate();
        initCoalInventoryTemplate();
    }

    private void initSummerPeakTemplate() {
        Long templateId = ensureTemplate("迎峰度夏检查报告模板", TYPE_SUMMER_PEAK, "迎峰度夏闭环检查固定模板");
        if (hasChapters(templateId)) {
            return;
        }
        Chapter root1 = chapter("基本情况", "TEXT", "说明检查背景、时间、检查对象和检查范围",
                chapter("检查时间与对象", "TEXT", "根据检查时间素材整理检查时间和对象"),
                chapter("检查组织与依据", "TEXT", "说明检查依据、检查人员和检查方式"));
        Chapter root2 = chapter("检查发现问题", "TEXT_TABLE", "汇总问题清单并按专业分类呈现",
                chapter("设备运行问题", "TEXT_TABLE", "提炼设备运行相关问题"),
                chapter("安全管理问题", "TEXT_TABLE", "提炼安全管理相关问题"),
                chapter("应急保障问题", "TEXT_TABLE", "提炼应急保障相关问题"));
        Chapter root3 = chapter("整改闭环情况", "TEXT_TABLE", "说明问题整改、责任单位和完成情况",
                chapter("整改措施", "TEXT_TABLE", "按问题列出整改措施"),
                chapter("闭环验证", "TEXT", "描述整改闭环验证情况"));
        Chapter root4 = chapter("风险分析与建议", "TEXT", "分析迎峰度夏风险并提出建议",
                chapter("主要风险", "TEXT", "总结仍需关注的主要风险"),
                chapter("后续建议", "TEXT", "提出后续管理建议"));
        Chapter root5 = chapter("结论", "TEXT", "形成检查结论");
        saveTree(templateId, Arrays.asList(root1, root2, root3, root4, root5));
    }

    private void initCoalInventoryTemplate() {
        Long templateId = ensureTemplate("煤库存审计报告模板", TYPE_COAL_INVENTORY, "电煤库存核查审计固定模板");
        if (hasChapters(templateId)) {
            return;
        }
        Chapter root1 = chapter("审计概况", "TEXT", "说明核查背景、核查范围和审计依据",
                chapter("核查对象与期间", "TEXT", "说明电厂、时间范围和核查对象"),
                chapter("数据来源", "TEXT", "说明库存台账、入厂煤、耗煤等数据来源"));
        Chapter root2 = chapter("库存数据核查", "TEXT_TABLE", "对库存数据进行结构化展示和核对",
                chapter("账面库存情况", "TEXT_TABLE", "展示账面库存、热值、煤种等数据"),
                chapter("实物盘点情况", "TEXT_TABLE", "展示现场盘点和差异情况"),
                chapter("差异分析", "TEXT", "分析账实差异原因"));
        Chapter root3 = chapter("采购与消耗分析", "TEXT_TABLE", "分析采购、消耗与库存变化",
                chapter("采购入库情况", "TEXT_TABLE", "展示采购入库统计"),
                chapter("发电耗煤情况", "TEXT_TABLE", "展示耗煤和库存周转情况"));
        Chapter root4 = chapter("问题及风险", "TEXT", "归纳库存管理问题和风险",
                chapter("管理问题", "TEXT", "说明台账、计量、盘点等问题"),
                chapter("经营风险", "TEXT", "说明库存保障和资金占用风险"));
        Chapter root5 = chapter("审计意见与建议", "TEXT", "提出审计意见和整改建议");
        saveTree(templateId, Arrays.asList(root1, root2, root3, root4, root5));
    }

    private Long ensureTemplate(String name, String reportType, String description) {
        ReportTemplate exist = reportTemplateService.getOne(new LambdaQueryWrapper<ReportTemplate>().eq(ReportTemplate::getReportType, reportType).last("LIMIT 1"));
        if (exist != null) {
            return exist.getId();
        }
        ReportTemplate template = new ReportTemplate();
        template.setTemplateName(name);
        template.setReportType(reportType);
        template.setDescription(description);
        template.setStatus(1);
        template.setTemplateScope("GLOBAL");
        template.setChapterCount(0);
        template.setCreatorId("system");
        template.setCreateTime(LocalDateTime.now());
        template.setUpdateTime(LocalDateTime.now());
        template.setDeleted(0);
        reportTemplateService.save(template);
        return template.getId();
    }

    private boolean hasChapters(Long templateId) {
        return reportTemplateChapterService.count(new LambdaQueryWrapper<ReportTemplateChapter>().eq(ReportTemplateChapter::getTemplateId, templateId)) > 0;
    }

    private void saveTree(Long templateId, List<Chapter> roots) {
        insertChildren(templateId, 0L, "", 1, roots);
        ReportTemplate template = reportTemplateService.getById(templateId);
        template.setChapterCount((int) reportTemplateChapterService.count(new LambdaQueryWrapper<ReportTemplateChapter>().eq(ReportTemplateChapter::getTemplateId, templateId)));
        template.setUpdateTime(LocalDateTime.now());
        reportTemplateService.updateById(template);
    }

    private void insertChildren(Long templateId, Long parentId, String parentNo, int level, List<Chapter> chapters) {
        for (int index = 0; index < chapters.size(); index++) {
            Chapter source = chapters.get(index);
            String chapterNo = parentNo.length() == 0 ? String.valueOf(index + 1) : parentNo + "." + (index + 1);
            ReportTemplateChapter chapter = new ReportTemplateChapter();
            chapter.setTemplateId(templateId);
            chapter.setParentId(parentId);
            chapter.setChapterTitle(source.title);
            chapter.setChapterNo(chapterNo);
            chapter.setChapterType(source.type);
            chapter.setLevel(level);
            chapter.setSort(index + 1);
            chapter.setDefaultContent("");
            chapter.setWritingPrompt(source.prompt);
            chapter.setRequiredFlag(1);
            chapter.setCreateTime(LocalDateTime.now());
            chapter.setUpdateTime(LocalDateTime.now());
            chapter.setDeleted(0);
            reportTemplateChapterService.save(chapter);
            if (!source.children.isEmpty()) {
                insertChildren(templateId, chapter.getId(), chapterNo, level + 1, source.children);
            }
        }
    }

    private Chapter chapter(String title, String type, String prompt, Chapter... children) {
        return new Chapter(title, type, prompt, Arrays.asList(children));
    }

    private static class Chapter {
        private final String title;
        private final String type;
        private final String prompt;
        private final List<Chapter> children;

        private Chapter(String title, String type, String prompt, List<Chapter> children) {
            this.title = title;
            this.type = type;
            this.prompt = prompt;
            this.children = children;
        }
    }
}
