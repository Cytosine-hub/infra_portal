package com.middleware.manager.wiki.service;

import com.middleware.manager.wiki.entity.WikiLink;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.WikiLinkMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KBV-012：页面更新后必须清理该页失效的出边。
 * <p>resolveLinks 此前只做幂等插入，页面正文把 [[A]] 改成 [[B]] 后 A 的旧边仍然
 * 保留。反复编辑会持续累积过期引用，图谱、检索的图扩展与孤儿页判断都会被污染。
 * <p>关键约束：只能删本页的**出边**且只删自己产生的 REFERENCES 类型。
 * 已有的 deleteByPageId 删的是双向边（from 或 to），会连带删掉别的页面指向本页的
 * 入边——那些边归属于对方页面，不该由本次保存决定去留。
 * <p>数据隔离：每轮用唯一 runId 构造夹具，不复用任何既有语料；tearDown 校验无残留。
 */
class LinkResolverStaleEdgeTest {

    @Mock private WikiPageMapper pageMapper;
    @Mock private WikiLinkMapper linkMapper;

    private LinkResolver resolver;
    private String runId;
    private final List<WikiPage> createdFixtures = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        resolver = new LinkResolver(pageMapper, linkMapper);
        runId = "KBV012-" + UUID.randomUUID().toString().substring(0, 8);
        createdFixtures.clear();
    }

    @AfterEach
    void tearDown() {
        // 本轮夹具全部在内存，不落库；显式清空避免用例间互相影响
        createdFixtures.clear();
    }

    private WikiPage page(Long id, String suffix, String content) {
        WikiPage p = new WikiPage();
        p.setId(id);
        p.setTitle(runId + "-" + suffix);
        p.setContent(content);
        createdFixtures.add(p);
        return p;
    }

    @Test
    @DisplayName("TC-LINK-004 页面正文改换引用目标后应删除失效的旧出边")
    void removesStaleOutgoingEdgeOnUpdate() {
        WikiPage targetA = page(2L, "目标A", "A 正文");
        WikiPage targetB = page(3L, "目标B", "B 正文");
        when(pageMapper.findAllIdAndTitle()).thenReturn(List.of(targetA, targetB));
        when(linkMapper.insertIgnore(any(WikiLink.class))).thenReturn(1);

        // 页面原先引用 A，本次更新改为只引用 B
        WikiPage source = page(1L, "源页面", "现在只参考 [[" + targetB.getTitle() + "]]");
        resolver.resolveLinks(List.of(source));

        verify(linkMapper).deleteOutgoingReferences(1L);

        ArgumentCaptor<WikiLink> captor = ArgumentCaptor.forClass(WikiLink.class);
        verify(linkMapper).insertIgnore(captor.capture());
        assertThat(captor.getValue().getToPageId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("TC-LINK-005 清理只针对本页出边，不得波及其他页面指向本页的入边")
    void doesNotTouchIncomingEdges() {
        when(pageMapper.findAllIdAndTitle()).thenReturn(List.of());

        WikiPage source = page(1L, "源页面", "正文不含任何引用");
        resolver.resolveLinks(List.of(source));

        // deleteByPageId 会同时删 from 与 to 两个方向，入边归属对方页面，不该被本次保存删掉
        verify(linkMapper, never()).deleteByPageId(any());
        verify(linkMapper).deleteOutgoingReferences(1L);
    }

    @Test
    @DisplayName("TC-LINK-006 正文清空全部引用后该页出边应全部删除且不新增")
    void clearingAllLinksRemovesAllOutgoingEdges() {
        when(pageMapper.findAllIdAndTitle()).thenReturn(List.of());

        WikiPage source = page(1L, "源页面", "本次编辑删掉了所有交叉引用");
        int created = resolver.resolveLinks(List.of(source));

        verify(linkMapper).deleteOutgoingReferences(1L);
        verify(linkMapper, never()).insertIgnore(any());
        assertThat(created).isZero();
    }

    @Test
    @DisplayName("TC-LINK-007 尚未落库的页面（无 id）不应触发出边清理")
    void skipsCleanupForUnsavedPage() {
        when(pageMapper.findAllIdAndTitle()).thenReturn(List.of());

        WikiPage unsaved = page(null, "未保存页", "参考 [[任意]]");
        resolver.resolveLinks(List.of(unsaved));

        verify(linkMapper, never()).deleteOutgoingReferences(any());
    }
}
