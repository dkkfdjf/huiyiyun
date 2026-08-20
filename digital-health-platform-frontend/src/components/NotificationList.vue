<template>
  <div class="nlist">
    <div
      v-for="n in items"
      :key="n.id"
      class="nlist__it"
      :class="{ unread: !n.isRead }"
      @click="$emit('item-click', n)"
    >
      <span class="nlist__cat" :class="catClass(n.category)">{{ catLabel(n.category) }}</span>
      <div class="nlist__main">
        <div class="nlist__t">{{ n.title }}</div>
        <div v-if="n.body" class="nlist__b">{{ n.body }}</div>
        <div class="nlist__time">{{ fmt(n.createTime) }}</div>
      </div>
      <span v-if="!n.isRead" class="nlist__dot" aria-hidden="true"></span>
    </div>
    <div v-if="!items.length" class="nlist__empty">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M6 9a6 6 0 0 1 12 0c0 5 2 6 2 6H4s2-1 2-6z" /><path d="M10 20a2 2 0 0 0 4 0" /></svg>
      <span>暂无通知</span>
    </div>
  </div>
</template>

<script setup>
defineProps({ items: { type: Array, default: () => [] } })
defineEmits(['item-click'])
function catLabel(c) { return ({ 0: '库存', 1: '反馈', 2: '药企', 3: '系统' })[c] || '通知' }
function catClass(c) { return ({ 0: 'c-stock', 1: 'c-demand', 2: 'c-company', 3: 'c-system' })[c] || 'c-system' }
function fmt(t) { const s = String(t || '').replace('T', ' '); return s.length >= 16 ? s.slice(5, 16) : s }
</script>

<style scoped>
.nlist__it {
  display: grid;
  grid-template-columns: 44px 1fr 8px;
  gap: 11px;
  padding: 13px 14px;
  border-bottom: 1px solid var(--line-2);
  cursor: pointer;
  transition: background 0.15s;
  position: relative;
}
.nlist__it:hover { background: rgba(99, 102, 241, 0.05); }
.nlist__it.unread { background: rgba(99, 102, 241, 0.075); }
.nlist__cat { align-self: start; font-size: 11px; font-weight: 600; text-align: center; padding: 3px 0; border-radius: 7px; color: #fff; }
.c-stock { background: #d97706; }
.c-demand { background: #4338ca; }
.c-company { background: #0d9488; }
.c-system { background: #8b93a6; }
.nlist__main { min-width: 0; }
.nlist__t { font-size: 13.5px; font-weight: 600; color: var(--ink); line-height: 1.3; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.nlist__b { font-size: 12px; color: var(--ink-2); margin-top: 3px; line-height: 1.45; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.nlist__time { font-size: 11px; color: var(--ink-3); margin-top: 5px; font-family: var(--font-m); }
.nlist__dot { width: 8px; height: 8px; border-radius: 50%; background: var(--indigo-hi, #6366f1); align-self: center; box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.18); }
.nlist__empty { padding: 48px 0; text-align: center; color: var(--ink-3); font-size: 13px; display: flex; flex-direction: column; align-items: center; gap: 10px; }
.nlist__empty svg { width: 34px; height: 34px; opacity: 0.4; }
</style>
