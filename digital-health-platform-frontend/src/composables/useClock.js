import { ref, onMounted, onUnmounted } from 'vue'
import { pad2 } from '../utils/format'

/** 秒级时钟:常驻"活着"的纯前端信号(落地页 ticker、工作台顶栏复用) */
export function useClock() {
  const now = ref(clockStr())
  let id = null
  onMounted(() => {
    id = setInterval(() => (now.value = clockStr()), 1000)
  })
  onUnmounted(() => id && clearInterval(id))
  return now
}

function clockStr(d = new Date()) {
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
}
