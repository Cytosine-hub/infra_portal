package com.middleware.manager.wiki.web.dto;

import com.middleware.manager.wiki.entity.WikiPage;

/**
 * 页面保存 / 重建关联的响应。
 * <p>把「页面本身」与「建边结果」分开表达：建边失败不该让保存失败（页面内容不能
 * 因为副产物出问题而丢失），但也不能像此前那样只写日志——用户会以为交叉引用已
 * 生效，而图谱与图扩展检索实际都少了边，且没有任何重试入口。
 */
public class WikiPageSaveResponse {

    private WikiPage page;
    /** 建边失败时的可读提示；成功时为 null，避免制造噪音。 */
    private String linkWarning;
    /** 本次解析出的关联数，供「重建关联」反馈实际效果。 */
    private Integer linksCreated;

    public static WikiPageSaveResponse ok(WikiPage page, int linksCreated) {
        WikiPageSaveResponse r = new WikiPageSaveResponse();
        r.page = page;
        r.linksCreated = linksCreated;
        return r;
    }

    public static WikiPageSaveResponse withWarning(WikiPage page, String warning) {
        WikiPageSaveResponse r = new WikiPageSaveResponse();
        r.page = page;
        r.linkWarning = warning;
        return r;
    }

    public WikiPage getPage() { return page; }
    public void setPage(WikiPage page) { this.page = page; }
    public String getLinkWarning() { return linkWarning; }
    public void setLinkWarning(String linkWarning) { this.linkWarning = linkWarning; }
    public Integer getLinksCreated() { return linksCreated; }
    public void setLinksCreated(Integer linksCreated) { this.linksCreated = linksCreated; }
}
