package com.middleware.manager.wiki.web;

import com.middleware.manager.wiki.entity.WikiLink;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.WikiLinkMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import com.middleware.manager.wiki.service.LinkResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 人工书写的经验页面保存后必须解析 [[页面标题]] 建边。
 * <p>删除 LLM 编译流水线时，LinkResolver 失去了唯一调用方（原本由 IngestAgent 在
 * 保存页面后调用），而补上的 createPage / updatePage 都没有接。后果是团队认真写的
 * 交叉引用永远不会变成边——图谱恒空、检索的图扩展扩不出任何东西，三个功能同时
 * 处于「看起来有、实际不工作」的状态。
 */
class WikiPageLinkingTest {

    @Mock private WikiPageMapper pageMapper;
    @Mock private WikiLinkMapper linkMapper;

    private LinkResolver resolver;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        resolver = new LinkResolver(pageMapper, linkMapper);
    }

    private WikiPage page(Long id, String title, String content) {
        WikiPage p = new WikiPage();
        p.setId(id);
        p.setTitle(title);
        p.setContent(content);
        return p;
    }

    @Test
    @DisplayName("TC-LINK-001 正文中的 [[标题]] 应解析成指向目标页面的边")
    void resolvesWikiLinkToEdge() {
        WikiPage target = page(2L, "连接池配置", "正文");
        when(pageMapper.findAllIdAndTitle()).thenReturn(List.of(target));
        when(linkMapper.insertIgnore(any(WikiLink.class))).thenReturn(1);

        WikiPage source = page(1L, "主从延迟处理", "先看 [[连接池配置]] 里的上限设置");
        int created = resolver.resolveLinks(List.of(source));

        ArgumentCaptor<WikiLink> captor = ArgumentCaptor.forClass(WikiLink.class);
        verify(linkMapper).insertIgnore(captor.capture());
        assertThat(created).isEqualTo(1);
        assertThat(captor.getValue().getFromPageId()).isEqualTo(1L);
        assertThat(captor.getValue().getToPageId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("TC-LINK-002 目标页面不存在时不建边，交由 Lint 报断链")
    void skipsUnresolvableLink() {
        when(pageMapper.findAllIdAndTitle()).thenReturn(List.of());

        WikiPage source = page(1L, "主从延迟处理", "参考 [[根本不存在的页面]]");
        int created = resolver.resolveLinks(List.of(source));

        assertThat(created).isZero();
        verify(linkMapper, never()).insertIgnore(any());
    }

    @Test
    @DisplayName("TC-LINK-003 没有 wikilink 的页面不应产生任何边")
    void plainPageProducesNoEdge() {
        when(pageMapper.findAllIdAndTitle()).thenReturn(List.of());

        int created = resolver.resolveLinks(List.of(page(1L, "纯文本页", "没有任何交叉引用")));

        assertThat(created).isZero();
        verify(linkMapper, never()).insertIgnore(any());
    }
}
