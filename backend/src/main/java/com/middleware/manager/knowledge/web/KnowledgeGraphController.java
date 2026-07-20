package com.middleware.manager.knowledge.web;

import com.middleware.manager.knowledge.entity.KnowledgeChunk;
import com.middleware.manager.knowledge.repository.KnowledgeChunkMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeGraphController {

    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    // 中间件/技术关键词词典
    private static final Set<String> KEYWORDS = Set.of(
        "redis", "mysql", "nginx", "tomcat", "kafka", "rabbitmq", "elasticsearch", "es",
        "mongodb", "postgresql", "oracle", "docker", "kubernetes", "k8s", "linux",
        "jvm", "gc", "oom", "cpu", "内存", "磁盘", "网络", "连接", "超时", "慢查询",
        "死锁", "主从", "集群", "哨兵", "分片", "备份", "恢复", "监控", "告警",
        "中间件", "数据库", "主机", "安全", "负载均衡", "缓存", "会话", "线程",
        "连接池", "日志", "配置", "部署", "升级", "迁移", "故障", "排查",
        "性能", "高可用", "容灾", "压力测试", "瓶颈", "优化", "索引", "锁",
        "事务", "主键", "外键", "表空间", "binlog", "redo", "undo", "wal",
        "keepalived", "haproxy", "lvs", "iptables", "firewalld", "selinux",
        "prometheus", "grafana", "zabbix", "ansible", "jenkins", "gitlab"
    );

    @GetMapping("/graph")
    public ResponseEntity<?> getKnowledgeGraph() {
        List<KnowledgeChunk> chunks = chunkMapper.findAll();

        // 1. 统计关键词频率
        Map<String, Integer> keywordCount = new HashMap<>();
        // 2. 记录关键词共现关系
        Map<String, Map<String, Integer>> coOccurrence = new HashMap<>();

        for (KnowledgeChunk chunk : chunks) {
            String content = chunk.getContent().toLowerCase();
            String source = chunk.getSourceTitle() != null ? chunk.getSourceTitle() : "未知";

            // 提取当前 chunk 中出现的关键词
            Set<String> found = new TreeSet<>();
            for (String kw : KEYWORDS) {
                if (content.contains(kw)) {
                    found.add(kw);
                    keywordCount.merge(kw, 1, Integer::sum);
                }
            }

            // 来源文档也作为一个节点
            found.add(source);
            keywordCount.merge(source, 1, Integer::sum);

            // 共现：同一 chunk 中出现的关键词两两连接
            List<String> foundList = new ArrayList<>(found);
            for (int i = 0; i < foundList.size(); i++) {
                for (int j = i + 1; j < foundList.size(); j++) {
                    String a = foundList.get(i);
                    String b = foundList.get(j);
                    coOccurrence.computeIfAbsent(a, k -> new HashMap<>())
                            .merge(b, 1, Integer::sum);
                    coOccurrence.computeIfAbsent(b, k -> new HashMap<>())
                            .merge(a, 1, Integer::sum);
                }
            }
        }

        // 3. 构建节点列表
        List<Map<String, Object>> nodes = new ArrayList<>();
        int id = 0;
        Map<String, Integer> nodeIdMap = new HashMap<>();
        for (Map.Entry<String, Integer> entry : keywordCount.entrySet()) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", id);
            node.put("name", entry.getKey());
            node.put("val", entry.getValue());  // 节点大小 = 出现次数
            // 分类：技术关键词 vs 文档来源
            node.put("group", KEYWORDS.contains(entry.getKey()) ? "keyword" : "document");
            nodes.add(node);
            nodeIdMap.put(entry.getKey(), id);
            id++;
        }

        // 4. 构建边列表
        List<Map<String, Object>> links = new ArrayList<>();
        Set<String> addedEdges = new HashSet<>();
        for (Map.Entry<String, Map<String, Integer>> entry : coOccurrence.entrySet()) {
            String from = entry.getKey();
            for (Map.Entry<String, Integer> target : entry.getValue().entrySet()) {
                String to = target.getKey();
                String edgeKey = from.compareTo(to) < 0 ? from + "|" + to : to + "|" + from;
                if (addedEdges.contains(edgeKey)) continue;
                addedEdges.add(edgeKey);

                Map<String, Object> link = new HashMap<>();
                link.put("source", nodeIdMap.get(from));
                link.put("target", nodeIdMap.get(to));
                link.put("value", target.getValue());  // 共现次数
                links.add(link);
            }
        }

        // 5. 返回
        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodes);
        result.put("links", links);
        return ResponseEntity.ok(result);
    }
}
