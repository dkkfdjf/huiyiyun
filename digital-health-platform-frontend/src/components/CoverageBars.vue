<template>
  <div class="card">
    <div class="card__head">
      <h3>{{ title }}</h3>
      <span v-if="more" class="more">{{ more }}</span>
    </div>
    <div class="card__body">
      <div v-for="(r, i) in rows" :key="i" class="cov__row">
        <span>{{ r.name }}</span>
        <div class="cov__bar"><i :style="{ width: pctOf(r) + '%' }"></i></div>
        <span class="v">{{ r.value }}</span>
      </div>
      <div v-if="foot && foot.length" class="cov__foot">
        <div v-for="(f, i) in foot" :key="i"><b>{{ f.b }}</b>{{ f.label }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  title: { type: String, default: '网点分布' },
  more: { type: String, default: '' },
  rows: { type: Array, default: () => [] }, // {name, value}
  foot: { type: Array, default: () => [] } // [{b, label}]
})

const max = computed(() => Math.max(1, ...props.rows.map((r) => Number(r.value) || 0)))
const pctOf = (r) => Math.round(((Number(r.value) || 0) / max.value) * 100)
</script>

<style scoped>
.card { background: var(--surface); border: 1px solid var(--line); border-radius: var(--rad); box-shadow: var(--sh-sm); }
.card__head { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 15px 20px; border-bottom: 1px solid var(--line-2); }
.card__head h3 { font-family: var(--font-d); font-weight: 700; font-size: 15px; }
.card__head .more { font-family: var(--font-m); font-size: 11px; color: var(--ink-3); cursor: pointer; }
.card__body { padding: 16px 20px; }
.cov__row { display: grid; grid-template-columns: 54px 1fr auto; gap: 12px; align-items: center; padding: 9px 0; }
.cov__row span { font-size: 13px; }
.cov__row .v { font-family: var(--font-m); font-size: 12px; color: var(--ink-2); }
.cov__bar { height: 8px; border-radius: 5px; background: var(--line-2); overflow: hidden; }
.cov__bar i { display: block; height: 100%; border-radius: 5px; background: linear-gradient(90deg, var(--indigo-hi), var(--indigo)); }
.cov__foot { display: flex; gap: 18px; margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--line-2); font-size: 12.5px; color: var(--ink-2); }
.cov__foot b { font-family: var(--font-d); color: var(--ink); font-size: 18px; display: block; margin-bottom: 2px; }
</style>
