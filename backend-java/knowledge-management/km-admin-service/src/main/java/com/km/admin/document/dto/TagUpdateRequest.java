package com.km.admin.document.dto;

import java.util.List;

public class TagUpdateRequest {
    private List<String> tags;

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
