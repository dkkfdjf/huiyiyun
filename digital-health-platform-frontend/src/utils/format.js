// 展示层格式化工具

export const nFmt = (n) => (Number(n) || 0).toLocaleString('zh-CN')

export const money = (n) => '¥' + nFmt(n)

export const pct = (n) => Math.round((Number(n) || 0) * 100) / 100 + '%'

/** 周(YYYYWW)→ 展示短标,如 '202628' → 'W28' */
export const weekShort = (w) => 'W' + String(w).slice(-2)

/**
 * 库存状态:依 当前库存/安全线 比例判定
 * @returns {{key,label,color,pct}} key: lo|mid|ok
 */
export function stockState(qty, threshold) {
  const q = Number(qty) || 0
  const th = Number(threshold) || 1
  const ratio = q / th
  let key, label, color
  if (ratio <= 0.3) {
    key = 'lo'
    label = '紧急'
    color = 'var(--red)'
  } else if (ratio <= 1) {
    key = 'mid'
    label = '偏低'
    color = 'var(--amber)'
  } else {
    key = 'ok'
    label = '正常'
    color = 'var(--green)'
  }
  return { key, label, color, pct: Math.min(100, Math.max(4, Math.round(ratio * 100))) }
}

/** 运营流水 delta 文本与色调 */
export function deltaText(type, delta) {
  const n = Number(delta) || 0
  return {
    text: (n >= 0 ? '+' : '−') + nFmt(Math.abs(n)),
    cls: type === 'out' || n < 0 ? 'out' : 'in'
  }
}

const WD = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
export function todayLine(d = new Date()) {
  const pad = (n) => (n < 10 ? '0' + n : '' + n)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${WD[d.getDay()]} · 今日运营总览`
}

export const pad2 = (n) => (n < 10 ? '0' + n : '' + n)

/** ISO 日期时间(如 '2026-08-04T14:30:00')→ 'MM-DD HH:mm'(运营流水紧凑展示,带日期) */
export const fmtDateTime = (iso) => {
  const s = String(iso || '')
  if (s.length < 16) return s
  return s.slice(5, 10) + ' ' + s.slice(11, 16)
}
