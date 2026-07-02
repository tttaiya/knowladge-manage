package com.km.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.km.report.common.context.LoginUserContext;
import com.km.report.common.exception.BizException;
import com.km.report.config.ReportExportProperties;
import com.km.report.config.ReportStorageProperties;
import com.km.report.dto.ExportDocxStyleConfig;
import com.km.report.entity.ReportChapterContent;
import com.km.report.entity.ReportExportTask;
import com.km.report.entity.ReportRecord;
import com.km.report.service.ReportAccessService;
import com.km.report.service.ReportChapterContentService;
import com.km.report.service.ReportDocxExportService;
import com.km.report.service.ReportExportTaskService;
import com.km.report.service.ReportFileStorageService;
import com.km.report.service.ReportRecordService;
import com.km.report.service.ReportSystemConfigService;
import com.km.report.vo.ReportExportResultVO;
import com.km.report.vo.FileUploadVO;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TextAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.util.Units;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReportDocxExportServiceImpl implements ReportDocxExportService {

    @Resource
    private ReportRecordService reportRecordService;

    @Resource
    private ReportChapterContentService reportChapterContentService;

    @Resource
    private ReportExportTaskService reportExportTaskService;

    @Resource
    private ReportSystemConfigService reportSystemConfigService;
    @Resource
    private ReportAccessService reportAccessService;

    @Resource
    private ReportExportProperties reportExportProperties;
    @Resource
    private ReportStorageProperties reportStorageProperties;
    @Resource
    private ReportFileStorageService reportFileStorageService;

    @Override
    public ReportExportResultVO regenerateDocx(Long reportId) {
        ReportRecord reportRecord = reportAccessService.requireOwnedRecord(reportId);

        List<ReportChapterContent> chapters = reportChapterContentService.list(
                new LambdaQueryWrapper<ReportChapterContent>()
                        .eq(ReportChapterContent::getReportId, reportId)
                        .orderByAsc(ReportChapterContent::getParentId)
                        .orderByAsc(ReportChapterContent::getSort)
                        .orderByAsc(ReportChapterContent::getId)
        );

        if (chapters == null || chapters.isEmpty()) {
            throw new BizException("报告章节内容为空，无法生成Word");
        }

        ReportExportTask task = new ReportExportTask();
        task.setReportId(reportId);
        task.setExportFormat("DOCX");
        task.setStatus(1);
        task.setTriggerType("MANUAL");
        task.setCreatorId(reportAccessService.currentUserId());
        task.setCreateTime(LocalDateTime.now());
        task.setDeleted(0);
        reportExportTaskService.save(task);

        try {
            ExportDocxStyleConfig styleConfig = buildStyleConfig();

            String safeReportName = sanitizeFileName(reportRecord.getReportName());
            String fileName = "report_" + reportId + "_" + System.currentTimeMillis() + "_" + safeReportName + ".docx";

            XWPFDocument document = new XWPFDocument();

            createHeaderFooter(document, styleConfig);
            createTitle(document, reportRecord, styleConfig);
            createCatalogNotice(document, styleConfig);

            for (ReportChapterContent chapter : chapters) {
                createChapter(document, chapter, styleConfig);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.write(outputStream);
            document.close();

            byte[] content = outputStream.toByteArray();
            FileUploadVO uploaded = reportFileStorageService.storeBytes(
                    content,
                    fileName,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    reportStorageProperties.getExportsBucket(),
                    "docx");
            String fileUrl = uploaded.getFileUrl();

            task.setStatus(2);
            task.setFileUrl(fileUrl);
            task.setBucket(uploaded.getBucket());
            task.setObjectKey(uploaded.getObjectKey());
            task.setFileSize(uploaded.getFileSize());
            task.setFinishTime(LocalDateTime.now());
            reportExportTaskService.updateById(task);

            reportRecord.setExportStatus(2);
            reportRecord.setFileUrl(fileUrl);
            reportRecord.setDocxUrl(fileUrl);
            reportRecordService.updateById(reportRecord);

            ReportExportResultVO resultVO = new ReportExportResultVO();
            resultVO.setExportTaskId(task.getId());
            resultVO.setReportId(reportId);
            resultVO.setFileUrl(fileUrl);
            resultVO.setFileName(fileName);
            resultVO.setMessage("Word文档已重新生成。本次生成基于已保存的报告数据，未重新执行AI生成。目录域需要在Word中右键目录并选择“更新域”后生效。");

            return resultVO;
        } catch (Exception e) {
            task.setStatus(3);
            task.setFailReason(e.getMessage());
            task.setFinishTime(LocalDateTime.now());
            reportExportTaskService.updateById(task);

            reportRecord.setExportStatus(3);
            reportRecordService.updateById(reportRecord);

            if (e instanceof BizException) {
                throw (BizException) e;
            }

            throw new BizException("Word文档生成失败：" + e.getMessage());
        }
    }

    @Override
    public void downloadFile(String fileName, HttpServletResponse response) {
        if (!StringUtils.hasText(fileName)) {
            throw new BizException("文件名不能为空");
        }

        if (fileName.contains("..") || fileName.contains("\\")) {
            throw new BizException("非法文件名");
        }

        reportFileStorageService.download(reportStorageProperties.getExportsBucket() + "/" + fileName, response);
    }

    private ExportDocxStyleConfig buildStyleConfig() {
        ExportDocxStyleConfig config = new ExportDocxStyleConfig();

        config.setFontName(reportSystemConfigService.getValueByKey("export.default.font_name", "宋体"));
        config.setFontSize(parseInteger(reportSystemConfigService.getValueByKey("export.default.font_size", "12"), 12));
        config.setEnableHeaderFooter(parseInteger(reportSystemConfigService.getValueByKey("export.enable_header_footer", "1"), 1));
        config.setTableBorderStyle(parseInteger(reportSystemConfigService.getValueByKey("export.table_border_style", "1"), 1));
        config.setHeading1FontSize(parseInteger(reportSystemConfigService.getValueByKey("export.heading1.font_size", "18"), 18));
        config.setHeading2FontSize(parseInteger(reportSystemConfigService.getValueByKey("export.heading2.font_size", "16"), 16));
        config.setHeading3FontSize(parseInteger(reportSystemConfigService.getValueByKey("export.heading3.font_size", "14"), 14));
        config.setSpacingBefore(parseInteger(reportSystemConfigService.getValueByKey("export.paragraph.spacing_before", "120"), 120));
        config.setSpacingAfter(parseInteger(reportSystemConfigService.getValueByKey("export.paragraph.spacing_after", "120"), 120));

        return config;
    }

    private Integer parseInteger(String value, Integer defaultValue) {
        try {
            return Integer.valueOf(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void createHeaderFooter(XWPFDocument document, ExportDocxStyleConfig config) {
        if (config.getEnableHeaderFooter() == null || config.getEnableHeaderFooter() != 1) {
            return;
        }

        XWPFHeaderFooterPolicy policy = document.createHeaderFooterPolicy();

        XWPFParagraph headerParagraph = document.createParagraph();
        headerParagraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun headerRun = headerParagraph.createRun();
        headerRun.setFontFamily(config.getFontName());
        headerRun.setFontSize(config.getHeaderFontSize());
        headerRun.setText(config.getHeaderText());
        policy.createHeader(XWPFHeaderFooterPolicy.DEFAULT, new XWPFParagraph[]{headerParagraph});

        XWPFParagraph footerParagraph = document.createParagraph();
        footerParagraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun footerRun = footerParagraph.createRun();
        footerRun.setFontFamily(config.getFontName());
        footerRun.setFontSize(config.getFooterFontSize());
        footerRun.setText(config.getFooterText());
        policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT, new XWPFParagraph[]{footerParagraph});
    }

    private void createTitle(XWPFDocument document, ReportRecord reportRecord, ExportDocxStyleConfig config) {
        XWPFParagraph titleParagraph = document.createParagraph();
        titleParagraph.setAlignment(ParagraphAlignment.CENTER);
        titleParagraph.setSpacingAfter(config.getSpacingAfter() * 2);

        XWPFRun titleRun = titleParagraph.createRun();
        titleRun.setFontFamily(config.getFontName());
        titleRun.setFontSize(config.getHeading1FontSize());
        titleRun.setBold(true);
        titleRun.setText(reportRecord.getReportName());

        XWPFParagraph metaParagraph = document.createParagraph();
        metaParagraph.setAlignment(ParagraphAlignment.CENTER);
        metaParagraph.setSpacingAfter(config.getSpacingAfter());

        XWPFRun metaRun = metaParagraph.createRun();
        metaRun.setFontFamily(config.getFontName());
        metaRun.setFontSize(config.getFontSize());
        metaRun.setText("报告类型：" + nullToEmpty(reportRecord.getReportType())
                + "    专业：" + nullToEmpty(reportRecord.getMajor())
                + "    电厂：" + nullToEmpty(reportRecord.getPowerPlant())
                + "    年份：" + (reportRecord.getReportYear() == null ? "" : reportRecord.getReportYear()));
    }

    private void createCatalogNotice(XWPFDocument document, ExportDocxStyleConfig config) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        paragraph.setBorderBottom(Borders.SINGLE);
        paragraph.setSpacingAfter(config.getSpacingAfter());

        XWPFRun run = paragraph.createRun();
        run.setFontFamily(config.getFontName());
        run.setFontSize(config.getFontSize() - 2);
        run.setColor("666666");
        run.setText("提示：如本文档包含目录，请在 Word 中右键目录并选择“更新域”后生效。");
    }

    private void createChapter(XWPFDocument document, ReportChapterContent chapter, ExportDocxStyleConfig config) {
        XWPFParagraph heading = document.createParagraph();
        heading.setAlignment(ParagraphAlignment.LEFT);
        heading.setSpacingBefore(config.getSpacingBefore());
        heading.setSpacingAfter(config.getSpacingAfter());

        XWPFRun headingRun = heading.createRun();
        headingRun.setFontFamily(config.getFontName());
        headingRun.setBold(true);

        int level = chapter.getLevel() == null ? 1 : chapter.getLevel();
        if (level <= 1) {
            headingRun.setFontSize(config.getHeading1FontSize());
        } else if (level == 2) {
            headingRun.setFontSize(config.getHeading2FontSize());
        } else {
            headingRun.setFontSize(config.getHeading3FontSize());
        }

        String chapterNo = nullToEmpty(chapter.getChapterNo());
        String title = nullToEmpty(chapter.getChapterTitle());
        headingRun.setText(chapterNo + " " + title);

        String content = chapter.getContent();
        if (!StringUtils.hasText(content)) {
            return;
        }

        String[] lines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n");

        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (isMarkdownImage(line)) {
                createImage(document, line, config);
                continue;
            }
            if (isTableStart(lines, index)) {
                List<String> tableLines = new ArrayList<>();
                while (index < lines.length && lines[index].trim().startsWith("|")) {
                    tableLines.add(lines[index]);
                    index++;
                }
                index--;
                createTable(document, tableLines, config);
                continue;
            }
            if (!StringUtils.hasText(line)) {
                continue;
            }
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setAlignment(ParagraphAlignment.LEFT);
            paragraph.setSpacingLineRule(org.apache.poi.xwpf.usermodel.LineSpacingRule.AUTO);
            paragraph.setSpacingBetween(config.getLineSpacing() / 240.0);
            paragraph.setSpacingBefore(0);
            paragraph.setSpacingAfter(config.getSpacingAfter());
            paragraph.setIndentationFirstLine(config.getParagraphFirstLineIndent());

            XWPFRun run = paragraph.createRun();
            run.setFontFamily(config.getFontName());
            run.setFontSize(config.getFontSize());
            run.setText(cleanMarkdown(line));
        }
    }

    private boolean isTableStart(String[] lines, int index) {
        return index + 1 < lines.length && lines[index].trim().startsWith("|") && lines[index + 1].contains("---");
    }

    private void createTable(XWPFDocument document, List<String> tableLines, ExportDocxStyleConfig config) {
        if (tableLines == null || tableLines.size() < 2) {
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (String line : tableLines) {
            if (line.contains("---")) {
                continue;
            }
            rows.add(splitMarkdownRow(line));
        }
        if (rows.isEmpty()) {
            return;
        }
        XWPFTable table = document.createTable(rows.size(), rows.get(0).length);
        table.setWidth("100%");
        applyTableStyle(table, config);
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            XWPFTableRow row = table.getRow(rowIndex);
            String[] values = rows.get(rowIndex);
            for (int cellIndex = 0; cellIndex < values.length; cellIndex++) {
                XWPFTableCell cell = row.getCell(cellIndex);
                cell.removeParagraph(0);
                XWPFParagraph paragraph = cell.addParagraph();
                XWPFRun run = paragraph.createRun();
                run.setFontFamily(config.getFontName());
                run.setFontSize(rowIndex == 0 ? config.getTableHeaderFontSize() : config.getTableCellFontSize());
                run.setBold(rowIndex == 0);
                run.setText(cleanMarkdown(values[cellIndex]));
            }
        }
    }

    private String[] splitMarkdownRow(String line) {
        String cleaned = line.trim();
        if (cleaned.startsWith("|")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.endsWith("|")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned.split("\\|");
    }

    private boolean isMarkdownImage(String line) {
        return line != null && line.trim().matches("!\\[[^]]*]\\([^)]+\\)");
    }

    private void createImage(XWPFDocument document, String line, ExportDocxStyleConfig config) {
        Pattern pattern = Pattern.compile("!\\[([^]]*)]\\(([^)]+)\\)");
        Matcher matcher = pattern.matcher(line.trim());
        if (!matcher.find()) {
            return;
        }
        String title = matcher.group(1);
        String url = matcher.group(2);
        ObjectRef objectRef = resolveObjectRef(url);
        if (objectRef == null) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setFontFamily(config.getFontName());
            run.setFontSize(config.getFontSize());
            run.setText("图片：" + title + "（对象地址无效：" + url + "）");
            return;
        }
        try (InputStream inputStream = reportFileStorageService.open(objectRef.bucket, objectRef.objectKey)) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun run = paragraph.createRun();
            run.addPicture(inputStream, pictureType(objectRef.objectKey), objectRef.fileName(), Units.toEMU(420), Units.toEMU(240));
            if (StringUtils.hasText(title)) {
                XWPFParagraph caption = document.createParagraph();
                caption.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun captionRun = caption.createRun();
                captionRun.setFontFamily(config.getFontName());
                captionRun.setFontSize(10);
                captionRun.setText(title);
            }
        } catch (Exception e) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText("图片插入失败：" + e.getMessage());
        }
    }

    private ObjectRef resolveObjectRef(String url) {
        String prefix = reportExportProperties.getUrlPrefix() + "/";
        if (url.startsWith(prefix)) {
            return ObjectRef.from(url.substring(prefix.length()), reportStorageProperties.getMaterialsBucket());
        }
        if (url.startsWith("/")) {
            String marker = "/files/";
            int index = url.indexOf(marker);
            if (index >= 0) {
                return ObjectRef.from(url.substring(index + marker.length()), reportStorageProperties.getMaterialsBucket());
            }
        }
        return ObjectRef.from(url, reportStorageProperties.getMaterialsBucket());
    }

    private int pictureType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) {
            return XWPFDocument.PICTURE_TYPE_PNG;
        }
        if (lower.endsWith(".gif")) {
            return XWPFDocument.PICTURE_TYPE_GIF;
        }
        return XWPFDocument.PICTURE_TYPE_JPEG;
    }


    private void applyTableStyle(XWPFTable table, ExportDocxStyleConfig config) {
        if (table == null || table.getRows() == null || table.getRows().isEmpty()) {
            return;
        }
        for (int rowIndex = 0; rowIndex < table.getRows().size(); rowIndex++) {
            XWPFTableRow row = table.getRow(rowIndex);
            if (row == null) continue;
            if (rowIndex == 0 && config.getTableRowRepeatHeader() != null && config.getTableRowRepeatHeader() == 1) {
                row.setRepeatHeader(true);
            }
            for (int cellIndex = 0; cellIndex < row.getTableCells().size(); cellIndex++) {
                XWPFTableCell cell = row.getCell(cellIndex);
                if (cell == null) continue;
                cell.setVerticalAlignment(org.apache.poi.xwpf.usermodel.XWPFTableCell.XWPFVertAlign.CENTER);
            }
        }
    }

    private String cleanMarkdown(String text) {
        if (text == null) {
            return "";
        }

        String result = text;

        result = result.replaceAll("^#{1,6}\\s*", "");
        result = result.replace("**", "");
        result = result.replace("__", "");
        result = result.replace("`", "");

        return result;
    }

    private String sanitizeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "未命名报告";
        }

        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static class ObjectRef {
        private final String bucket;
        private final String objectKey;

        private ObjectRef(String bucket, String objectKey) {
            this.bucket = bucket;
            this.objectKey = objectKey;
        }

        private static ObjectRef from(String value, String defaultBucket) {
            if (!StringUtils.hasText(value) || value.contains("..")) {
                return null;
            }
            int slash = value.indexOf('/');
            if (slash > 0 && value.substring(0, slash).startsWith("report-")) {
                return new ObjectRef(value.substring(0, slash), value.substring(slash + 1));
            }
            return new ObjectRef(defaultBucket, value);
        }

        private String fileName() {
            int index = objectKey.lastIndexOf('/');
            return index >= 0 ? objectKey.substring(index + 1) : objectKey;
        }
    }
}
