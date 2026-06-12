package com.middleware.manager.wiki.service;

import com.middleware.manager.wiki.entity.LintResult;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.LintResultMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class LintAgent {
    /** @see LinkResolver#WIKILINK_PATTERN */
    private static final Pattern WIKILINK_PATTERN = LinkResolver.WIKILINK_PATTERN;

    private final WikiPageMapper pageMapper;
    private final LintResultMapper lintResultMapper;

    public LintAgent(WikiPageMapper pageMapper,
                     LintResultMapper lintResultMapper) {
        this.pageMapper = pageMapper;
        this.lintResultMapper = lintResultMapper;
    }

    /**
     * Run all lint checks, persist results, and return them.
     */
    @Transactional
    public List<LintResult> runLint() {
        // Clean up old resolved results before running
        lintResultMapper.deleteResolved();

        List<LintResult> results = new ArrayList<>();
        results.addAll(detectOrphanPages());
        results.addAll(detectBrokenLinks());
        results.addAll(detectStaleContent());
        results.addAll(detectUnresolvedContradictions());
        results.addAll(detectMissingSourceRefs());
        results.addAll(detectActivePagesWithoutReview());
        results.addAll(detectOversizedPages());
        results.addAll(detectDuplicateTitles());

        // Persist all new results
        for (LintResult result : results) {
            lintResultMapper.upsert(result);
        }

        log.info("Lint run completed: {} issues found", results.size());
        return results;
    }

    /**
     * Find ACTIVE pages with no incoming links (orphan pages).
     */
    public List<LintResult> detectOrphanPages() {
        List<LintResult> results = new ArrayList<>();
        List<WikiPage> orphans = pageMapper.findOrphanPages();
        for (WikiPage page : orphans) {
            LintResult r = new LintResult();
            fill(r, "ORPHAN", page.getId(), "page:" + page.getId(),
                    "Page \"" + page.getTitle() + "\" has no incoming links and may be hard to discover.",
                    "MEDIUM");
            results.add(r);
        }
        log.debug("Detected {} orphan pages", orphans.size());
        return results;
    }

    /**
     * Extract [[wikilinks]] from all pages and check if targets exist.
     */
    public List<LintResult> detectBrokenLinks() {
        List<LintResult> results = new ArrayList<>();

        // Build a set of all existing page titles
        Set<String> existingTitles = new HashSet<>();
        for (WikiPage p : pageMapper.findAllIdAndTitle()) {
            existingTitles.add(p.getTitle());
        }

        // Scan all pages for [[links]]
        List<WikiPage> allPages = pageMapper.findAll();
        for (WikiPage page : allPages) {
            if (page.getContent() == null) continue;
            Matcher matcher = WIKILINK_PATTERN.matcher(page.getContent());
            while (matcher.find()) {
                String target = matcher.group(1).trim();
                if (!existingTitles.contains(target)) {
                    LintResult r = new LintResult();
                    fill(r, "BROKEN_LINK", page.getId(), "page:" + page.getId() + ":target:" + target,
                            "Page \"" + page.getTitle() + "\" contains broken link [[" + target + "]] — target page does not exist.",
                            "HIGH");
                    results.add(r);
                }
            }
        }
        log.debug("Detected {} broken links", results.size());
        return results;
    }

    /**
     * Find ACTIVE pages that haven't been updated in over 365 days.
     */
    public List<LintResult> detectStaleContent() {
        List<LintResult> results = new ArrayList<>();
        List<WikiPage> stalePages = pageMapper.findStalePages(365);
        for (WikiPage page : stalePages) {
            LintResult r = new LintResult();
            fill(r, "STALE", page.getId(), "page:" + page.getId(),
                    "Page \"" + page.getTitle() + "\" has not been updated for over 365 days (last updated: " + page.getUpdatedAt() + ").",
                    "LOW");
            results.add(r);
        }
        log.debug("Detected {} stale pages", stalePages.size());
        return results;
    }

    /**
     * Find pages with status=CONTRADICTED.
     */
    public List<LintResult> detectUnresolvedContradictions() {
        List<LintResult> results = new ArrayList<>();
        List<WikiPage> contradicted = pageMapper.findByStatus("CONTRADICTED");
        for (WikiPage page : contradicted) {
            LintResult r = new LintResult();
            fill(r, "CONTRADICTION", page.getId(), "page:" + page.getId(),
                    "Page \"" + page.getTitle() + "\" is marked as CONTRADICTED. " +
                            (page.getContradictionNote() != null ? "Note: " + page.getContradictionNote() : "No details provided."),
                    "HIGH");
            results.add(r);
        }
        log.debug("Detected {} contradicted pages", contradicted.size());
        return results;
    }

    public List<LintResult> detectMissingSourceRefs() {
        List<LintResult> results = new ArrayList<>();
        for (WikiPage page : pageMapper.findAllExcludingContent()) {
            if (!"ACTIVE".equals(page.getStatus())) continue;
            if (page.getSourceRefs() == null || page.getSourceRefs().isBlank()) {
                LintResult r = new LintResult();
                fill(r, "GAP", page.getId(), "missing-source:" + page.getId(),
                        "Page \"" + page.getTitle() + "\" is ACTIVE but has no source references.",
                        "MEDIUM");
                results.add(r);
            }
        }
        return results;
    }

    public List<LintResult> detectActivePagesWithoutReview() {
        List<LintResult> results = new ArrayList<>();
        for (WikiPage page : pageMapper.findByStatus("ACTIVE")) {
            if (page.getReviewedBy() == null || page.getReviewedAt() == null) {
                LintResult r = new LintResult();
                fill(r, "GAP", page.getId(), "missing-review:" + page.getId(),
                        "Page \"" + page.getTitle() + "\" is ACTIVE but has no review record.",
                        "MEDIUM");
                results.add(r);
            }
        }
        return results;
    }

    public List<LintResult> detectOversizedPages() {
        List<LintResult> results = new ArrayList<>();
        for (WikiPage page : pageMapper.findAll()) {
            int length = page.getContent() != null ? page.getContent().length() : 0;
            if (length > 30000) {
                LintResult r = new LintResult();
                fill(r, "GAP", page.getId(), "oversized:" + page.getId(),
                        "Page \"" + page.getTitle() + "\" is " + length + " characters and should be split for reliable retrieval.",
                        "LOW");
                results.add(r);
            }
        }
        return results;
    }

    public List<LintResult> detectDuplicateTitles() {
        Map<String, List<WikiPage>> groups = new HashMap<>();
        for (WikiPage page : pageMapper.findAllExcludingContent()) {
            String key = normalize(page.getTitle()) + "|" + nullToEmpty(page.getSoftware()) + "|" + nullToEmpty(page.getVersion());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(page);
        }

        List<LintResult> results = new ArrayList<>();
        for (List<WikiPage> pages : groups.values()) {
            if (pages.size() <= 1) continue;
            WikiPage first = pages.get(0);
            LintResult r = new LintResult();
            fill(r, "GAP", first.getId(), "duplicate:" + normalize(first.getTitle()) + ":" + nullToEmpty(first.getSoftware()) + ":" + nullToEmpty(first.getVersion()),
                    "Multiple pages share a similar title/software/version: " +
                            pages.stream().map(WikiPage::getTitle).distinct().toList(),
                    "MEDIUM");
            results.add(r);
        }
        return results;
    }

    private void fill(LintResult result, String type, Long pageId, String target, String description, String severity) {
        LocalDateTime now = LocalDateTime.now();
        result.setLintType(type);
        result.setPageId(pageId);
        result.setFingerprint(type + ":" + target);
        result.setDescription(description);
        result.setSeverity(severity);
        result.setResolved(false);
        result.setFirstSeenAt(now);
        result.setLastSeenAt(now);
        result.setCreatedAt(now);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
