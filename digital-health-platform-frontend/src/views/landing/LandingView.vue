<template>
  <div class="landing">
    <div class="amb" aria-hidden="true">
      <i v-for="(p, i) in particles" :key="i" :style="p"></i>
    </div>
    <div class="progress" :style="{ width: progress + '%' }"></div>

    <nav class="nav">
      <div class="wrap nav__in">
        <router-link class="brand" to="/">
          <span class="brand__dot"></span>
          <span class="brand__t">慧医云<small>Huiyi Cloud</small></span>
        </router-link>
        <div class="nav__links">
          <a href="#trend">实时运营</a>
          <a href="#chain">供应链</a>
          <a href="#map">覆盖</a>
          <a href="#roles">角色</a>
        </div>
        <div class="nav__cta">
          <router-link class="btn btn--solid" to="/admin">进入工作台 <span class="arr">→</span></router-link>
        </div>
      </div>
    </nav>

    <header class="hero">
      <div class="wrap hero__in">
        <div class="hero__copy">
          <span class="kick hin"><i></i>医药供应链与医师资源平台</span>
          <h1 class="hero__t hin">医药运营，<span class="l2">一处统管。</span></h1>
          <p class="hero__sub hin">药、医、网点，散者归一；进销、调度与流向，于同一云端同步而动。</p>
          <div class="hero__cta hin">
            <router-link class="btn btn--solid" to="/admin">进入工作台 <span class="arr">→</span></router-link>
            <a class="btn btn--ghost" href="#trend">查看实时运营</a>
          </div>
          <div class="hero__stats hin">
            <div class="st"><div class="v in"><span v-count="week.inQty">0</span><span class="u">件</span></div><div class="k">本周入库</div></div>
            <div class="st"><div class="v out"><span v-count="week.outQty">0</span><span class="u">件</span></div><div class="k">本周出库</div></div>
            <div class="st"><div class="v in">¥<span v-count="week.salesAmount">0</span></div><div class="k">本周销售额</div></div>
          </div>
        </div>
        <MoleculeHero class="hin" />
      </div>
    </header>

    <!-- 实时运营 -->
    <section id="trend" style="padding-top: 0">
      <div class="wrap">
        <div class="sec__head" v-reveal>
          <div class="sec__tag">实时运营</div>
          <h2 class="sec__t">此刻，账实同息。</h2>
          <p class="sec__d">每一笔进出，即刻写入库存；销售、出库与净库存随之同步。近 12 周趋势与实时运营流水如下。</p>
        </div>
        <div class="trend__grid">
          <div class="card chart" v-reveal>
            <div class="chart__top">
              <div class="chart__legend">
                <span><i style="background: var(--indigo)"></i>销售额（元）</span>
                <span><i style="background: var(--amber); opacity: 0.7"></i>出库量（件）</span>
              </div>
              <span class="mono chart__note">近 12 周 · 实时</span>
            </div>
            <svg ref="chartSvg" class="chart__svg" :class="{ in: chartIn }" viewBox="0 0 720 280" role="img" aria-label="销售与出库趋势">
              <defs>
                <linearGradient id="tgrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#6366F1" stop-opacity=".30" />
                  <stop offset="100%" stop-color="#6366F1" stop-opacity="0" />
                </linearGradient>
              </defs>
              <line class="grid" x1="50" y1="50" x2="690" y2="50" /><line class="grid" x1="50" y1="100" x2="690" y2="100" />
              <line class="grid" x1="50" y1="150" x2="690" y2="150" /><line class="grid" x1="50" y1="200" x2="690" y2="200" />
              <line class="grid axis" x1="50" y1="230" x2="690" y2="230" />
              <!-- 双 Y 轴:左轴销售额(¥,靛蓝)、右轴出库量(件,琥珀),刻度颜色与各自线条一致 -->
              <text v-for="(t,i) in chart.axisTicks" :key="'sl'+i" class="ax ax--s" :x="42" :y="t.y + 4" text-anchor="end">¥{{ chart.fmtYuan(t.s) }}</text>
              <text v-for="(t,i) in chart.axisTicks" :key="'or'+i" class="ax ax--o" :x="698" :y="t.y + 4" text-anchor="start">{{ t.o }}</text>
              <path class="line line--out" :d="chart.outPath" />
              <path class="area" :d="chart.areaPath" />
              <path class="line line--sales" :d="chart.salesPath" />
              <g>
                <circle v-for="(p, i) in chart.dots" :key="i" class="dot" :cx="p[0]" :cy="p[1]" r="4" :style="{ transitionDelay: 0.6 + i * 0.07 + 's' }" />
              </g>
              <text class="ax" x="50" y="255" text-anchor="middle">W1</text><text class="ax" x="225" y="255" text-anchor="middle">W4</text>
              <text class="ax" x="399" y="255" text-anchor="middle">W7</text><text class="ax" x="574" y="255" text-anchor="middle">W10</text>
              <text class="ax" x="690" y="255" text-anchor="end">W{{ Math.max(1, trend.length) }}</text>
              <g class="chart__scan"><line x1="50" y1="44" x2="50" y2="230" stroke="#6366F1" stroke-width="1.5" opacity=".5" /></g>
              <circle v-if="chart.last" class="chart__live-ring" :cx="chart.last[0]" :cy="chart.last[1]" r="6" />
              <circle v-if="chart.last" class="chart__live" :cx="chart.last[0]" :cy="chart.last[1]" r="4" />
              <!-- 末端数值标注:两条线各标最新值,销售额(¥)与出库量(件)都明确可见 -->
              <text v-if="chart.last" class="elab elab--s" :x="chart.last[0] + 8" :y="chart.last[1] + 4">¥{{ Number(chart.sLastVal).toLocaleString() }}</text>
              <text v-if="chart.outLast" class="elab elab--o" :x="chart.outLast[0] + 8" :y="chart.outLast[1] + 4">{{ chart.oLastVal }} 件</text>
            </svg>
          </div>
          <div class="card feed" v-reveal>
            <div class="feed__head">
              <h3>运营流水</h3>
              <span class="tag"><i></i>实时 · <span class="mono">{{ clock }}</span></span>
            </div>
            <div class="feed__body">
              <div class="feed__list">
                <div v-for="(r, i) in feedLoop" :key="i" class="feed__row">
                  <span class="feed__t">{{ r.tm }}</span>
                  <span class="feed__e">{{ r.txt }}</span>
                  <span class="feed__d" :class="r.cls">{{ r.delta }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 供应链 -->
    <section id="chain" style="padding-top: 0">
      <div class="wrap">
        <div class="sec__head" v-reveal>
          <div class="sec__tag">供应链</div>
          <h2 class="sec__t">一药一行迹。</h2>
          <p class="sec__d">从入库、铺货到销售、补货，每一笔流向皆有迹可循；任意一批，来路去处皆可追溯。</p>
        </div>
        <div class="chain" v-reveal>
          <div
            v-for="(s, i) in steps"
            :key="i"
            class="step"
            :class="[s.alert ? 'step--alert' : '', chainOn[i] ? 'is-in' : '']"
          >
            <div class="step__n">{{ s.n }}</div>
            <div class="step__t">{{ s.t }}</div>
            <div class="step__d">{{ s.d }}</div>
          </div>
        </div>
        <div class="chain__rev" v-reveal><b>需求回流</b> ─ 医师报缺 → 药企受理 → 反哺补货与铺货</div>
      </div>
    </section>

    <!-- 覆盖 -->
    <section id="map" style="padding-top: 0">
      <div class="wrap">
        <div class="sec__head" v-reveal>
          <div class="sec__tag">覆盖</div>
          <h2 class="sec__t">一图，揽尽版图。</h2>
          <p class="sec__d">网点与医疗机构落点于地图，覆盖的广度与疏密，一览可读。</p>
        </div>
        <div class="card map" ref="mapwrap" v-reveal>
          <!-- 真实高德地图:聚焦中国 fitView,网点(靛蓝水滴)+机构(琥珀环)打点,常显名字标签 -->
          <LocationMap :points="landPoints" :height="380" label />
          <div class="map__legend">
            <span><i></i>销售网点</span>
            <span><i class="inst"></i>医疗机构</span>
            <span class="mono legend__note">实时分布</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 角色与权限 -->
    <section id="roles" style="padding-top: 0">
      <div class="wrap">
        <div class="sec__head" v-reveal>
          <div class="sec__tag">角色与权限</div>
          <h2 class="sec__t">所见，即所辖。</h2>
          <p class="sec__d">管理员、药企、医疗机构与医师四类角色，各守其界——目光所及，恰是权责所至。</p>
        </div>
        <div class="roles__grid" v-reveal>
          <div v-for="r in roles" :key="r.ic" class="role">
            <div class="role__ic">{{ r.ic }}</div>
            <div>
              <div class="role__t">{{ r.t }} <small>{{ r.en }}</small></div>
              <div class="role__d">{{ r.d }}</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 规模 -->
    <section id="scale" style="padding-top: 0">
      <div class="wrap">
        <div class="scale" v-reveal>
          <div class="sec__head scale__head">
            <div class="sec__tag tag--light">平台规模</div>
            <h2 class="sec__t t--light">一云之上，万链归序。</h2>
          </div>
          <div class="scale__grid">
            <div>
              <div class="stat__n"><span v-count="scale.drugCount">0</span><span class="u">+</span></div>
              <div class="stat__rule"></div>
              <div class="stat__l">在管药品，按规格与剂型归档</div>
            </div>
            <div>
              <div class="stat__n"><span v-count="scale.locationCount">0</span></div>
              <div class="stat__rule"></div>
              <div class="stat__l">覆盖销售网点，地图标注</div>
            </div>
            <div>
              <div class="stat__n"><span v-count="scale.institutionCount">0</span></div>
              <div class="stat__rule"></div>
              <div class="stat__l">合作医疗机构</div>
            </div>
            <div>
              <div class="stat__n"><span v-count="scale.doctorCount">0</span><span class="u">+</span></div>
              <div class="stat__rule"></div>
              <div class="stat__l">在册医师，按科室分布</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <footer class="foot">
      <div class="wrap foot__in">
        <div><div class="foot__brand"><i></i>慧医云<small>医药供应链与医师资源平台</small></div></div>
        <div class="foot__note">慧医云 · 医药供应链与医师资源平台。各区块数据来自平台真实运营记录。</div>
        <div class="foot__cta"><router-link class="btn btn--solid" to="/admin">进入工作台 <span class="arr">→</span></router-link></div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import MoleculeHero from '../../components/MoleculeHero.vue'
import LocationMap from '../../components/LocationMap.vue'
import { useClock } from '../../composables/useClock'
import { fmtDateTime, deltaText } from '../../utils/format'
import { getPublicStats } from '../../api/public'

const clock = useClock()

/* —— 公开概览(免登录真实数据) —— */
const stats = ref(null)
const week = computed(() => stats.value?.week || { inQty: 0, outQty: 0, netQty: 0, salesAmount: 0 })
const scale = computed(() => stats.value?.scale || { drugCount: 0, locationCount: 0, institutionCount: 0, doctorCount: 0 })
const trend = computed(() => stats.value?.trend12w || [])

/* —— 趋势图:由真实 12 周数据驱动 path —— */
const chartSvg = ref(null)
const chartIn = ref(false)
const chart = computed(() => {
  const pts = trend.value
  const empty = { salesPath: '', areaPath: '', outPath: '', dots: [], last: null, axisTicks: [], fmtYuan: (v) => v }
  if (!pts.length) return empty
  const sales = pts.map((p) => Number(p.salesAmount) || 0)
  const outs = pts.map((p) => Number(p.outboundQty) || 0)
  const niceMax = (v) => { if (v <= 0) return 1; const mag = Math.pow(10, Math.floor(Math.log10(v))); const nn = v / mag; return (nn <= 1 ? 1 : nn <= 2 ? 2 : nn <= 5 ? 5 : 10) * mag }
  const sAxisMax = niceMax(Math.max(...sales, 1))   // 销售额左轴最大值(圆整为 1/2/5×10^k)
  const oAxisMax = niceMax(Math.max(...outs, 1))    // 出库量右轴最大值
  const n = pts.length
  const x = (i) => 50 + (640 / Math.max(1, n - 1)) * i
  const ys = (v) => 230 - (v / sAxisMax) * 180      // 销售额 → 左轴(¥)
  const yo = (v) => 230 - (v / oAxisMax) * 180      // 出库量 → 右轴(件)
  const mk = (arr, f) => arr.map((_, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${f(arr[i]).toFixed(1)}`).join(' ')
  const salesPath = mk(sales, ys)
  const outPath = mk(outs, yo)
  const areaPath = salesPath + ` L${x(n - 1).toFixed(1)},230 L${x(0).toFixed(1)},230 Z`
  const dots = sales.map((_, i) => [Number(x(i).toFixed(1)), Number(ys(sales[i]).toFixed(1))])
  const outLast = outs.length ? [Number(x(n - 1).toFixed(1)), Number(yo(outs[outs.length - 1]).toFixed(1))] : null
  // 双 Y 轴 5 档刻度:左轴销售额(¥)、右轴出库量(件),按 100/75/50/25/0% 取值
  const ys5 = [50, 100, 150, 200, 230]
  const axisTicks = [1, 0.75, 0.5, 0.25, 0].map((t, i) => ({ y: ys5[i], s: Math.round(sAxisMax * t), o: Math.round(oAxisMax * t) }))
  const fmtYuan = (v) => (v >= 10000 ? `${Math.round(v / 1000)}k` : `${v}`)
  return {
    salesPath, areaPath, outPath, dots,
    last: dots[dots.length - 1] || null, outLast,
    sLastVal: sales[sales.length - 1] || 0,
    oLastVal: outs[outs.length - 1] || 0,
    axisTicks, fmtYuan
  }
})

/* —— 滚动进度条 —— */
const progress = ref(0)
const onScroll = () => {
  const h = document.documentElement
  const max = h.scrollHeight - h.clientHeight
  progress.value = max > 0 ? (h.scrollTop / max) * 100 : 0
}

/* —— 环境层漂浮粒子(全屏持续上升)—— */
const particles = Array.from({ length: 34 }, () => {
  const sz = 3 + Math.random() * 6
  const deep = Math.random() < 0.3
  return {
    left: Math.random() * 100 + '%',
    width: sz + 'px',
    height: sz + 'px',
    background: deep ? 'var(--indigo)' : 'var(--indigo-hi)',
    animationDuration: 12 + Math.random() * 18 + 's',
    animationDelay: -Math.random() * 30 + 's'
  }
})

/* —— 供应链节点逐个点亮(静态说明,数据无关) —— */
const steps = [
  { n: '01', t: '药品入库', d: '录入名称 规格 剂型' },
  { n: '02', t: '网点铺货', d: '设定库存与售价' },
  { n: '03', t: '销售扣减', d: '即时扣减库存' },
  { n: '04', t: '库存预警', d: '低于阈值预警', alert: true },
  { n: '05', t: '补货入库', d: '库存回升' }
]
const chainOn = reactive([false, false, false, false, false])

/* —— 运营流水:真实 recentOps(入库+出库),带日期 —— */
const feedRows = computed(() =>
  (stats.value?.opsFeed || []).map((r) => {
    const d = deltaText(r.type, r.delta)
    return { tm: fmtDateTime(r.time), txt: r.event, delta: d.text, cls: d.cls }
  })
)
const feedLoop = computed(() => {
  const r = feedRows.value
  return r.length ? [...r, ...r] : []        // 双倍以衔接无限滚动 ticker
})

/* —— 覆盖地图打点:经纬度交给高德(tone 区分网点靛蓝/机构琥珀),label 常显名字 —— */
const landPoints = computed(() => [
  ...(stats.value?.locations || []).map((p) => ({ name: p.name, longitude: p.lng, latitude: p.lat, tone: 'outlet' })),
  ...(stats.value?.institutions || []).map((p) => ({ name: p.name + (p.count ? ` · ${p.count} 位医师` : ''), longitude: p.lng, latitude: p.lat, tone: 'inst' }))
])
const mapwrap = ref(null)
const mapIn = ref(false)

/* —— 角色(静态说明) —— */
const roles = [
  { ic: 'AD', t: '管理员', en: 'Admin', d: '平台配置、全局监管与统计，统管药品、网点、机构与医师档案。' },
  { ic: 'PH', t: '药企', en: 'Pharma', d: '维护本企业药品档案，向网点铺货并响应补货与需求工单。' },
  { ic: 'IN', t: '医疗机构', en: 'Institution', d: '管理本院医师与科室分布，查阅公告与必备材料。' },
  { ic: 'DR', t: '医师', en: 'Doctor', d: '上报缺货与采购需求，跟进药企处置与到货状态。' }
]

let observers = []
onMounted(async () => {
  document.addEventListener('scroll', onScroll, { passive: true })
  onScroll()

  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const observe = (el, threshold, cb) => {
    if (!el || reduced) { el && cb(); return null }
    const io = new IntersectionObserver(
      (es) => es.forEach((e) => {
        if (e.isIntersecting) { cb(); io.disconnect() }
      }),
      { threshold }
    )
    io.observe(el)
    return io
  }

  observers.push(
    observe(chartSvg.value, 0.3, () => (chartIn.value = true)),
    observe(mapwrap.value, 0.25, () => (mapIn.value = true))
  )
  // 供应链逐节点点亮
  const chainEl = document.querySelector('.landing .chain')
  observers.push(
    observe(chainEl, 0.4, () => {
      steps.forEach((_, i) => setTimeout(() => (chainOn[i] = true), i * 140))
    })
  )

  // 拉取真实公开概览(免登录)。失败对访客保持静默(getPublicStats 带 silent,不弹错),但记到控制台,
  // 便于区分"真没数据"与"后端不可达"——否则地图/数字空态与后端宕机看起来一模一样。
  try {
    stats.value = await getPublicStats()
  } catch (e) {
    console.error('[landing] 公开概览加载失败,地图/数字维持空态:', e?.message || e)
  }
})
onUnmounted(() => {
  document.removeEventListener('scroll', onScroll)
  observers.forEach((o) => o && o.disconnect())
})
</script>

<style scoped>
/* 落地页令牌(取自 preview/v12.html,覆盖共享默认) */
.landing {
  --bg: #edf1f6;
  --surface-2: #e2e7f1;
  --surface-3: #dce2ee;
  --ink: #0c1222;
  --ink-2: #475063;
  --indigo-soft: #e0e7ff;
  --indigo-glow: rgba(99, 102, 241, 0.16);
  --line: #d6dce6;
  --rad: 18px;
  --sh: 0 14px 40px -20px rgba(67, 56, 202, 0.2);
  --sh-sm: 0 6px 18px -10px rgba(67, 56, 202, 0.16);
  position: relative;
  min-height: 100vh;
  background: var(--bg);
  overflow-x: hidden;
  isolation: isolate; /* 建层叠上下文:让 z-index:-1 的粒子层画在背景之上、内容之下 */
}
.landing::before {
  content: '';
  position: fixed;
  inset: 0;
  z-index: -2;
  pointer-events: none;
  background:
    radial-gradient(80% 50% at 88% -5%, var(--indigo-glow), transparent 60%),
    radial-gradient(60% 40% at 0% 0%, rgba(124, 58, 237, 0.06), transparent 60%);
}

/* 环境层 + 进度条 */
.amb {
  position: fixed;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  overflow: hidden;
}
.amb i {
  position: absolute;
  bottom: -24px;
  border-radius: 50%;
  background: var(--indigo-hi);
  box-shadow: 0 0 8px var(--indigo-glow);
  animation: drift linear infinite;
}
@keyframes drift {
  0% { transform: translateY(0) scale(0.6); opacity: 0; }
  10% { opacity: 0.6; }
  90% { opacity: 0.45; }
  100% { transform: translateY(-112vh) scale(1.15); opacity: 0; }
}
.progress {
  position: fixed;
  top: 0;
  left: 0;
  height: 3px;
  z-index: 60;
  background: linear-gradient(90deg, var(--indigo), var(--indigo-hi));
}

.wrap { max-width: var(--maxw); margin: 0 auto; padding: 0 28px; }

/* nav */
.nav {
  position: sticky;
  top: 0;
  z-index: 50;
  backdrop-filter: saturate(140%) blur(12px);
  background: rgba(237, 241, 246, 0.82);
  border-bottom: 1px solid var(--line);
}
.nav__in { display: flex; align-items: center; gap: 24px; height: 70px; }
.brand { display: flex; align-items: center; gap: 10px; }
.brand__dot {
  width: 11px; height: 11px; border-radius: 50%;
  background: var(--indigo);
  box-shadow: 0 0 0 4px var(--indigo-glow);
}
.brand__t { font-family: var(--font-d); font-weight: 800; font-size: 20px; letter-spacing: -0.02em; }
.brand__t small {
  font-family: var(--font-m); font-weight: 400; font-size: 10.5px;
  color: var(--ink-3); letter-spacing: 0.1em; margin-left: 8px; text-transform: uppercase;
}
.nav__links { display: flex; gap: 24px; margin-left: 16px; font-size: 14.5px; color: var(--ink-2); }
.nav__links a { padding: 5px 0; transition: color 0.2s; position: relative; }
.nav__links a:hover { color: var(--indigo); }
.nav__links a::after {
  content: ''; position: absolute; left: 0; bottom: 0; height: 2px; width: 0;
  background: var(--indigo-hi); border-radius: 2px; transition: width 0.25s;
}
.nav__links a:hover::after { width: 100%; }
.nav__cta { margin-left: auto; }

/* btn */
.btn {
  display: inline-flex; align-items: center; gap: 8px; border-radius: 999px;
  padding: 12px 22px; font-family: var(--font-d); font-size: 14.5px; font-weight: 700;
  cursor: pointer; border: 1px solid transparent;
  transition: transform 0.15s, box-shadow 0.2s, background 0.2s, border-color 0.2s, color 0.2s;
}
.btn--solid { background: var(--indigo); color: #fff; box-shadow: 0 8px 20px -8px rgba(67, 56, 202, 0.55); }
.btn--solid:hover { transform: translateY(-2px); background: #3a2fb0; box-shadow: 0 12px 26px -8px rgba(67, 56, 202, 0.6); }
.btn--ghost { background: var(--surface); color: var(--ink); border-color: var(--line); }
.btn--ghost:hover { border-color: var(--indigo); color: var(--indigo); }
.btn .arr { transition: transform 0.2s; }
.btn:hover .arr { transform: translateX(3px); }

/* hero */
.hero { padding: 74px 0 60px; }
.hero__in { display: grid; grid-template-columns: 1.08fr 0.92fr; gap: 46px; align-items: center; }
.kick {
  display: inline-flex; align-items: center; gap: 10px; font-family: var(--font-m);
  font-size: 12px; letter-spacing: 0.04em; color: var(--indigo); font-weight: 500;
  background: var(--indigo-glow); padding: 7px 14px; border-radius: 999px;
}
.kick i { width: 7px; height: 7px; border-radius: 50%; background: var(--indigo-hi); }
h1.hero__t {
  font-family: var(--font-d); font-weight: 800; font-size: clamp(42px, 5.6vw, 68px);
  line-height: 1.04; letter-spacing: -0.035em; margin: 22px 0 0;
}
h1.hero__t .l2 { color: var(--indigo); display: block; }
.hero__sub { margin-top: 22px; max-width: 520px; font-size: 16.5px; color: var(--ink-2); }
.hero__cta { display: flex; gap: 13px; margin-top: 28px; flex-wrap: wrap; }
.hero__stats {
  display: flex; gap: 0; margin-top: 30px; border: 1px solid var(--line);
  border-radius: var(--rad-sm); overflow: hidden; background: var(--surface);
  max-width: 540px; box-shadow: var(--sh-sm);
}
.hero__stats .st { flex: 1; padding: 13px 16px; border-right: 1px solid var(--line); }
.hero__stats .st:last-child { border-right: none; }
.hero__stats .st .v {
  font-family: var(--font-d); font-weight: 800; font-size: 22px; letter-spacing: -0.03em;
}
.hero__stats .st .v .u {
  font-family: var(--font-m); font-size: 12px; color: var(--ink-3); font-weight: 500; margin-left: 3px;
}
.hero__stats .st .v.in { color: var(--indigo); }
.hero__stats .st .v.out { color: var(--amber); }
.hero__stats .st .k {
  font-family: var(--font-m); font-size: 10.5px; color: var(--ink-3); letter-spacing: 0.04em; margin-top: 3px;
}

/* hero 入场 */
.hero .hin { opacity: 0; transform: translateY(20px); animation: hin 0.8s cubic-bezier(0.2, 0.7, 0.2, 1) forwards; }
.hero__copy .hin:nth-child(1) { animation-delay: 0.05s; }
.hero__copy .hin:nth-child(2) { animation-delay: 0.16s; }
.hero__copy .hin:nth-child(3) { animation-delay: 0.28s; }
.hero__copy .hin:nth-child(4) { animation-delay: 0.4s; }
.hero__copy .hin:nth-child(5) { animation-delay: 0.52s; }
.hero__in > .hin { animation-delay: 0.3s; }
@keyframes hin { to { opacity: 1; transform: none; } }

/* section */
section { padding: 88px 0; }
.sec__head { max-width: 680px; margin-bottom: 42px; }
.sec__tag {
  font-family: var(--font-m); font-size: 12px; color: var(--indigo-hi); letter-spacing: 0.08em;
  display: inline-flex; align-items: center; gap: 9px;
}
.sec__tag::before { content: ''; width: 22px; height: 2px; background: var(--indigo-hi); border-radius: 2px; }
.sec__t {
  font-family: var(--font-d); font-weight: 700; font-size: clamp(27px, 3.4vw, 40px);
  line-height: 1.15; margin-top: 13px; letter-spacing: -0.025em;
}
.sec__d { margin-top: 14px; color: var(--ink-2); font-size: 16px; }

/* 趋势 */
.trend__grid { display: grid; grid-template-columns: 1.55fr 1fr; gap: 22px; }
.card { background: var(--surface); border: 1px solid var(--line); border-radius: var(--rad); box-shadow: var(--sh-sm); }
.chart { padding: 26px 26px 20px; }
.chart__top { display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: 10px; margin-bottom: 10px; }
.chart__legend { display: flex; gap: 18px; font-size: 12.5px; color: var(--ink-2); }
.chart__legend span { display: inline-flex; align-items: center; gap: 7px; }
.chart__legend i { width: 11px; height: 11px; border-radius: 3px; display: inline-block; }
.chart__note { font-size: 11px; color: var(--ink-3); }
.chart__svg { width: 100%; height: auto; display: block; overflow: visible; }
.chart__svg .area { fill: url(#tgrad); opacity: 0; transition: opacity 1s 0.4s ease; }
.chart__svg .line {
  fill: none; stroke-width: 2.6; stroke-linecap: round; stroke-linejoin: round;
}
.chart__svg .line--sales { stroke: var(--indigo); }
.chart__svg .line--out { stroke: var(--amber); stroke-width: 1.8; stroke-dasharray: 5 4; opacity: 0.75; }
.chart__svg.in .area { opacity: 1; }
.chart__svg .dot {
  fill: var(--indigo); opacity: 0; transform-box: fill-box; transform-origin: center; transform: scale(0);
  transition: opacity 0.3s, transform 0.3s;
}
.chart__svg.in .dot { opacity: 1; transform: scale(1); }
.chart__svg .grid { stroke: var(--line); stroke-width: 1; }
.chart__svg .grid.axis { stroke: var(--ink-3); }
.chart__svg .ax { font-family: var(--font-m); font-size: 10px; fill: var(--ink-3); }
.chart__svg .ax--s { fill: var(--indigo); }   /* 左轴刻度:销售额色 */
.chart__svg .ax--o { fill: var(--amber); }    /* 右轴刻度:出库量色 */
.chart__svg .elab { font-family: var(--font-m); font-size: 11px; font-weight: 700; }
.chart__svg .elab--s { fill: var(--indigo); }
.chart__svg .elab--o { fill: var(--amber); }
.chart__scan { animation: scan 7s linear infinite; }
@keyframes scan {
  0% { transform: translateX(0); opacity: 0; }
  8% { opacity: 0.5; }
  92% { opacity: 0.45; }
  100% { transform: translateX(640px); opacity: 0; }
}
.chart__live { fill: #6366f1; transform-box: fill-box; transform-origin: center; animation: lbeat 1.4s ease-in-out infinite; }
@keyframes lbeat { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.4); } }
.chart__live-ring { fill: none; stroke: #6366f1; stroke-width: 2; transform-box: fill-box; transform-origin: center; animation: lring 1.8s ease-out infinite; }
@keyframes lring { 0% { transform: scale(0.6); opacity: 0.8; } 100% { transform: scale(2.6); opacity: 0; } }

/* 运营流水 ticker */
.feed { padding: 20px 22px 16px; }
.feed__head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.feed__head h3 { font-family: var(--font-d); font-size: 15px; font-weight: 700; }
.feed__head .tag { font-family: var(--font-m); font-size: 10.5px; color: var(--indigo); display: inline-flex; align-items: center; gap: 7px; }
.feed__head .tag i { width: 7px; height: 7px; border-radius: 50%; background: var(--indigo-hi); position: relative; }
.feed__head .tag i::after {
  content: ''; position: absolute; inset: -4px; border-radius: 50%;
  border: 1px solid var(--indigo-hi); animation: ping 1.9s infinite;
}
@keyframes ping { 0% { transform: scale(0.6); opacity: 0.9; } 100% { transform: scale(1.9); opacity: 0; } }
.feed__body {
  height: 238px; overflow: hidden;
  -webkit-mask: linear-gradient(180deg, transparent, #000 12%, #000 88%, transparent);
  mask: linear-gradient(180deg, transparent, #000 12%, #000 88%, transparent);
}
.feed__list { animation: tick 26s linear infinite; }
.feed:hover .feed__list { animation-play-state: paused; }
@keyframes tick { to { transform: translateY(-50%); } }
.feed__row {
  display: grid; grid-template-columns: 46px 1fr auto; gap: 10px; padding: 9px 0;
  border-bottom: 1px solid var(--line); align-items: baseline; font-family: var(--font-m); font-size: 12.5px;
}
.feed__t { color: var(--ink-3); }
.feed__e { color: var(--ink); }
.feed__d { font-weight: 600; text-align: right; }
.feed__d.in { color: var(--indigo); }
.feed__d.out { color: var(--amber); }

/* 供应链 */
.chain { display: grid; grid-template-columns: repeat(5, 1fr); gap: 0; position: relative; }
.chain::before {
  content: ''; position: absolute; top: 24px; left: 11%; right: 11%; height: 2px;
  background-image: linear-gradient(90deg, var(--indigo) 0 9px, transparent 9px 18px);
  background-size: 18px 2px;
  background-repeat: repeat-x;
  opacity: 0.4;
  animation: chainflow 1.4s linear infinite; /* 流向:虚线沿供应链由入库流向补货,持续流动 */
}
@keyframes chainflow { to { background-position-x: 18px; } }
.step { text-align: center; padding: 0 10px; position: relative; }
.step__n {
  width: 48px; height: 48px; margin: 0 auto 16px; border-radius: 50%; background: var(--surface);
  border: 2px solid var(--line); display: grid; place-items: center; position: relative; z-index: 1;
  font-family: var(--font-m); font-weight: 600; font-size: 14px; color: var(--ink-2); transition: all 0.35s;
}
.step.is-in .step__n {
  background: var(--indigo); border-color: var(--indigo); color: #fff; transform: translateY(-3px);
  box-shadow: 0 8px 18px -8px rgba(67, 56, 202, 0.6);
}
.step--alert.is-in .step__n { background: var(--amber); border-color: var(--amber); box-shadow: 0 8px 18px -8px rgba(217, 119, 6, 0.6); }
.step__t { font-family: var(--font-d); font-weight: 700; font-size: 16px; margin-bottom: 5px; }
.step__d { font-family: var(--font-m); font-size: 11.5px; color: var(--ink-3); }
.chain__rev {
  margin-top: 36px; text-align: center; font-size: 14px; color: var(--ink-2);
  background: var(--surface-2); border-radius: var(--rad-sm); padding: 15px 20px;
}
.chain__rev b { color: var(--indigo); }

/* 覆盖(真实高德地图由 LocationMap 渲染,这里只保留图例样式) */
.map { overflow: hidden; }
.map__legend { display: flex; gap: 22px; padding: 15px 24px; border-top: 1px solid var(--line); background: var(--surface); font-size: 13px; color: var(--ink-2); }
.map__legend span { display: inline-flex; align-items: center; gap: 8px; }
.map__legend i { width: 11px; height: 11px; border-radius: 50% 50% 50% 2px; background: var(--indigo); transform: rotate(-45deg); }
.map__legend i.inst { background: transparent; border: 2px solid var(--amber); border-radius: 50%; }
.legend__note { margin-left: auto; color: var(--ink-3); font-size: 11px; }

/* 角色 */
.roles__grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 18px; }
.role {
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--rad); padding: 24px;
  display: flex; gap: 15px; align-items: flex-start; box-shadow: var(--sh-sm);
  transition: transform 0.25s, box-shadow 0.25s, border-color 0.25s;
}
.role:hover { transform: translateY(-3px); box-shadow: var(--sh); border-color: var(--indigo-hi); }
.role__ic {
  width: 42px; height: 42px; flex: none; border-radius: 12px; background: var(--indigo-glow);
  display: grid; place-items: center; color: var(--indigo); font-family: var(--font-m); font-weight: 700; font-size: 13px;
}
.role__t { font-family: var(--font-d); font-weight: 700; font-size: 18px; display: flex; align-items: center; gap: 10px; }
.role__t small { font-family: var(--font-m); font-size: 10.5px; color: var(--ink-3); font-weight: 500; letter-spacing: 0.05em; }
.role__d { color: var(--ink-2); font-size: 14px; margin-top: 6px; }

/* 规模 */
.scale {
  background: linear-gradient(135deg, #0c1222, #1e1b4b, #0c1222);
  background-size: 200% 200%;
  color: #e8eaf6; border-radius: var(--rad); padding: 64px 48px;
  animation: scalesheen 14s ease-in-out infinite; /* 暗带缓慢流光,避免滚到底部时页面变死 */
}
@keyframes scalesheen { 0%, 100% { background-position: 0% 50%; } 50% { background-position: 100% 50%; } }
.scale__head { color: #e8eaf6; margin-bottom: 38px; }
.tag--light { color: var(--indigo-hi); }
.t--light { color: #fff; }
.scale__grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 18px; }
.stat__n { font-family: var(--font-d); font-weight: 800; font-size: clamp(38px, 4.8vw, 56px); line-height: 1; color: #fff; letter-spacing: -0.035em; }
.stat__n .u { font-size: 0.42em; color: var(--indigo-hi); margin-left: 4px; }
.stat__rule { width: 32px; height: 2px; background: var(--indigo-hi); margin: 14px 0 0; border-radius: 2px; }
.stat__l { margin-top: 12px; font-size: 13.5px; color: #9aa3bd; max-width: 200px; }

/* footer */
.foot { padding: 56px 0 46px; border-top: 1px solid var(--line); }
.foot__in { display: flex; justify-content: space-between; gap: 30px; flex-wrap: wrap; align-items: flex-end; }
.foot__brand { font-family: var(--font-d); font-weight: 800; font-size: 26px; letter-spacing: -0.025em; display: flex; align-items: center; gap: 10px; }
.foot__brand i { width: 11px; height: 11px; border-radius: 50%; background: var(--indigo); }
.foot__brand small { display: block; font-family: var(--font-m); font-size: 11.5px; color: var(--ink-3); font-weight: 400; margin-top: 6px; letter-spacing: 0.05em; margin-left: 21px; }
.foot__note { font-size: 13px; color: var(--ink-3); max-width: 360px; }
.foot__cta { display: flex; gap: 12px; }

@media (max-width: 940px) {
  .hero__in { grid-template-columns: 1fr; gap: 32px; }
  .trend__grid { grid-template-columns: 1fr; }
  .roles__grid { grid-template-columns: 1fr; }
  .chain { grid-template-columns: repeat(2, 1fr); gap: 24px 0; }
  .chain::before { display: none; }
  .scale__grid { grid-template-columns: repeat(2, 1fr); gap: 30px; }
  .nav__links { display: none; }
  .scale { padding: 48px 28px; }
}
@media (max-width: 560px) {
  .wrap { padding: 0 18px; }
  .hero { padding: 48px 0 40px; }
  section { padding: 60px 0; }
  .hero__cta { flex-direction: column; align-items: stretch; }
  .btn { justify-content: center; }
  .hero__stats { flex-wrap: wrap; }
  .hero__stats .st { flex: 1 1 50%; border-bottom: 1px solid var(--line); }
  .chain { grid-template-columns: 1fr; }
  .scale__grid { grid-template-columns: 1fr; }
}
</style>
