package com.middleware.manager.knowledge.service;

public class KnowledgeSearchResult {
    private String content;
    private String sourceTitle;
    private String sourceType;
    private Long sourceId;
    private String category;
    private String software;
    private float score;
    private String source;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSourceTitle() { return sourceTitle; }
    public void setSourceTitle(String sourceTitle) { this.sourceTitle = sourceTitle; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSoftware() { return software; }
    public void setSoftware(String software) { this.software = software; }
    public float getScore() { return score; }
    public void setScore(float score) { this.score = score; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
