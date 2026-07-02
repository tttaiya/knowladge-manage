package com.km.report.dto;

import lombok.Data;

import java.util.List;

@Data
public class InsertTableRequest {
    private List<String> headers;
    private List<List<String>> rows;
    private String title;
}
