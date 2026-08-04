package com.middleware.manager.wiki.service;

import com.middleware.manager.wiki.entity.WikiLink;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.WikiLinkMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * 「先删后插」是本次新引入的破坏性路径：删除成功而插入失败时，本页出边被清空且
 * 无任何补偿。修改前只做幂等插入，失败是无害的。因此这一步必须在事务内，
 * 且异常必须能传播出去触发回滚。
 */
class LinkResolverTransactionTest {

    @Mock private WikiPageMapper pageMapper;
    @Mock private WikiLinkMapper linkMapper;

    private LinkResolver resolver;
    private String runId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        resolver = new LinkResolver(pageMapper, linkMapper);
        runId = "KBV12T-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private WikiPage page(Long id, String suffix, String content) {
        WikiPage p = new WikiPage();
        p.setId(id);
        p.setTitle(runId + "-" + suffix);
        p.setContent(content);
        return p;
    }

    @Test
    @DisplayName("TC-LINK-009 resolveLinks 必须标注事务，删除与插入不得半途落库")
    void resolveLinksIsTransactional() throws NoSuchMethodException {
        Method method = LinkResolver.class.getMethod("resolveLinks", List.class);

        assertThat(method.isAnnotationPresent(Transactional.class))
                .as("先删后插是多步写入，插入失败必须回滚删除")
                .isTrue();
    }

    @Test
    @DisplayName("TC-LINK-010 删除必须先于插入执行，顺序颠倒会把新边一并删掉")
    void deleteHappensBeforeInsert() {
        WikiPage target = page(2L, "目标", "正文");
        when(pageMapper.findAllIdAndTitle()).thenReturn(List.of(target));
        when(linkMapper.insertIgnore(any(WikiLink.class))).thenReturn(1);

        WikiPage source = page(1L, "源页", "参考 [[" + target.getTitle() + "]]");
        resolver.resolveLinks(List.of(source));

        InOrder order = inOrder(linkMapper);
        order.verify(linkMapper).deleteOutgoingReferences(1L);
        order.verify(linkMapper).insertIgnore(any(WikiLink.class));
    }

    @Test
    @DisplayName("TC-LINK-011 插入失败应向上抛出以触发回滚，不得静默吞掉")
    void insertFailurePropagates() {
        WikiPage target = page(2L, "目标", "正文");
        when(pageMapper.findAllIdAndTitle()).thenReturn(List.of(target));
        when(linkMapper.insertIgnore(any(WikiLink.class)))
                .thenThrow(new RuntimeException("数据库连接抖动"));

        WikiPage source = page(1L, "源页", "参考 [[" + target.getTitle() + "]]");

        assertThatThrownBy(() -> resolver.resolveLinks(List.of(source)))
                .isInstanceOf(RuntimeException.class);
    }
}
