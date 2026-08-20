<template>
  <div class="locmap">
    <!-- 降级:未配置 key → 占位卡片(展示点位数) -->
    <template v-if="!enabled">
      <div class="ph" :style="{ height: height + 'px' }">
        <span class="ph-ico">🗺️</span>
        <span class="ph-t">分布地图未启用</span>
        <span class="ph-s">已加载 {{ points.length }} 个{{ unit }} · 配置 VITE_AMAP_KEY 后此处渲染分布图</span>
      </div>
    </template>

    <!-- 地图模式 -->
    <template v-else>
      <div ref="mapEl" class="mapbox" :class="{ bad: state === 'bad' }" :style="{ height: height + 'px' }"></div>
      <div v-if="state === 'bad'" class="tip warn">地图加载失败 — {{ points.length }} 个{{ unit }},详见下表</div>
    </template>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { amapEnabled, loadAmap } from '../utils/amap'

const props = defineProps({
  points: { type: Array, default: () => [] },    // [{ name, longitude, latitude, tone? }]
  height: { type: Number, default: 260 },
  unit: { type: String, default: '网点' },        // 占位/失败文案里的点位名称(网点/医院…)
  tone: { type: String, default: 'outlet' },      // 默认标记色:outlet 靛蓝水滴 / inst 琥珀环
  label: { type: Boolean, default: false }        // true=常显名字标签(精选集,如落地页);false=悬停才显名字
})

const enabled = amapEnabled
const mapEl = ref(null)
const state = ref('idle')            // idle|loading|ready|bad
let map = null, AMap = null, info = null, markers = []

// 中国大陆大致经纬度范围(过滤 0,0 / 海外脏坐标,避免 fitView 被单个离群点拉到"半个世界")
const IN_CHINA = (lo, la) => lo >= 73 && lo <= 135 && la >= 18 && la <= 54
const CN_CENTER = [104, 35]

const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]))

// 标记 DOM:18×18 容器居中(offset -9,-9),dot 居中;label 模式常显名字,否则仅 dot
function markerHtml(p) {
  const tone = p.tone || props.tone
  const cls = tone === 'inst' ? 'amap-mk--inst' : 'amap-mk--out'
  const lab = props.label ? `<span class="amap-mk__lab">${esc(p.name)}</span>` : ''
  return `<div class="amap-mk ${cls}"><span class="amap-mk__pulse"></span><span class="amap-mk__d"></span>${lab}</div>`
}

function openTip(p, pos) {
  if (!info || !map) return
  const tone = p.tone || props.tone
  info.setContent(`<div class="amap-tip amap-tip--${tone}">${esc(p.name)}</div>`)
  info.open(map, pos)
}

function draw() {
  if (!map || !AMap) return
  markers.forEach((m) => map.remove(m))
  markers = []
  const pts = props.points.filter((p) => p.longitude != null && p.latitude != null && IN_CHINA(Number(p.longitude), Number(p.latitude)))
  if (!pts.length) {
    // 无中国境内有效点:回到全国视角,而不是 fitView 空集→缩到全球
    map.setZoomAndCenter(4, CN_CENTER)
    return
  }
  pts.forEach((p) => {
    const pos = [Number(p.longitude), Number(p.latitude)]
    const m = new AMap.Marker({ position: pos, content: markerHtml(p), offset: new AMap.Pixel(-9, -9), title: p.name })
    // 非常显标签模式:悬停标记弹出名字(常显模式标签已在 DOM 里,无需弹窗)
    if (!props.label) {
      m.on('mouseover', () => openTip(p, pos))
      m.on('mouseout', () => info && info.close())
    }
    map.add(m); markers.push(m)
  })
  // fitView 自适应点位;第 4 参 maxZoom=14 防止单点过度放大
  map.setFitView(markers, false, [60, 60, 60, 60], 14)
  // 兜底:个别边界/重叠点偶尔把视野拉太远(<全国级),拉回中国中心
  if (map.getZoom() < 3.5) map.setZoomAndCenter(4, CN_CENTER)
}

// 对外暴露:点列表行 → 放大定位到该点并弹出名字(供各端"地图+列表联动")
function focus(lng, lat, opts = {}) {
  if (!map || !AMap || lng == null || lat == null) return
  const pos = [Number(lng), Number(lat)]
  map.setZoomAndCenter(opts.zoom ?? 15, pos)
  if (opts.name) openTip({ name: opts.name, tone: opts.tone }, pos)
}
defineExpose({ focus })

