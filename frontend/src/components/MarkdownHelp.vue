<template>
  <div v-if="show" class="modal-backdrop" @click.self="$emit('close')">
    <div class="help-panel">
      <div class="help-header">
        <h3>Markdown 语法参考</h3>
        <button class="ghost" @click="$emit('close')">关闭</button>
      </div>
      <div class="help-body">
        <h4>快捷键</h4>
        <table>
          <thead><tr><th>操作</th><th>说明</th></tr></thead>
          <tbody>
            <tr><td><code>Tab</code></td><td>插入 4 个空格缩进</td></tr>
            <tr><td><code>Enter</code></td><td>列表中按回车自动续行（空行回车退出列表）</td></tr>
            <tr><td><code>[行数][列数] + 空格/回车</code></td><td>快速生成表格，例如输入 <code>[3][4]</code> 后按空格，生成 3 行 4 列表格</td></tr>
            <tr><td>粘贴图片</td><td>直接粘贴剪贴板中的图片，自动上传并插入</td></tr>
          </tbody>
        </table>

        <h4>文字样式</h4>
        <table>
          <thead><tr><th>效果</th><th>语法</th></tr></thead>
          <tbody>
            <tr><td><strong>粗体</strong></td><td><code>**文字**</code></td></tr>
            <tr><td><em>斜体</em></td><td><code>*文字*</code></td></tr>
            <tr><td><del>删除线</del></td><td><code>~~文字~~</code></td></tr>
            <tr><td><code>行内代码</code></td><td><code>`代码`</code></td></tr>
            <tr><td><a href="#">链接</a></td><td><code>[文字](网址)</code></td></tr>
            <tr><td>图片</td><td><code>![alt](图片地址)</code></td></tr>
          </tbody>
        </table>

        <h4>标题</h4>
        <pre><code># 一级标题
## 二级标题
### 三级标题</code></pre>

        <h4>列表</h4>
        <pre><code>- 无序项目
- 无序项目
  - 缩进子项

1. 有序项目
2. 有序项目</code></pre>
        <p class="help-note">提示：在列表中按 <code>Enter</code> 会自动续写下一列表项，连续按两次 <code>Enter</code> 退出列表。</p>

        <h4>表格（快捷方式）</h4>
        <pre><code>[3][4] + 空格/回车</code></pre>
        <p class="help-note">输入 <code>[行数][列数]</code> 后按空格或回车，自动生成表格。行数和列数范围 1-10。</p>
        <h4>表格（手动编写）</h4>
        <pre><code>| 列A | 列B | 列C |
|-----|-----|-----|
| A1  | B1  | C1  |
| A2  | B2  | C2  |</code></pre>

        <h4>代码块</h4>
        <pre><code>```java
public static void main(String[] args) {
    System.out.println("Hello");
}
```</code></pre>

        <h4>引用</h4>
        <pre><code>> 这是一段引用文字
> 可以多行</code></pre>

        <h4>分隔线</h4>
        <pre><code>---</code></pre>

        <h4>占位符变量</h4>
        <pre><code>{{参数编码}}</code></pre>
        <p class="help-note">占位符会自动替换为对应标准参数的值，仅文档编辑支持。</p>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({ show: Boolean })
defineEmits(['close'])
</script>

<style scoped>
.help-panel {
  background: var(--color-bg); border-radius: var(--radius-lg); width: min(700px, 92vw); max-height: 85vh; overflow-y: auto; padding: 24px;
  box-shadow: 0 18px 48px rgba(23,35,59,0.28);
}
.help-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.help-header h3 { margin: 0; font-size: var(--text-lg); letter-spacing: 0; }
.help-body h4 { margin: 18px 0 6px; font-size: var(--text-sm); color: var(--color-primary); }
.help-body table { width: 100%; border-collapse: collapse; margin-bottom: 12px; }
.help-body th, .help-body td { padding: 8px 12px; border-bottom: 1px solid var(--color-border); text-align: left; font-size: var(--text-sm); }
.help-body th { background: var(--color-bg-secondary); color: var(--color-text-secondary); text-transform: none; }
.help-body code { background: var(--color-bg-secondary); padding: 1px 6px; border-radius: 3px; font-size: var(--text-xs); }
.help-body pre { background: var(--color-bg-tertiary); color: var(--color-text); padding: 14px; border-radius: var(--radius-md); overflow-x: auto; font-size: var(--text-xs); line-height: 1.6; margin: 6px 0; }
.help-body pre code { background: none; padding: 0; color: inherit; }
.help-note { color: var(--color-text-tertiary); font-size: var(--text-xs); margin: 8px 0 0; }
</style>
