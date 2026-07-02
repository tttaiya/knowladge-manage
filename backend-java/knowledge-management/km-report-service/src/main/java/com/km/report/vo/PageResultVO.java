package com.km.report.vo;

import lombok.Data;
import java.util.List;

@Data
public class PageResultVO<T> {
    private Long total;
    private Long pageNum;
    private Long pageSize;
    private List<T> records;
}
