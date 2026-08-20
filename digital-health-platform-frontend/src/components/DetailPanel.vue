<template>
  <div class="dp">
    <!-- 标题带:大标题 + 右侧标签/公司 -->
    <div class="dp__head">
      <h3 class="dp__title">{{ title }}</h3>
      <div v-if="$slots.tags" class="dp__tags"><slot name="tags" /></div>
    </div>

    <!-- 元信息:label/value 卡片网格,告别"一堆文本堆叠" -->
    <dl v-if="meta?.length" class="dp__meta">
      <div v-for="m in meta" :key="m.label" class="dp__cell">
        <dt>{{ m.label }}</dt>
        <dd :class="{ muted: isEmpty(m.value) }">{{ isEmpty(m.value) ? '—' : m.value }}</dd>
      </div>
    </dl>

    <!-- 正文 / 说明:prose 卡片,可滚 -->
    <div v-if="$slots.default" class="dp__body">
      <div v-if="bodyLabel" class="dp__body-label">{{ bodyLabel }}</div>
      <div class="dp__prose"><slot /></div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  // [{ label, value }] — 顺序即展示顺序
  meta: { type: Array, default: () => [] },
  bodyLabel: { type: String, default: '' }
})
const isEmpty = (v) => v == null || v === ''
</script>

<style scoped>
.dp { display: flex; flex-direction: column; gap: 18px; }

.dp__head {
  display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap;
  padding-bottom: 14px; border-bottom: 1px solid var(--line);
}
.dp__title {
  font-family: var(--font-d); font-size: 19px; font-weight: 700;
  color: var(--ink); letter-spacing: -0.01em; line-height: 1.3; margin: 0;
}
.dp__tags { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }

.dp__meta { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; margin: 0; }
.dp__cell {
  background: var(--field); border: 1px solid var(--line); border-radius: var(--rad-sm);
  padding: 12px 14px; transition: border-color 0.18s;
}
.dp__cell:hover { border-color: var(--indigo-hi, #6366f1); }
.dp__cell dt { font-size: 12px; color: var(--ink-3); margin-bottom: 6px; }
.dp__cell dd { font-size: 14px; font-weight: 600; color: var(--ink); margin: 0; line-height: 1.4; word-break: break-word; }
.dp__cell dd.muted { color: var(--ink-4); font-weight: 500; }

.dp__body { display: flex; flex-direction: column; gap: 8px; }
.dp__body-label { font-size: 12px; color: var(--ink-3); }
.dp__prose {
  background: var(--field); border: 1px solid var(--line); border-radius: var(--rad-sm);
  padding: 16px 18px; font-size: 14.5px; line-height: 1.75; color: var(--ink-2);
  white-space: pre-wrap; word-break: break-word; max-height: 46vh; overflow: auto;
}
</style>
