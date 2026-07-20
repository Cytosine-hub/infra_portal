package com.middleware.manager.wiki.service;

import com.middleware.manager.wiki.entity.WikiLink;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.WikiLinkMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LinkResolverTest {

    @Mock
    private WikiPageMapper pageMapper;
    @Mock
    private WikiLinkMapper linkMapper;

    private LinkResolver linkResolver;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        linkResolver = new LinkResolver(pageMapper, linkMapper);
    }

    @Nested
    @DisplayName("extractWikiLinks")
    class ExtractWikiLinks {

        @Test
        @DisplayName("提取单个 wikilink")
        void singleLink() {
            List<String> links = linkResolver.extractWikiLinks("See [[Redis]] for details");
            assertEquals(1, links.size());
            assertEquals("Redis", links.get(0));
        }

        @Test
        @DisplayName("提取多个 wikilink")
        void multipleLinks() {
            List<String> links = linkResolver.extractWikiLinks("Use [[Redis]] or [[Memcached]] for caching");
            assertEquals(2, links.size());
            assertEquals("Redis", links.get(0));
            assertEquals("Memcached", links.get(1));
        }

        @Test
        @DisplayName("无 wikilink 返回空列表")
        void noLinks() {
            List<String> links = linkResolver.extractWikiLinks("No links here");
            assertTrue(links.isEmpty());
        }

        @Test
        @DisplayName("null 内容返回空列表")
        void nullContent() {
            List<String> links = linkResolver.extractWikiLinks(null);
            assertTrue(links.isEmpty());
        }

        @Test
        @DisplayName("空内容返回空列表")
        void emptyContent() {
            List<String> links = linkResolver.extractWikiLinks("");
            assertTrue(links.isEmpty());
        }

        @Test
        @DisplayName("链接两端有空白被 trim")
        void linkWithWhitespace() {
            List<String> links = linkResolver.extractWikiLinks("See [[ Redis ]] here");
            assertEquals(1, links.size());
            assertEquals("Redis", links.get(0));
        }

        @Test
        @DisplayName("连续的 wikilink")
        void consecutiveLinks() {
            List<String> links = linkResolver.extractWikiLinks("[[A]][[B]][[C]]");
            assertEquals(3, links.size());
            assertEquals(Arrays.asList("A", "B", "C"), links);
        }

        @Test
        @DisplayName("wikilink 在 Markdown 其他语法内")
        void linkInsideMarkdown() {
            List<String> links = linkResolver.extractWikiLinks("**bold [[Redis]] text**");
            assertEquals(1, links.size());
            assertEquals("Redis", links.get(0));
        }
    }

    @Nested
    @DisplayName("resolveLinks")
    class ResolveLinks {

        @Test
        @DisplayName("解析页面间的 wikilink 并创建链接")
        void resolveLinksBetweenPages() {
            WikiPage source = createPage(1L, "Source Page", "OVERVIEW",
                    "See [[Target Page]] for details");
            WikiPage target = createPage(2L, "Target Page", "CONFIG", "content");

            when(pageMapper.findAllIdAndTitle()).thenReturn(Arrays.asList(source, target));
            when(linkMapper.insertIgnore(any(WikiLink.class))).thenReturn(1);

            int created = linkResolver.resolveLinks(Arrays.asList(source, target));

            assertEquals(1, created);
            verify(linkMapper).insertIgnore(any(WikiLink.class));
        }

        @Test
        @DisplayName("不创建指向自身的链接")
        void noSelfLink() {
            WikiPage page = createPage(1L, "Self Ref", "OVERVIEW",
                    "See [[Self Ref]] for details");

            when(pageMapper.findAllIdAndTitle()).thenReturn(Collections.singletonList(page));

            int created = linkResolver.resolveLinks(Collections.singletonList(page));

            assertEquals(0, created);
            verify(linkMapper, never()).insertIgnore(any());
        }

        @Test
        @DisplayName("不存在的目标页面不创建链接")
        void brokenLinkIgnored() {
            WikiPage source = createPage(1L, "Source", "OVERVIEW",
                    "See [[Nonexistent]] for details");

            when(pageMapper.findAllIdAndTitle()).thenReturn(Collections.singletonList(source));

            int created = linkResolver.resolveLinks(Collections.singletonList(source));

            assertEquals(0, created);
            verify(linkMapper, never()).insertIgnore(any());
        }

        @Test
        @DisplayName("null ID 的页面不创建链接")
        void nullIdPageSkipped() {
            WikiPage page = createPage(null, "No ID", "OVERVIEW", "[[Other]]");
            WikiPage other = createPage(2L, "Other", "CONFIG", "content");
            when(pageMapper.findAllIdAndTitle()).thenReturn(Collections.singletonList(other));

            int created = linkResolver.resolveLinks(Collections.singletonList(page));

            assertEquals(0, created);
            verify(linkMapper, never()).insertIgnore(any());
        }

        @Test
        @DisplayName("null 内容的页面不报错")
        void nullContentPageSafe() {
            WikiPage page = createPage(1L, "Null Content", "OVERVIEW", null);

            when(pageMapper.findAllIdAndTitle()).thenReturn(Collections.singletonList(page));

            int created = linkResolver.resolveLinks(Collections.singletonList(page));

            assertEquals(0, created);
        }
    }

    @Nested
    @DisplayName("findBrokenLinks")
    class FindBrokenLinks {

        @Test
        @DisplayName("找到断链")
        void findsBrokenLinks() {
            WikiPage page = createPage(1L, "Source", "OVERVIEW",
                    "See [[Missing Page]] and [[Redis]]");

            WikiPage redis = createPage(2L, "Redis", "CONFIG", "content");
            when(pageMapper.findAllIdAndTitle()).thenReturn(Arrays.asList(page, redis));

            List<String> broken = linkResolver.findBrokenLinks(page);

            assertEquals(1, broken.size());
            assertEquals("Missing Page", broken.get(0));
        }

        @Test
        @DisplayName("无断链返回空列表")
        void noBrokenLinks() {
            WikiPage page = createPage(1L, "Source", "OVERVIEW", "See [[Redis]]");
            WikiPage redis = createPage(2L, "Redis", "CONFIG", "content");

            when(pageMapper.findAllIdAndTitle()).thenReturn(Arrays.asList(page, redis));

            List<String> broken = linkResolver.findBrokenLinks(page);

            assertTrue(broken.isEmpty());
        }
    }

    private WikiPage createPage(Long id, String title, String pageType, String content) {
        WikiPage page = new WikiPage();
        page.setId(id);
        page.setTitle(title);
        page.setPageType(pageType);
        page.setContent(content);
        page.setStatus("ACTIVE");
        return page;
    }
}
