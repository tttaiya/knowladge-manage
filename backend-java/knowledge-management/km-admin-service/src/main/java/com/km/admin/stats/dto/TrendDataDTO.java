package com.km.admin.stats.dto;

/**
 * 趋势数据点：单日 {date, count}。
 * date 格式 yyyy-MM-dd（DATE_FORMAT 输出字符串）。
 */
public class TrendDataDTO {

    private String date;
    private long count;

    public TrendDataDTO() {}

    public TrendDataDTO(String date, long count) {
        this.date = date;
        this.count = count;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
}