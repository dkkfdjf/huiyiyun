<template>
  <div class="card">
    <div class="card__head">
      <h3>{{ title }}</h3>
      <span v-if="more" class="more" @click="$emit('more')">{{ more }}</span>
    </div>
    <div class="card__body">
      <div v-for="(it, i) in items" :key="i" class="tl__it">
        <span class="tl__d" :class="it.dot"></span>
        <div class="tl__t">
          <b>{{ it.title }}</b>
          <small>{{ it.sub }}</small>
        </div>
        <span class="tl__a">{{ it.action }}</span>
      </div>
      <div v-if="!items.length" class="empty">暂无动态</div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '近期动态' },
  more: { type: String, default: '' },
  items: { type: Array, default: () => [] } // {dot:''|amber|green|red, title, sub, action}
})
defineEmits(['more']) // 点击"更多"跳转(首页按需监听;不监听则纯展示)
</script>

<style scoped>
.card { background: var(--surface); border: 1px solid var(--line); border-radius: var(--rad); box-shadow: var(--sh-sm); }
.card__head { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 15px 20px; border-bottom: 1px solid var(--line-2); }
.card__head h3 { font-family: var(--font-d); font-weight: 700; font-size: 15px; }
.card__head .more { font-family: var(--font-m); font-size: 11px; color: var(--ink-3); cursor: pointer; }
.card__body { padding: 16px 20px; max-height: 360px; overflow-y: auto; }
.tl__it { display: grid; grid-template-columns: auto 1fr auto; gap: 13px; align-items: flex-start; padding: 11px 0; border-top: 1px solid var(--line-2); }
.tl__it:first-child { border-top: none; }
.tl__d { width: 9px; height: 9px; border-radius: 50%; margin-top: 5px; background: var(--indigo-hi); }
.tl__d.amber { background: var(--amber); }
.tl__d.green { background: var(--green); }
.tl__d.red { background: var(--red); }
.tl__t { font-size: 13px; }
.tl__t b { font-weight: 600; }
.tl__t small { display: block; font-family: var(--font-m); font-size: 10.5px; color: var(--ink-3); margin-top: 2px; }
.tl__a { font-family: var(--font-m); font-size: 10.5px; color: var(--ink-3); }
.empty { text-align: center; color: var(--ink-3); padding: 18px 0; }
</style>
