<template>
  <div class="card">
    <div class="card__head">
      <h3>{{ title }}</h3>
      <span v-if="more" class="more">{{ more }}</span>
    </div>
    <div class="chart">
      <div v-if="legend.length" class="chart__top">
        <div class="chart__legend">
          <span v-for="(l, i) in legend" :key="i"><i :style="{ background: l.color }"></i>{{ l.label }}</span>
        </div>
      </div>
      <div ref="el" class="chart__canvas" :style="{ height: (height || 250) + 'px' }"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  title: String,
  more: String,
  legend: { type: Array, default: () => [] }, // [{label,color}]
  option: { type: Object, default: () => ({}) },
  height: { type: Number, default: 250 }
})

const el = ref(null)
let chart = null

function resize() {
  chart && chart.resize()
}
onMounted(async () => {
  await nextTick()
  chart = echarts.init(el.value)
  chart.setOption(props.option || {})
  window.addEventListener('resize', resize)
})
watch(
  () => props.option,
  (o) => {
    if (chart && o) chart.setOption(o, true)
  },
  { deep: true }
)
onUnmounted(() => {
  window.removeEventListener('resize', resize)
  chart && chart.dispose()
})
</script>

<style scoped>
.card { background: var(--surface); border: 1px solid var(--line); border-radius: var(--rad); box-shadow: var(--sh-sm); }
.card__head {
  display: flex; align-items: center; justify-content: space-between; gap: 10px;
  padding: 15px 20px; border-bottom: 1px solid var(--line-2);
}
.card__head h3 { font-family: var(--font-d); font-weight: 700; font-size: 15px; }
.card__head .more { font-family: var(--font-m); font-size: 11px; color: var(--ink-3); cursor: pointer; }
.card__head .more:hover { color: var(--indigo); }
.chart { padding: 6px 14px 14px; }
.chart__top { display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: 10px; padding: 12px 8px 8px; }
.chart__legend { display: flex; gap: 18px; font-size: 12px; color: var(--ink-2); }
.chart__legend span { display: inline-flex; align-items: center; gap: 7px; }
.chart__legend i { width: 10px; height: 10px; border-radius: 3px; display: inline-block; }
.chart__canvas { width: 100%; }
</style>
