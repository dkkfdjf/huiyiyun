// 共享动效指令:count-up 数字滚动、reveal 滚入显隐。
// 两套页面复刻自 preview,行为一致,故提为全局指令避免重复。

const prefersReduced = () =>
  typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

function animateCount(el, to) {
  const target = Number(to) || 0
  if (prefersReduced()) {
    el.textContent = target.toLocaleString('zh-CN')
    return
  }
  let start = null
  const dur = 1200
  function step(t) {
    if (!start) start = t
    const p = Math.min((t - start) / dur, 1)
    const v = Math.round((0.5 - Math.cos(p * Math.PI) / 2) * target)
    el.textContent = v.toLocaleString('zh-CN')
    if (p < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}

/** v-count="<number>" —— 元素进入视口后从 0 滚到目标值(toLocaleString) */
export const vCount = {
  mounted(el, binding) {
    el.textContent = '0'
    el._countTarget = Number(binding.value) || 0
    el._countVisible = false
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => {
          if (e.isIntersecting) {
            el._countVisible = true
            animateCount(el, el._countTarget)
            io.disconnect()
          }
        })
      },
      { threshold: 0.5 }
    )
    io.observe(el)
  },
  updated(el, binding) {
    const v = Number(binding.value) || 0
    if (v === el._countTarget) return            // 值未变,跳过(避免无谓重播)
    el._countTarget = v
    // 元素已在视口(首屏 IO 已触发过):后端数据晚到、值变化时立即重播——
    // 否则首屏数字会因 IO 抢先于 API 滚到 0 后永久卡死(IO 已 disconnect 不再触发)。
    // 尚未进入视口:仅更新 target,待 IO 触发时用新值播放。
    if (el._countVisible) animateCount(el, v)
  }
}

/** v-reveal —— 自带 .reveal 基础类,进入视口加 .is-revealed(配合全局 .reveal 样式) */
export const vReveal = {
  mounted(el) {
    el.classList.add('reveal')
    if (prefersReduced()) {
      el.classList.add('is-revealed')
      return
    }
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => {
          if (e.isIntersecting) {
            el.classList.add('is-revealed')
            io.disconnect()
          }
        })
      },
      { threshold: 0.14 }
    )
    io.observe(el)
  }
}

export function installDirectives(app) {
  app.directive('count', vCount)
  app.directive('reveal', vReveal)
}
