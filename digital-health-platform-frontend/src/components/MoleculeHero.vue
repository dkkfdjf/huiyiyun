<template>
  <div class="molecule-wrap">
   <div class="molecule-float">
    <svg ref="mol" class="mol" viewBox="0 0 460 460" role="img" aria-label="药物分子结构示意">
      <defs>
        <radialGradient id="mgrad" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stop-color="#6366F1" stop-opacity=".35" />
          <stop offset="100%" stop-color="#6366F1" stop-opacity="0" />
        </radialGradient>
      </defs>
      <circle class="glow" cx="230" cy="230" r="200" />
      <line v-for="(b, i) in bonds" :key="i" class="bond" :x1="b[0]" :y1="b[1]" :x2="b[2]" :y2="b[3]" />
      <g v-for="(a, i) in atoms" :key="'a' + i" class="atom" :style="{ transitionDelay: a.d + 's' }">
        <circle :class="a.c" :cx="a.x" :cy="a.y" :r="a.r" />
        <text v-if="a.lbl" class="lbl" :x="a.x" :y="a.y">{{ a.lbl }}</text>
      </g>
      <g class="orbit">
        <circle cx="230" cy="70" r="4.5" fill="#6366F1" opacity=".9" />
        <animateTransform attributeName="transform" attributeType="XML" type="rotate" from="0 230 230" to="360 230 230" dur="13s" repeatCount="indefinite" />
      </g>
      <g class="orbit">
        <circle cx="230" cy="125" r="3.5" fill="#7C3AED" opacity=".85" />
        <animateTransform attributeName="transform" attributeType="XML" type="rotate" from="360 230 230" to="0 230 230" dur="9s" repeatCount="indefinite" />
      </g>
      <g class="orbit">
        <circle cx="230" cy="170" r="3" fill="#6366F1" opacity=".8" />
        <animateTransform attributeName="transform" attributeType="XML" type="rotate" from="0 230 230" to="360 230 230" dur="18s" repeatCount="indefinite" />
      </g>
    </svg>
    <div class="mol-cap">
      <b>阿莫西林 <small>Amoxicillin</small></b>
      <span class="f">C₁₆H₁₉N₃O₅S</span>
    </div>
   </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const mol = ref(null)

// 键(苯环 + 取代基连线),坐标取自 preview/v12.html
const bonds = [
  [230, 150, 299, 190], [299, 190, 299, 270], [299, 270, 230, 310], [230, 310, 161, 270],
  [161, 270, 161, 190], [161, 190, 230, 150], [236, 162, 287, 192], [287, 268, 236, 298],
  [173, 262, 173, 198], [230, 150, 230, 80], [299, 190, 368, 150], [299, 270, 368, 310],
  [362, 304, 362, 290], [161, 190, 92, 150]
]
// 原子:碳点 + 取代基(OH/N/O/CH₃)
const atoms = [
  { x: 230, y: 150, r: 5, c: 'a-c', d: 0.34 }, { x: 299, y: 190, r: 5, c: 'a-c', d: 0.4 },
  { x: 299, y: 270, r: 5, c: 'a-c', d: 0.46 }, { x: 230, y: 310, r: 5, c: 'a-c', d: 0.52 },
  { x: 161, y: 270, r: 5, c: 'a-c', d: 0.58 }, { x: 161, y: 190, r: 5, c: 'a-c', d: 0.64 },
  { x: 230, y: 80, r: 13, c: 'a-amb', d: 0.7, lbl: 'OH' },
  { x: 368, y: 150, r: 13, c: 'a-het', d: 0.76, lbl: 'N' },
  { x: 368, y: 310, r: 13, c: 'a-amb', d: 0.82, lbl: 'O' },
  { x: 92, y: 150, r: 13, c: 'a-het', d: 0.88, lbl: 'CH₃' }
]

onMounted(() => {
  // bonds 错峰 + ready 组装(提速,与 hero 文案同步收尾)
  mol.value.querySelectorAll('.bond').forEach((b, i) => {
    b.style.transitionDelay = i * 0.035 + 's'
  })
  requestAnimationFrame(() => setTimeout(() => mol.value.classList.add('ready'), 160))
})
</script>

<style scoped>
.molecule-wrap {
  position: relative;
  display: grid;
  place-items: center;
}
.molecule-float {
  position: relative; /* 供 mol-cap 绝对定位锚定 */
  display: grid;
  place-items: center;
  animation: mfloat 7s ease-in-out infinite; /* 持续浮动移到内层:根元素由父级 .hin 入场动画接管,二者分处不同元素互不覆盖 */
}
@keyframes mfloat {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-12px); }
}
.mol {
  width: min(420px, 92%);
  height: auto;
  overflow: visible;
  filter: drop-shadow(0 18px 40px rgba(67, 56, 202, 0.2));
}
.mol .bond {
  stroke: var(--indigo);
  stroke-width: 3;
  stroke-linecap: round;
  stroke-dasharray: 240;
  stroke-dashoffset: 240;
  transition: stroke-dashoffset 0.6s ease;
}
.mol .atom {
  opacity: 0;
  transform-box: fill-box;
  transform-origin: center;
  transform: scale(0);
  transition:
    opacity 0.4s cubic-bezier(0.2, 1.4, 0.4, 1),
    transform 0.4s cubic-bezier(0.2, 1.4, 0.4, 1);
}
.mol.ready .bond { stroke-dashoffset: 0; }
.mol.ready .atom { opacity: 1; transform: scale(1); }
.mol .a-c { fill: var(--ink); }
.mol .a-het { fill: var(--indigo); }
.mol .a-amb { fill: var(--amber); }
.mol .lbl {
  font-family: var(--font-m);
  font-size: 13px;
  font-weight: 600;
  text-anchor: middle;
  dominant-baseline: central;
  fill: #fff;
}
.mol .glow {
  fill: url(#mgrad);
  transform-box: fill-box;
  transform-origin: center;
  animation: breathe 4.5s ease-in-out infinite;
}
@keyframes breathe {
  0%, 100% { opacity: 0.4; transform: scale(0.95); }
  50% { opacity: 0.58; transform: scale(1.08); }
}
.mol-cap {
  position: absolute;
  bottom: -4px;
  left: 50%;
  transform: translateX(-50%);
  text-align: center;
  white-space: nowrap;
}
.mol-cap b { font-family: var(--font-d); font-size: 15px; font-weight: 700; }
.mol-cap b small {
  font-family: var(--font-m);
  font-weight: 400;
  font-size: 11px;
  color: var(--ink-3);
  margin-left: 6px;
}
.mol-cap .f {
  display: block;
  font-family: var(--font-m);
  font-size: 13px;
  color: var(--indigo);
  margin-top: 3px;
  letter-spacing: 1px;
}
@media (prefers-reduced-motion: reduce) {
  .mol .bond { stroke-dashoffset: 0 !important; }
  .mol .atom { opacity: 1 !important; transform: scale(1) !important; }
}
</style>
