<template>
  <div class="picker">
    <!-- 降级:未配置 key → 占位 + 手填经纬度(CRUD 不受影响) -->
    <template v-if="!enabled">
      <div class="ph">
        <span class="ph-ico">🗺️</span>
        <span class="ph-t">地图未启用(未配置 VITE_AMAP_KEY)</span>
        <span class="ph-s">可手动填写经纬度,网点保存与展示不受影响</span>
      </div>
      <div class="coords">
        <el-input-number v-model="lngModel" :controls="false" :precision="6" :step="0.000001" placeholder="经度 lng" style="width: 100%" />
        <el-input-number v-model="latModel" :controls="false" :precision="6" :step="0.000001" placeholder="纬度 lat" style="width: 100%" />
      </div>
    </template>

    <!-- 地图模式 -->
    <template v-else>
      <div class="search">
        <el-input v-model="kw" placeholder="搜索地点(如:协和医院)" clearable style="flex: 1" @keyup.enter="doSearch" />
        <el-button @click="doSearch">搜索</el-button>
      </div>
      <div ref="mapEl" class="mapbox" :class="{ dim: state === 'loading', bad: state === 'bad' }"></div>
      <div v-if="state === 'bad'" class="tip warn">地图加载失败,请在下方手动填写经纬度</div>
      <div class="coords">
        <el-input-number v-model="lngModel" :controls="false" :precision="6" :step="0.000001" placeholder="经度 lng" style="width: 100%" />
        <el-input-number v-model="latModel" :controls="false" :precision="6" :step="0.000001" placeholder="纬度 lat" style="width: 100%" />
      </div>
      <div class="tip">点击地图取点,或搜索后自动定位</div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { amapEnabled, loadAmap } from '../utils/amap'

const props = defineProps({
  longitude: { type: [Number, String], default: null },
  latitude: { type: [Number, String], default: null }
})
const emit = defineEmits(['update:longitude', 'update:latitude'])

const enabled = amapEnabled
const mapEl = ref(null)
const state = ref('idle')            // idle|loading|ready|bad
const kw = ref('')
let map = null, marker = null, AMap = null, placeSearch = null

const lngModel = computed({
  get: () => (props.longitude === '' || props.longitude == null) ? undefined : Number(props.longitude),
  set: (v) => emit('update:longitude', v == null ? null : v)
})
const latModel = computed({
  get: () => (props.latitude === '' || props.latitude == null) ? undefined : Number(props.latitude),
  set: (v) => emit('update:latitude', v == null ? null : v)
})

function setPoint(lng, lat) {
  emit('update:longitude', Number(Number(lng).toFixed(6)))
  emit('update:latitude', Number(Number(lat).toFixed(6)))
  moveMarker(Number(lng), Number(lat))
}

function moveMarker(lng, lat) {
  if (!map || !AMap) return
  const p = [lng, lat]
  if (marker) marker.setPosition(p)
  else { marker = new AMap.Marker({ position: p }); map.add(marker) }
  map.setCenter(p)
}

onMounted(async () => {
  if (!enabled) return
  state.value = 'loading'
  AMap = await loadAmap()
  if (!AMap || !mapEl.value) { state.value = 'bad'; return }
  try {
    const hasPt = props.longitude != null && props.latitude != null
    const center = hasPt ? [Number(props.longitude), Number(props.latitude)] : [104.0668, 30.5728]
    map = new AMap.Map(mapEl.value, { zoom: hasPt ? 14 : 4, center, viewMode: '2D' })
    map.on('click', (e) => setPoint(e.lnglat.getLng(), e.lnglat.getLat()))
    if (hasPt) { marker = new AMap.Marker({ position: center }); map.add(marker) }
    placeSearch = new AMap.PlaceSearch({ pageSize: 1, extensions: 'all' })
    state.value = 'ready'
  } catch {
    state.value = 'bad'
  }
})

function doSearch() {
  if (!kw.value || !placeSearch) return
  placeSearch.search(kw.value, (status, res) => {
    if (status !== 'complete' || !res || !res.poiList || !res.poiList.pois.length) return
    const poi = res.poiList.pois[0]
    setPoint(poi.location.getLng(), poi.location.getLat())
  })
}

// 外部(输入框)改值时同步标点
watch([() => props.longitude, () => props.latitude], ([lng, lat]) => {
  if (lng != null && lat != null) moveMarker(Number(lng), Number(lat))
})

onBeforeUnmount(() => { if (map) { map.destroy(); map = null } })
</script>

<style scoped>
.picker { display: flex; flex-direction: column; gap: 10px; }
.ph {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px;
  padding: 26px 16px; border-radius: var(--rad); text-align: center;
  background: var(--surface); border: 1px dashed var(--line);
}
.ph-ico { font-size: 30px; line-height: 1; }
.ph-t { font-size: 13.5px; font-weight: 600; color: var(--ink-2); }
.ph-s { font-size: 12px; color: var(--ink-3); }
.coords { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.search { display: flex; gap: 8px; }
.mapbox { height: 260px; border-radius: var(--rad); border: 1px solid var(--line); overflow: hidden; background: var(--surface); }
.mapbox.dim { opacity: 0.6; }
.mapbox.bad { border-color: rgba(220, 42, 69, 0.4); }
.tip { font-size: 12px; color: var(--ink-3); }
.tip.warn { color: var(--red, #dc2a45); }
</style>
