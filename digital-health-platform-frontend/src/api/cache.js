import http from '../utils/request'

// Redis 缓存监控(系统监控页轮询;仅管理员 —— 后端 @RequiresRole(ADMIN))
// silent:后端/Redis 短暂不可达时不弹"服务器错误"刷屏,组件以 online=false 自行展示离线态
export const cacheStats = () => http.get('/cache/stats', { silent: true })
// 一键清空全部业务缓存(Flyway/手动改库后,驱逐旧缓存让下次访问回查数据库)
export const clearCache = () => http.post('/cache/evict')
