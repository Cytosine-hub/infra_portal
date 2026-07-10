<template>
  <section class="workspace migration-page">
    <div class="migration-hero">
      <div>
        <p class="eyebrow">Data Migration</p>
        <h2>数据迁移架构设计</h2>
        <p>面向常用数据库迁移场景，先建立可插拔执行框架，再逐步接入 MySQL、Oracle、OceanBase、OSS 等能力。</p>
      </div>
      <div class="migration-boundary">
        <strong>安全边界</strong>
        <span>只提供迁移功能，不保存源端/目标端地址、账号、密码或临时访问密钥。</span>
      </div>
    </div>

    <div class="migration-grid">
      <article v-for="item in architecture" :key="item.title" class="migration-card">
        <span>{{ item.step }}</span>
        <h3>{{ item.title }}</h3>
        <p>{{ item.description }}</p>
      </article>
    </div>

    <section class="migration-section">
      <div class="section-heading">
        <div>
          <p class="eyebrow">Pattern</p>
          <h3>核心设计模式</h3>
        </div>
      </div>
      <div class="pattern-list">
        <div v-for="pattern in patterns" :key="pattern.name">
          <strong>{{ pattern.name }}</strong>
          <span>{{ pattern.description }}</span>
        </div>
      </div>
    </section>

    <section class="migration-section">
      <div class="section-heading">
        <div>
          <p class="eyebrow">Roadmap</p>
          <h3>分阶段实现</h3>
        </div>
      </div>
      <ol class="migration-roadmap">
        <li v-for="phase in roadmap" :key="phase">{{ phase }}</li>
      </ol>
    </section>
  </section>
</template>

<script setup>
const architecture = [
  { step: '01', title: '连接适配层', description: '每类数据库提供独立 Connector，负责驱动加载、连接校验、元数据读取和能力声明。' },
  { step: '02', title: '迁移计划层', description: '将表、字段、索引、约束、数据范围和并发策略转换为 MigrationPlan，不绑定具体数据库实现。' },
  { step: '03', title: '执行管道层', description: '通过 Reader、Transformer、Writer 组合执行结构迁移、数据搬迁、校验和补偿。' },
  { step: '04', title: '插件注册中心', description: '按数据库类型发现插件，新增数据库只增加插件实现，不改主流程。' }
]

const patterns = [
  { name: 'Strategy', description: '不同数据库的读写、DDL 转换、分页抽取采用策略实现。' },
  { name: 'Factory', description: '按数据库类型创建 Connector、Dialect、Reader、Writer。' },
  { name: 'Template Method', description: '迁移执行流程固定，具体连接、抽取、写入、校验步骤由插件覆盖。' },
  { name: 'Adapter', description: '屏蔽 MySQL、Oracle、OceanBase、OSS SDK/API 差异。' },
  { name: 'Chain of Responsibility', description: '迁移前检查、字段映射、类型转换、脱敏等处理器按链路组合。' }
]

const roadmap = [
  '第一阶段：完成插件接口、参数模型、能力声明和 dry-run 校验。',
  '第二阶段：接入 MySQL 到 MySQL 的表结构与批量数据迁移。',
  '第三阶段：扩展 Oracle、OceanBase 方言和跨库类型映射。',
  '第四阶段：接入 OSS 文件中转、断点续传、校验报告和任务导出。'
]
</script>

<style scoped>
.migration-page {
  --migration-side-width: 360px;
  --migration-copy-width: 720px;
  --migration-card-columns: 4;
  --migration-pattern-columns: 5;
  display: grid;
  gap: var(--space-lg);
  padding-top: var(--space-lg);
}
.migration-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(var(--card-height), var(--migration-side-width));
  gap: var(--space-lg);
  align-items: stretch;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-xl);
  background: var(--color-bg);
}
.migration-hero h2 {
  margin: 0;
  font-size: var(--text-3xl);
}
.migration-hero p {
  max-width: var(--migration-copy-width);
  margin: var(--space-md) 0 0;
  color: var(--color-text-secondary);
  line-height: var(--leading-relaxed);
}
.migration-boundary {
  display: grid;
  align-content: center;
  gap: var(--space-sm);
  border: 1px solid var(--color-primary-100);
  border-radius: var(--radius-md);
  padding: var(--space-lg);
  color: var(--color-primary-900);
  background: var(--color-primary-light);
}
.migration-boundary span {
  color: var(--color-primary-800);
  line-height: var(--leading-relaxed);
}
.migration-grid {
  display: grid;
  grid-template-columns: repeat(var(--migration-card-columns), 1fr);
  gap: var(--space-md);
}
.migration-card {
  display: grid;
  gap: var(--space-sm);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
  background: var(--color-bg);
}
.migration-card span {
  color: var(--color-primary);
  font-size: var(--text-sm);
  font-weight: 700;
}
.migration-card h3 {
  margin: 0;
  font-size: var(--text-lg);
}
.migration-card p {
  margin: 0;
  color: var(--color-text-secondary);
  line-height: var(--leading-relaxed);
}
.migration-section {
  display: grid;
  gap: var(--space-md);
}
.pattern-list {
  display: grid;
  grid-template-columns: repeat(var(--migration-pattern-columns), 1fr);
  gap: var(--space-md);
}
.pattern-list div {
  display: grid;
  gap: var(--space-sm);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
  background: var(--color-bg);
}
.pattern-list span,
.migration-roadmap {
  color: var(--color-text-secondary);
  line-height: var(--leading-relaxed);
}
.migration-roadmap {
  margin: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-lg) var(--space-xl);
  background: var(--color-bg);
}

@media (max-width: 980px) {
  .migration-hero,
  .migration-grid,
  .pattern-list {
    grid-template-columns: 1fr;
  }
}
</style>
