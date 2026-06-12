package com.middleware.manager.wiki.service;

import com.middleware.manager.wiki.entity.WikiLink;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.WikiLinkMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class WikiGraphService {

    private final WikiPageMapper pageMapper;
    private final WikiLinkMapper linkMapper;
    private final WikiPermissionService permissionService;

    @Value("${app.wiki.graph.max-nodes:1000}")
    private int maxNodes;

    private volatile Map<String, Object> cachedPublicGraph;
    private volatile long cachedAtMillis;
    private static final long CACHE_TTL_MILLIS = 30_000L;

    // 5-signal weights
    private static final double W_DIRECT_LINK = 3.0;
    private static final double W_SAME_SOFTWARE = 4.0;
    private static final double W_SAME_CATEGORY = 2.0;
    private static final double W_CO_OCCURRENCE = 1.5;
    private static final double W_TRANSITIVE_DEP = 2.5;
    private static final Set<String> PUBLIC_GRAPH_STATUSES = Set.of("ACTIVE", "CONTRADICTED");
    private static final Set<String> AUTHENTICATED_GRAPH_STATUSES = Set.of(
            "DRAFT", "PENDING_REVIEW", "ACTIVE", "CONTRADICTED");

    public WikiGraphService(WikiPageMapper pageMapper, WikiLinkMapper linkMapper,
                            WikiPermissionService permissionService) {
        this.pageMapper = pageMapper;
        this.linkMapper = linkMapper;
        this.permissionService = permissionService;
    }

    /**
     * 构建完整的知识图谱：5 信号评分 + 软件类型社区聚类。
     */
    public Map<String, Object> buildGraph() {
        long now = System.currentTimeMillis();
        Map<String, Object> cached = cachedPublicGraph;
        if (cached != null && now - cachedAtMillis < CACHE_TTL_MILLIS) {
            return cached;
        }
        Map<String, Object> graph = buildGraph(null);
        cachedPublicGraph = graph;
        cachedAtMillis = now;
        return graph;
    }

    public Map<String, Object> buildGraph(Authentication authentication) {
        boolean authenticated = isRealUser(authentication);
        List<WikiPage> pages = pageMapper.findAllExcludingContent();
        if (authenticated) {
            pages = permissionService.filterVisiblePages(authentication, pages);
        }
        pages = pages.stream()
                .filter(page -> isGraphVisibleStatus(page, authenticated))
                .limit(Math.max(1, maxNodes))
                .collect(Collectors.toList());
        List<WikiLink> links = linkMapper.findAll();

        if (pages.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("nodes", Collections.emptyList());
            empty.put("links", Collections.emptyList());
            empty.put("communityCount", 0);
            empty.put("communities", Collections.emptyMap());
            empty.put("communityStats", Collections.emptyList());
            return empty;
        }

        // 构建数据索引
        Map<Long, WikiPage> pageMap = new HashMap<>();
        for (WikiPage p : pages) pageMap.put(p.getId(), p);

        Map<Long, Set<Long>> directLinks = new HashMap<>();
        Map<String, Set<Long>> softwareIndex = new HashMap<>();
        Map<String, Set<Long>> categoryIndex = new HashMap<>();
        Map<Long, Set<Long>> dependsOnChains = new HashMap<>();
        Map<String, Set<Long>> sourceIndex = new HashMap<>();

        for (WikiPage p : pages) {
            directLinks.putIfAbsent(p.getId(), new HashSet<>());
            if (p.getSoftware() != null && !p.getSoftware().isEmpty()) {
                softwareIndex.computeIfAbsent(p.getSoftware(), k -> new HashSet<>()).add(p.getId());
            }
            if (p.getCategory() != null && !p.getCategory().isEmpty()) {
                categoryIndex.computeIfAbsent(p.getCategory(), k -> new HashSet<>()).add(p.getId());
            }
            if (p.getSourceRefs() != null && !p.getSourceRefs().isBlank()) {
                sourceIndex.computeIfAbsent(p.getSourceRefs(), k -> new HashSet<>()).add(p.getId());
            }
        }

        for (WikiLink link : links) {
            if (!pageMap.containsKey(link.getFromPageId()) || !pageMap.containsKey(link.getToPageId())) {
                continue;
            }
            directLinks.computeIfAbsent(link.getFromPageId(), k -> new HashSet<>()).add(link.getToPageId());
            directLinks.computeIfAbsent(link.getToPageId(), k -> new HashSet<>()).add(link.getFromPageId());
            if ("DEPENDS_ON".equals(link.getLinkType())) {
                dependsOnChains.computeIfAbsent(link.getFromPageId(), k -> new HashSet<>()).add(link.getToPageId());
            }
        }

        // 5-signal 评分
        Graph<Long, DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (WikiPage p : pages) graph.addVertex(p.getId());

        Map<String, Double> edgeWeights = new HashMap<>();

        // 信号1: 直接链接
        for (var entry : directLinks.entrySet()) {
            long from = entry.getKey();
            for (long to : entry.getValue()) {
                if (from < to) addEdgeWeight(graph, edgeWeights, from, to, W_DIRECT_LINK);
            }
        }

        // 信号2: 同一软件
        for (Set<Long> group : softwareIndex.values()) {
            List<Long> list = new ArrayList<>(group);
            if (list.size() > 80) {
                list = list.stream()
                        .filter(id -> {
                            String type = pageMap.get(id).getPageType();
                            return "OVERVIEW".equals(type) || "RUNBOOK".equals(type) || "ENTITY".equals(type);
                        })
                        .limit(80)
                        .collect(Collectors.toList());
            }
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    addEdgeWeight(graph, edgeWeights, list.get(i), list.get(j), W_SAME_SOFTWARE);
                }
            }
        }

        // 信号4: 同一来源文档弱关联
        for (Set<Long> group : sourceIndex.values()) {
            List<Long> list = new ArrayList<>(group);
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    addEdgeWeight(graph, edgeWeights, list.get(i), list.get(j), W_CO_OCCURRENCE);
                }
            }
        }

        // 信号3: 同一分类（仅 OVERVIEW 页面之间，避免不同软件被拉到同一社区）
        for (Set<Long> group : categoryIndex.values()) {
            List<Long> list = new ArrayList<>(group);
            // 只取 OVERVIEW 类型的页面
            List<Long> overviews = list.stream()
                    .filter(id -> "OVERVIEW".equals(pageMap.get(id).getPageType()))
                    .collect(java.util.stream.Collectors.toList());
            for (int i = 0; i < overviews.size(); i++) {
                for (int j = i + 1; j < overviews.size(); j++) {
                    addEdgeWeight(graph, edgeWeights, overviews.get(i), overviews.get(j), W_SAME_CATEGORY);
                }
            }
            // OVERVIEW 到同分类其他页面也加边（分类入口连接）
            for (long ovId : overviews) {
                for (long pageId : list) {
                    if (ovId != pageId) {
                        addEdgeWeight(graph, edgeWeights, ovId, pageId, W_SAME_CATEGORY * 0.5);
                    }
                }
            }
        }

        // 信号5: 依赖链传递（1跳）
        for (var entry : dependsOnChains.entrySet()) {
            long from = entry.getKey();
            for (long mid : entry.getValue()) {
                Set<Long> transitive = dependsOnChains.getOrDefault(mid, Collections.emptySet());
                for (long to : transitive) {
                    if (from != to) addEdgeWeight(graph, edgeWeights, from, to, W_TRANSITIVE_DEP);
                }
            }
        }

        // 设置边权重
        for (DefaultWeightedEdge edge : graph.edgeSet()) {
            long s = graph.getEdgeSource(edge);
            long t = graph.getEdgeTarget(edge);
            String key = edgeKey(s, t);
            graph.setEdgeWeight(edge, edgeWeights.getOrDefault(key, 0.0));
        }

        StableCommunities stableCommunities = buildSoftwareCommunities(pages);
        Map<Long, Integer> communities = stableCommunities.nodeCommunities();

        // 构建输出
        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<Long, Integer> nodeIdMap = new HashMap<>();
        int idx = 0;
        for (WikiPage p : pages) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", idx);
            node.put("pageId", p.getId());
            node.put("name", p.getTitle());
            node.put("pageType", p.getPageType());
            node.put("category", p.getCategory());
            node.put("software", p.getSoftware());
            node.put("status", p.getStatus());
            node.put("community", communities.getOrDefault(p.getId(), 0));
            node.put("communityKey", stableCommunities.communityKeys().get(communities.getOrDefault(p.getId(), 0)));
            node.put("communityName", stableCommunities.communityNames().getOrDefault(communities.getOrDefault(p.getId(), 0), ""));
            nodes.add(node);
            nodeIdMap.put(p.getId(), idx);
            idx++;
        }

        List<Map<String, Object>> edges = new ArrayList<>();
        for (DefaultWeightedEdge edge : graph.edgeSet()) {
            long src = graph.getEdgeSource(edge);
            long tgt = graph.getEdgeTarget(edge);
            Integer from = nodeIdMap.get(src);
            Integer to = nodeIdMap.get(tgt);
            if (from != null && to != null) {
                double weight = graph.getEdgeWeight(edge);
                Map<String, Object> link = new HashMap<>();
                link.put("source", from);
                link.put("target", to);
                link.put("weight", Math.round(weight * 100.0) / 100.0);
                link.put("linkType", resolveLinkType(src, tgt, links));
                edges.add(link);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("links", edges);
        result.put("communityCount", stableCommunities.communityNames().size());
        result.put("communities", stableCommunities.communityNames());
        result.put("communityStats", buildCommunityStats(stableCommunities, communities, graph));
        return result;
    }

    private boolean isRealUser(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName());
    }

    private boolean isGraphVisibleStatus(WikiPage page, boolean authenticated) {
        if (page == null) return false;
        Set<String> allowedStatuses = authenticated ? AUTHENTICATED_GRAPH_STATUSES : PUBLIC_GRAPH_STATUSES;
        return allowedStatuses.contains(page.getStatus());
    }

    private void addEdgeWeight(Graph<Long, DefaultWeightedEdge> graph, Map<String, Double> weights,
                               long from, long to, double delta) {
        if (from == to) return;
        String key = edgeKey(from, to);
        weights.merge(key, delta, Double::sum);
        if (!graph.containsEdge(from, to)) {
            try {
                graph.addEdge(from, to);
            } catch (Exception e) {
                // edge already exists, ignore
            }
        }
    }

    private String edgeKey(long a, long b) {
        return Math.min(a, b) + ":" + Math.max(a, b);
    }

    private String resolveLinkType(long from, long to, List<WikiLink> links) {
        for (WikiLink l : links) {
            if ((l.getFromPageId().equals(from) && l.getToPageId().equals(to)) ||
                (l.getFromPageId().equals(to) && l.getToPageId().equals(from))) {
                return l.getLinkType();
            }
        }
        return "RELATED";
    }

    private StableCommunities buildSoftwareCommunities(List<WikiPage> pages) {
        Set<String> sortedKeys = new TreeSet<>();
        Map<Long, String> keyByPageId = new HashMap<>();
        for (WikiPage page : pages) {
            String key = communityKey(page);
            keyByPageId.put(page.getId(), key);
            sortedKeys.add(key);
        }

        Map<String, Integer> idByKey = new LinkedHashMap<>();
        Map<Integer, String> communityKeys = new LinkedHashMap<>();
        Map<Integer, String> communityNames = new LinkedHashMap<>();
        int id = 0;
        for (String key : sortedKeys) {
            idByKey.put(key, id);
            communityKeys.put(id, key);
            communityNames.put(id, communityName(key));
            id++;
        }

        Map<Long, Integer> nodeCommunities = new HashMap<>();
        for (Map.Entry<Long, String> entry : keyByPageId.entrySet()) {
            nodeCommunities.put(entry.getKey(), idByKey.get(entry.getValue()));
        }
        return new StableCommunities(nodeCommunities, communityKeys, communityNames);
    }

    private String communityKey(WikiPage page) {
        return normalizeCommunityPart(page.getCategory(), "未分类")
                + "::" + normalizeCommunityPart(page.getSoftware(), "通用");
    }

    private String communityName(String communityKey) {
        String[] parts = communityKey.split("::", 2);
        String category = parts.length > 0 ? parts[0] : "未分类";
        String software = parts.length > 1 ? parts[1] : "通用";
        return software + " (" + category + ")";
    }

    private String normalizeCommunityPart(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private List<Map<String, Object>> buildCommunityStats(StableCommunities stableCommunities,
                                                          Map<Long, Integer> communities,
                                                          Graph<Long, DefaultWeightedEdge> graph) {
        Map<Integer, Integer> nodeCounts = new HashMap<>();
        Map<Integer, Integer> edgeCounts = new HashMap<>();
        for (Integer communityId : stableCommunities.communityNames().keySet()) {
            nodeCounts.put(communityId, 0);
            edgeCounts.put(communityId, 0);
        }
        for (Integer communityId : communities.values()) {
            nodeCounts.merge(communityId, 1, Integer::sum);
        }
        for (DefaultWeightedEdge edge : graph.edgeSet()) {
            Integer sourceCommunity = communities.get(graph.getEdgeSource(edge));
            Integer targetCommunity = communities.get(graph.getEdgeTarget(edge));
            if (sourceCommunity != null && sourceCommunity.equals(targetCommunity)) {
                edgeCounts.merge(sourceCommunity, 1, Integer::sum);
            }
        }

        List<Map<String, Object>> stats = new ArrayList<>();
        for (Integer communityId : stableCommunities.communityNames().keySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", communityId);
            item.put("key", stableCommunities.communityKeys().get(communityId));
            item.put("name", stableCommunities.communityNames().get(communityId));
            item.put("nodeCount", nodeCounts.getOrDefault(communityId, 0));
            item.put("edgeCount", edgeCounts.getOrDefault(communityId, 0));
            stats.add(item);
        }
        return stats;
    }

    private record StableCommunities(Map<Long, Integer> nodeCommunities,
                                     Map<Integer, String> communityKeys,
                                     Map<Integer, String> communityNames) {}
}
