import AMapLoader from '@amap/amap-jsapi-loader'

const KEY = import.meta.env.VITE_AMAP_KEY
const SEC = import.meta.env.VITE_AMAP_SECURITY

/**
 * 是否配置了高德 Web JS API key。
 * 未配置时,所有地图组件(MapPicker / LocationMap)走降级:占位 + 手填经纬度,
 * 网点 CRUD 完全不受影响。配置方式见 frontend/.env.example。
 */
export const amapEnabled = !!KEY

let _p = null

/**
 * 加载高德 JS SDK(单例)。无 key 或加载失败均 resolve(null) —— 组件据此切降级,绝不 reject。
 * 无 key 时短路,不发注定失败的脚本请求。
 */
export function loadAmap() {
  if (!KEY) return Promise.resolve(null)
  if (_p) return _p
  if (SEC) window._AMapSecurityConfig = { securityJsCode: SEC }
  const attempt = () =>
    AMapLoader.load({
      key: KEY,
      version: '2.0',
      plugins: ['AMap.Scale', 'AMap.Geocoder', 'AMap.AutoComplete', 'AMap.PlaceSearch']
    })
  // 偶发加载失败(网络 / CDN 抖动)重试一次,仍失败才降级;单例缓存,绝不 reject
  _p = attempt().catch(
    () => new Promise((resolve) => setTimeout(() => attempt().then(resolve).catch(() => resolve(null)), 800))
  )
  return _p
}
