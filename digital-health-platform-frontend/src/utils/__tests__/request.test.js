import { describe, it, expect, vi } from 'vitest'

// 屏蔽 request.js 的重依赖:element-plus 渲染、router 跳转、auth token
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn(), success: vi.fn(), info: vi.fn() } }))
vi.mock('../auth', () => ({ getToken: () => 'TOK', clearToken: vi.fn(), isLoggedIn: () => false }))
vi.mock('../../router', () => ({ default: { push: vi.fn() } }))

import { unwrap } from '../request'

describe('request unwrap', () => {
  it('returns data on code 200', async () => {
    expect(await unwrap({ code: 200, message: 'success', data: { a: 1 } })).toEqual({ a: 1 })
  })

  it('throws the body on non-200', async () => {
    let caught
    try {
      await unwrap({ code: 1001, message: '库存不足', data: null })
    } catch (e) {
      caught = e
    }
    expect(caught).toBeTruthy()
    expect(caught.code).toBe(1001)
  })
})
