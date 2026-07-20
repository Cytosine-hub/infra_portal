package com.middleware.manager.wiki.service;

import com.middleware.manager.wiki.entity.WikiPage;

import java.util.List;

public class WikiSearchResult {
    private WikiPage page;
    private float score;
    private String matchSource;
    private List<String> relatedPageTitles;

    public WikiPage getPage() { return page; }
    public void setPage(WikiPage page) { this.page = page; }
    public float getScore() { return score; }
    public void setScore(float score) { this.score = score; }
    public String getMatchSource() { return matchSource; }
    public void setMatchSource(String matchSource) { this.matchSource = matchSource; }
    public List<String> getRelatedPageTitles() { return relatedPageTitles; }
    public void setRelatedPageTitles(List<String> relatedPageTitles) { this.relatedPageTitles = relatedPageTitles; }
}
