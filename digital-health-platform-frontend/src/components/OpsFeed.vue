<template>
  <div class="card">
    <div class="card__head">
      <h3>{{ title }}</h3>
      <span v-if="more" class="more" @click="emit('more')">{{ more }}</span>
    </div>
    <div class="card__body feed">
      <div v-for="(r, i) in rows" :key="i" class="feed__row">
        <span class="feed__t">{{ r.time }}</span>
        <span class="feed__e">{{ r.event }}</span>
        <span class="feed__d" :class="r.type === 'out' ? 'out' : 'in'">{{ r.delta }}</span>
      </div>
      <div v-if="!rows.length" class="empty">暂无流水</div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '运营流水' },
  more: { type: String, default: '最近' },
  rows: { type: Array, default: () => [] } // {time,event,delta,type:'in'|'out'}
})
const emit = defineEmits(['more'])
</script>

<style scoped>
.card { background: var(--surface); border: 1px solid var(--line); border-radius: var(--rad); box-shadow: var(--sh-sm); }
.card__head { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 15px 20px; border-bottom: 1px solid var(--line-2); }
.card__head h3 { font-family: var(--font-d); font-weight: 700; font-size: 15px; }
.card__head .more { font-family: var(--font-m); font-size: 11px; color: var(--ink-3); cursor: pointer; }
.feed { padding: 8px 18px 14px; max-height: 320px; overflow-y: auto; }
.feed__row { display: grid; grid-template-columns: 42px 1fr auto; gap: 9px; padding: 9px 0; border-bottom: 1px solid var(--line-2); align-items: baseline; font-family: var(--font-m); font-size: 12px; }
.feed__row:last-child { border-bottom: none; }
.feed__t { color: var(--ink-3); }
.feed__e { color: var(--ink); min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.feed__d { font-weight: 600; text-align: right; }
.feed__d.in { color: var(--indigo); }
.feed__d.out { color: var(--amber); }
.empty { padding: 24px 0; text-align: center; color: var(--ink-3); font-size: 13px; }
</style>
