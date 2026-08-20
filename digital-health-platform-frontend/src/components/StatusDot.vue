<template>
  <span class="sdot" :class="tone"><i class="dot"></i><slot>{{ label }}</slot></span>
</template>

<script setup>
/* 轻量状态点:彩色圆点 + 文字。替代 el-tag 的彩色块,克制不塑料。
   tone: gray(默认/中性) | indigo(进行中) | green(成功/已完成) | amber(待办/警告) | red(危险/紧急) */
defineProps({
  tone: { type: String, default: 'gray' },
  label: { type: String, default: '' }
})
</script>

<style scoped>
.sdot {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  color: var(--ink-2);
  white-space: nowrap;
  line-height: 1;
}
.sdot .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex: none;
  background: var(--ink-4);
}
.sdot.amber .dot { background: var(--amber); }
.sdot.indigo .dot { background: var(--indigo-hi); }
.sdot.green .dot { background: var(--green); }
.sdot.red .dot { background: var(--red); }
.sdot.gray .dot { background: var(--ink-4); }
</style>
