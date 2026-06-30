package com.km.admin.document.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

public class DocumentTag implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long docId;
    private String tagName;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }

    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
