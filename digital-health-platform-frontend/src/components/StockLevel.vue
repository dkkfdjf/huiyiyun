<template>
  <div class="sl">
    <!-- 药丸形水位条:满=绿,空=红(类比库存量可视化) -->
    <div class="sl__bar">
      <i class="sl__fill" :class="state.key" :style="{ width: state.pct + '%' }"></i>
    </div>
    <div class="sl__meta">
      <b class="sl__qty" :class="state.key">{{ fmt(qty) }}</b>
      <span class="sl__thr">阈值 {{ fmt(threshold) }}</span>
      <span class="sl__tag" :class="state.key">{{ state.label }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  qty: { type: Number, default: 0 },
  threshold: { type: Number, default: 0 }
})

const fmt = (n) => (n == null ? '—' : Number(n).toLocaleString())

const state = computed(() => {
  const q = Number(props.qty) || 0
  const t = Number(props.threshold) || 0
  if (!t) {
    // 未设预警(0=不预警):中性条,不参与红绿判定
    const ref = Math.max(q, 1)
    return { key: 'idle', label: '未设预警', pct: Math.min(100, (q / ref) * 100) }
  }
  // 参考刻度=阈值的两倍,使"阈值"落在条形中段可见位置
  const ref = Math.max(t * 2, q, 1)
  const pct = Math.min(100, (q / ref) * 100)
  if (q <= t) return { key: 'danger', label: '预警', pct }
  if (q <= t * 1.5) return { key: 'warn', label: '偏低', pct }
  return { key: 'safe', label: '充足', pct }
})
</script>

<style scoped>
.sl { display: flex; flex-direction: column; gap: 6px; min-width: 150px; }

.sl__bar { height: 8px; border-radius: 999px; background: var(--line-2); overflow: hidden; }
.sl__fill { display: block; height: 100%; border-radius: 999px; transition: width 0.5s cubic-bezier(0.2, 0.7, 0.2, 1); }
.sl__fill.safe { background: linear-gradient(90deg, #34d399, var(--green)); }
.sl__fill.warn { background: linear-gradient(90deg, #fbbf24, var(--amber)); }
.sl__fill.danger { background: linear-gradient(90deg, #fb7185, var(--red)); }
.sl__fill.idle { background: linear-gradient(90deg, var(--indigo-hi), var(--indigo)); opacity: 0.55; }

.sl__meta { display: flex; align-items: center; gap: 8px; font-size: 12.5px; }
.sl__qty { font-family: var(--font-m); font-weight: 700; color: var(--ink); }
.sl__qty.safe { color: var(--green); }
.sl__qty.warn { color: var(--amber); }
.sl__qty.danger { color: var(--red); }
.sl__qty.idle { color: var(--ink-2); }
.sl__thr { color: var(--ink-3); font-size: 11.5px; }
.sl__tag { margin-left: auto; font-family: var(--font-m); font-size: 10.5px; font-weight: 600; padding: 2px 8px; border-radius: 6px; }
.sl__tag.safe { background: rgba(14, 156, 143, 0.12); color: var(--green); }
.sl__tag.warn { background: var(--amber-soft); color: var(--amber); }
.sl__tag.danger { background: rgba(220, 42, 69, 0.1); color: var(--red); }
.sl__tag.idle { background: var(--line-2); color: var(--ink-3); }
</style>
