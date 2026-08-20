import { describe, it, expect, vi, beforeEach } from 'vitest'

// 屏蔽真实 loader:降级路径根本不会调用 load,这里仅隔离模块副作用
vi.mock('@amap/amap-jsapi-loader', () => ({ default: { load: vi.fn() } }))

describe('amap graceful degradation', () => {
  beforeEach(() => {
    // 与本地 .env.local(真实 key)解耦:强制无 key,确保每次都测降级路径
    vi.resetModules()
    vi.stubEnv('VITE_AMAP_KEY', '')
  })

  it('reports disabled when no key is configured', async () => {
    const { amapEnabled } = await import('../amap')
    expect(amapEnabled).toBe(false)
  })

  it('resolves null instead of rejecting when disabled', async () => {
    const { loadAmap } = await import('../amap')
    // 无 key 短路:不发脚本请求,直接 null,绝不 reject
    expect(await loadAmap()).toBeNull()
  })
})
