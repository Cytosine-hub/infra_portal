// 标准发布列表的纯展示逻辑：标题、元信息、搜索、排序与分类聚合
// 页面与分区/总览子组件共用，避免同一套规则出现多份实现

export const UNCATEGORIZED = '未分类'
// 列表卡片默认只展示前若干份标准文档，其余通过「查看更多」展开，避免大量数据时页面被拉长
export const DOC_PREVIEW_LIMIT = 3
const SOFTWARE_SUMMARY_LIMIT = 5

export function displayTitle(item) {
  if (!item) return ''
  return item.title || [item.category, item.software, item.softwareVersion].filter(Boolean).join(' / ') || '未命名'
}

export function formatDate(value) {
  if (!value) return '-'
  const matched = String(value).match(/^\d{4}-\d{2}-\d{2}/)
  return matched ? matched[0] : String(value)
}

export function publishedDocuments(standard) {
  return (standard?.relatedDocuments || []).filter((doc) => !doc.status || doc.status === 'PUBLISHED')
}

export function standardTimestamp(standard) {
  return standard?.publishedAt || standard?.updatedAt || standard?.createdAt || ''
}

export function standardMetaFields(standard) {
  return [
    { label: '类别', value: standard.category || UNCATEGORIZED },
    { label: '软件', value: standard.software || '-' },
    { label: '软件版本', value: standard.softwareVersion || '-' },
    { label: '标准版本', value: standard.version || '-' },
    { label: '发布时间', value: formatDate(standardTimestamp(standard)) }
  ]
}

// 关联标准文档同样要展示版本与发布时间，缺失时回退到 standardVersion / updatedAt
export function documentMetaFields(doc) {
  return [
    { label: '版本', value: doc?.version || doc?.standardVersion || '-' },
    { label: '发布', value: formatDate(doc?.publishedAt || doc?.updatedAt || doc?.createdAt) }
  ]
}

function searchText(standard) {
  return [
    displayTitle(standard),
    standard.category,
    standard.software,
    standard.softwareVersion,
    standard.version,
    ...publishedDocuments(standard).map((doc) => doc.title)
  ].filter(Boolean).join(' ').toLowerCase()
}

function compareStandards(left, right, sortBy) {
  if (sortBy === 'name') return displayTitle(left).localeCompare(displayTitle(right), 'zh-Hans-CN')
  if (sortBy === 'documents') {
    const gap = publishedDocuments(right).length - publishedDocuments(left).length
    if (gap !== 0) return gap
  }
  return standardTimestamp(right).localeCompare(standardTimestamp(left))
}

export function searchAndSortStandards(standards, keyword, sortBy) {
  const query = (keyword || '').toLowerCase()
  const matched = query
    ? standards.filter((standard) => searchText(standard).includes(query))
    : [...standards]
  return matched.sort((left, right) => compareStandards(left, right, sortBy))
}

export function buildCategoryGroups(standards) {
  const groups = new Map()
  for (const standard of standards) {
    const category = standard.category || UNCATEGORIZED
    if (!groups.has(category)) groups.set(category, { category, standards: [], documentCount: 0, softwareSummary: '' })
    const group = groups.get(category)
    group.standards.push(standard)
    group.documentCount += publishedDocuments(standard).length
  }
  for (const group of groups.values()) {
    const names = [...new Set(group.standards.map((standard) => standard.software).filter(Boolean))]
    group.softwareSummary = names.slice(0, SOFTWARE_SUMMARY_LIMIT).join('、') + (names.length > SOFTWARE_SUMMARY_LIMIT ? ' 等' : '')
  }
  return [...groups.values()]
}

export function buildSummaryMetrics(standards) {
  const documentCount = standards.reduce((total, standard) => total + publishedDocuments(standard).length, 0)
  const categories = new Set(standards.map((standard) => standard.category || UNCATEGORIZED))
  const latest = standards.map(standardTimestamp).filter(Boolean).sort().at(-1)
  return [
    { label: '标准总数', value: standards.length },
    { label: '标准文档', value: documentCount },
    { label: '标准类别', value: categories.size },
    { label: '最近更新', value: formatDate(latest) }
  ]
}
