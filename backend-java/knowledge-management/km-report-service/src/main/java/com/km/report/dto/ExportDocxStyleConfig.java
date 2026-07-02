package com.km.report.dto;

import lombok.Data;

@Data
public class ExportDocxStyleConfig {
    private String fontName = "宋体";
    private Integer fontSize = 12;
    private Integer heading1FontSize = 18;
    private Integer heading2FontSize = 16;
    private Integer heading3FontSize = 14;
    private Integer spacingBefore = 120;
    private Integer spacingAfter = 120;
    private Integer lineSpacing = 360;
    private Integer paragraphFirstLineIndent = 420;
    private Integer pageMarginTop = 1440;
    private Integer pageMarginRight = 1080;
    private Integer pageMarginBottom = 1440;
    private Integer pageMarginLeft = 1080;
    private Integer enableHeaderFooter = 1;
    private String headerText = "电力监督AI平台";
    private String footerText = "本报告由系统生成，请在 Word 中更新目录域后查看最新目录";
    private Integer headerFontSize = 9;
    private Integer footerFontSize = 9;
    private Integer tableBorderStyle = 1;
    private Integer tableHeaderFontSize = 12;
    private Integer tableCellFontSize = 12;
    private Integer tableRowRepeatHeader = 1;
    private String tableHeaderBackground = "D9E2F3";
    private String tableHeaderTextColor = "000000";
}