onMounted(async () => {
  if (!enabled) return
  state.value = 'loading'
  AMap = await loadAmap()
  if (!AMap || !mapEl.value) { state.value = 'bad'; return }
  try {
    map = new AMap.Map(mapEl.value, { zoom: 4, center: CN_CENTER, viewMode: '2D' })
    // autoMove:false —— 关掉气泡打开时的自动平移;否则它会打断 focus() 的 setZoomAndCenter 动画,
    // 造成"第一次点击落在旁边、第二次才精准"(气泡平移 vs 缩放动画打架)。focus 已把点居中,气泡在正上方本就可见。
    info = new AMap.InfoWindow({ isCustom: true, offset: new AMap.Pixel(0, -26), closeWhenClickMap: true, autoMove: false })
    draw()
    state.value = 'ready'
  } catch {
    state.value = 'bad'
  }
})

watch(() => props.points, draw, { deep: true })
onBeforeUnmount(() => { if (map) { map.destroy(); map = null } })
</script>

<style scoped>
.locmap { width: 100%; }
.ph {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px;
  height: 260px; border-radius: var(--rad); text-align: center;
  background:
    radial-gradient(circle at 30% 20%, rgba(67, 56, 202, 0.08), transparent 60%),
    radial-gradient(circle at 70% 80%, rgba(67, 56, 202, 0.06), transparent 55%),
    var(--surface);
  border: 1px dashed var(--line);
}
.ph-ico { font-size: 34px; line-height: 1; opacity: 0.8; }
.ph-t { font-size: 14px; font-weight: 600; color: var(--ink-2); }
.ph-s { font-size: 12px; color: var(--ink-3); }
.mapbox { height: 260px; border-radius: var(--rad); border: 1px solid var(--line); overflow: hidden; background: var(--surface); }
.mapbox.bad { border-color: rgba(220, 42, 69, 0.4); }
.tip.warn { margin-top: 8px; font-size: 12px; color: var(--red, #dc2a45); }
</style>

<!-- 高德自定义标记/弹窗的 DOM 由 SDK 注入到地图容器,scoped 够不到,故用全局块 -->
<style>
.amap-mk { position: relative; width: 18px; height: 18px; }
.amap-mk__d { position: absolute; left: 2px; top: 2px; width: 14px; height: 14px; }
.amap-mk--out .amap-mk__d {
  border-radius: 50% 50% 50% 2px; background: #4338ca;
  transform: rotate(-45deg); transform-origin: center;
  box-shadow: 0 4px 10px rgba(67, 56, 202, 0.45);
}
.amap-mk--inst .amap-mk__d { width: 16px; height: 16px; left: 1px; top: 1px; border-radius: 50%; background: transparent; border: 2.5px solid #d97706; box-shadow: 0 3px 8px rgba(217, 119, 6, 0.35); }
.amap-mk__pulse {
  position: absolute; left: 50%; top: 50%; width: 14px; height: 14px; border-radius: 50%;
  background: rgba(99, 102, 241, 0.28); transform: translate(-50%, -50%);
  animation: amappp 2.4s infinite; pointer-events: none;
}
.amap-mk--inst .amap-mk__pulse { background: rgba(217, 119, 6, 0.2); }
@keyframes amappp { 0% { transform: translate(-50%, -50%) scale(0.6); opacity: 0.7; } 100% { transform: translate(-50%, -50%) scale(3); opacity: 0; } }
/* 常显名字标签(label 模式):偏右、白底胶囊,紧贴标记 */
.amap-mk__lab {
  position: absolute; left: 22px; top: 50%; transform: translateY(-50%);
  font-family: 'DM Mono', ui-monospace, monospace; font-weight: 600; font-size: 11px; line-height: 1.3;
  color: #3b4257; background: rgba(255, 255, 255, 0.96); padding: 2px 7px; border-radius: 6px;
  white-space: nowrap; border: 1px solid #e2e7f2; box-shadow: 0 2px 8px rgba(16, 18, 32, 0.1);
}
.amap-mk--inst .amap-mk__lab { color: #b45309; }
/* 悬停弹窗 / focus 弹窗 */
.amap-tip {
  font-family: 'DM Mono', ui-monospace, monospace; font-weight: 500; font-size: 12px; line-height: 1.4;
  color: #161b2e; background: #fff; padding: 5px 10px; border-radius: 8px;
  border: 1px solid #e2e7f2; box-shadow: 0 6px 18px rgba(16, 18, 32, 0.14); white-space: nowrap;
}
.amap-tip--inst { color: #b45309; }
</style>
