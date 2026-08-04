package com.middleware.manager.wiki.service;

import com.middleware.manager.wiki.entity.WikiLink;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.WikiLinkMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class LinkResolver {
    public static final Pattern WIKILINK_PATTERN = Pattern.compile("\\[\\[([^\\]]+)\\]\\]");

    private final WikiPageMapper pageMapper;
    private final WikiLinkMapper linkMapper;

    public LinkResolver(WikiPageMapper pageMapper, WikiLinkMapper linkMapper) {
        this.pageMapper = pageMapper;
        this.linkMapper = linkMapper;
    }

    public List<String> extractWikiLinks(String content) {
        List<String> links = new ArrayList<>();
        if (content == null) return links;
        Matcher matcher = WIKILINK_PATTERN.matcher(content);
        while (matcher.find()) {
            links.add(matcher.group(1).trim());
        }
        return links;
    }

    /**
     * 按当前正文重建各页出边。
     * <p>必须在事务内：本方法是「先删后插」的多步写入，删除成功而插入失败会让该页
     * 出边被清空且无补偿——图谱与图扩展检索直接少边，用户不会收到任何提示。
     * 改造前只做幂等插入，失败是无害的，这条破坏性路径是随清理逻辑一起引入的。
     */
    @Transactional
    public int resolveLinks(List<WikiPage> pages) {
        Map<String, Long> titleIndex = new HashMap<>();
        for (WikiPage page : pages) {
            if (page.getId() != null) {
                titleIndex.put(page.getTitle(), page.getId());
            }
        }
        // 只查 id 和 title，避免 SELECT * 携带大 content 列导致排序溢出
        List<WikiPage> existingPages = pageMapper.findAllIdAndTitle();
        for (WikiPage page : existingPages) {
            titleIndex.putIfAbsent(page.getTitle(), page.getId());
        }

        int created = 0;
        for (WikiPage page : pages) {
            if (page.getId() == null) continue;
            // 先清掉本页已有的出边再按当前正文重建：仅做幂等插入的话，正文把 [[A]]
            // 改成 [[B]] 后 A 的旧边会一直留着，反复编辑持续累积过期引用。
            linkMapper.deleteOutgoingReferences(page.getId());
            List<String> linkTargets = extractWikiLinks(page.getContent());
            for (String target : linkTargets) {
                Long targetId = titleIndex.get(target);
                if (targetId == null || targetId.equals(page.getId())) continue;

                WikiLink link = new WikiLink();
                link.setFromPageId(page.getId());
                link.setToPageId(targetId);
                link.setLinkType("REFERENCES");
                link.setConfidence(new BigDecimal("0.90"));
                created += linkMapper.insertIgnore(link);
            }
        }
        log.info("Resolved {} wiki links from {} pages", created, pages.size());
        return created;
    }

    public List<String> findBrokenLinks(WikiPage page) {
        List<String> broken = new ArrayList<>();
        List<String> targets = extractWikiLinks(page.getContent());
        Set<String> existingTitles = new HashSet<>();
        for (WikiPage p : pageMapper.findAllIdAndTitle()) {
            existingTitles.add(p.getTitle());
        }
        for (String target : targets) {
            if (!existingTitles.contains(target)) {
                broken.add(target);
            }
        }
        return broken;
    }
}
