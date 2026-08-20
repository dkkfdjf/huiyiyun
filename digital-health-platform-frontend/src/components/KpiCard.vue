<template>
  <div class="kpi" :class="tone">
    <div class="kpi__k">{{ label }}</div>
    <div class="kpi__v">
      <template v-if="prefix">{{ prefix }}</template><span v-count="value">0</span><span v-if="unit" class="u">{{ unit }}</span>
    </div>
    <div v-if="delta" class="kpi__d" :class="deltaTone">{{ deltaArrow }} {{ delta }}</div>
    <svg v-if="spark && spark.length" class="kpi__spark" viewBox="0 0 60 24" preserveAspectRatio="none">
      <polyline :points="pts" fill="none" :stroke="sparkColor" stroke-width="2" />
    </svg>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  label: String,
  value: { type: Number, default: 0 },
  unit: String,
  prefix: String,
  tone: { type: String, default: '' }, // '' | in | out | net | alert
  delta: String,
  deltaTone: { type: String, default: 'flat' }, // up | down | flat
  spark: { type: Array, default: () => [] }
})

const deltaArrow = computed(() => ({ up: '▲', down: '▼', flat: '·' }[props.deltaTone] || '·'))
const sparkColor = computed(() => ({ in: '#4338CA', out: '#D97706', net: '#6366F1', alert: '#DC2A45' }[props.tone] || '#4338CA'))
const pts = computed(() => {
  const a = props.spark
  if (!a || a.length < 2) return ''
  const mn = Math.min(...a)
  const mx = Math.max(...a)
  const d = mx - mn || 1
  return a
    .map((v, i) => `${((i / (a.length - 1)) * 60).toFixed(1)},${(24 - ((v - mn) / d) * 18 - 3).toFixed(1)}`)
    .join(' ')
})
</script>

<style scoped>
.kpi {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--rad);
  padding: 16px 17px;
  box-shadow: var(--sh-sm);
  position: relative;
  overflow: hidden;
}
.kpi__k { font-family: var(--font-m); font-size: 11px; color: var(--ink-3); letter-spacing: 0.04em; }
.kpi__v { font-family: var(--font-d); font-weight: 800; font-size: 27px; letter-spacing: -0.03em; margin-top: 7px; line-height: 1; }
.kpi__v .u { font-family: var(--font-m); font-size: 12px; color: var(--ink-3); font-weight: 500; margin-left: 3px; }
.kpi.in .kpi__v { color: var(--indigo); }
.kpi.out .kpi__v { color: var(--amber); }
.kpi.net .kpi__v { color: var(--indigo); }
.kpi.alert .kpi__v { color: var(--red); }
.kpi__d { display: inline-flex; align-items: center; gap: 4px; font-family: var(--font-m); font-size: 11px; margin-top: 8px; }
.kpi__d.up { color: var(--green); }
.kpi__d.down { color: var(--red); }
.kpi__d.flat { color: var(--ink-3); }
.kpi__spark { position: absolute; right: 14px; bottom: 14px; width: 60px; height: 24px; opacity: 0.85; }
</style>
