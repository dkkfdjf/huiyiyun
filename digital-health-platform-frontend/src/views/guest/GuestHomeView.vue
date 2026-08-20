<template>
  <div class="guest-home">
    <!-- 体验横幅 -->
    <section class="hero">
      <div class="hero__txt">
        <span class="badge"><i class="dot"></i>只读体验</span>
        <h1>慧医云 · 平台概览</h1>
        <p>无需账号即可浏览平台真实数据:药企、医疗机构、医师、销售网点、必备材料与政策公告。联系方式、证件号等敏感字段已自动脱敏,写操作仅登录角色可用。</p>
        <div class="hero__acts">
          <router-link to="/login" class="cta">登录体验完整功能 →</router-link>
          <span class="hint">有疑问?点右下角 <b>AI 助手</b> 直接对话</span>
        </div>
      </div>
      <div class="hero__deco" aria-hidden="true">
        <svg viewBox="0 0 200 160" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round">
          <path d="M100 18l70 40v44l-70 40-70-40V58z" opacity="0.5" />
          <path d="M100 38l52 30v24l-52 30-52-30V68z" opacity="0.7" />
          <circle cx="100" cy="80" r="3.5" fill="currentColor" stroke="none" />
        </svg>
      </div>
    </section>

    <!-- 规模 KPI(免登录公开概览,失败静默维持 0) -->
    <section class="kpis">
      <KpiCard label="在管药品" :value="scale.drugCount" unit="+" tone="in" />
      <KpiCard label="销售网点" :value="scale.locationCount" tone="net" />
      <KpiCard label="医疗机构" :value="scale.institutionCount" tone="out" />
      <KpiCard label="注册医师" :value="scale.doctorCount" unit="+" tone="in" />
    </section>

    <!-- 浏览入口 -->
    <section class="browse">
      <h2>浏览目录</h2>
      <div class="grid">
        <router-link v-for="c in cards" :key="c.to" :to="c.to" class="card">
          <span class="card__ico" v-html="c.svg"></span>
          <span class="card__t">{{ c.label }}</span>
          <span class="card__d">{{ c.desc }}</span>
          <span class="card__go">进入 →</span>
        </router-link>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import KpiCard from '../../components/KpiCard.vue'
import { getPublicStats } from '../../api/public'

const stats = ref(null)
// 免登录公开概览;失败静默(落地页同源),KPI 维持 0 而非弹错
const scale = computed(() => stats.value?.scale || { drugCount: 0, locationCount: 0, institutionCount: 0, doctorCount: 0 })
onMounted(async () => {
  try {
    stats.value = await getPublicStats()
  } catch (e) {
    /* 静默:后端短暂不可用不弹错 */
  }
})

const PHARMA = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M3 21h18" /><path d="M5 21V8l5-3v16" /><path d="M10 21V5l9 3v13" /><path d="M8 10h.01M8 13h.01M14 10h.01M14 13h.01" /></svg>'
const INST = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M5 9h14v10a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1z" /><path d="M12 6v6M9 9h6" /><path d="M3 9V7a1 1 0 0 1 1-1h16a1 1 0 0 1 1 1v2" /></svg>'
const DOC = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="9" cy="8" r="3.2" /><path d="M3.5 20a5.5 5.5 0 0 1 11 0" /><path d="M17 11l2 2 3.5-3.5" /></svg>'
const LOC = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M5 9l7-5 7 5v9a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1z" /><path d="M9 19v-6h6v6" /></svg>'
const MATERIAL = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="3" y="4" width="18" height="4" rx="1" /><path d="M5 8v11a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8" /><path d="M10 12h4" /></svg>'
const POLICY = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M7 3h7l4 4v14a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z" /><path d="M14 3v4h4" /><path d="M9 12h6M9 16h4" /></svg>'

const cards = [
  { to: '/guest/companies', label: '药企', desc: '入驻药企档案与状态', svg: PHARMA },
  { to: '/guest/institutions', label: '医疗机构', desc: '联网医院与诊所', svg: INST },
  { to: '/guest/doctors', label: '医师', desc: '执业医师名录', svg: DOC },
  { to: '/guest/locations', label: '销售网点', desc: '终端铺货网点', svg: LOC },
  { to: '/guest/materials', label: '必备材料', desc: '基药与必备目录', svg: MATERIAL },
  { to: '/guest/policies', label: '政策公告', desc: '药企最新公告', svg: POLICY }
]
</script>

<style scoped>
.guest-home { display: flex; flex-direction: column; gap: 22px; }

/* —— 横幅 —— */
.hero {
  position: relative; overflow: hidden;
  background: linear-gradient(135deg, var(--indigo-soft), var(--surface));
  border: 1px solid var(--line);
  border-radius: var(--rad);
  padding: 30px 32px;
  display: flex; align-items: center; justify-content: space-between; gap: 24px;
  box-shadow: var(--sh-sm);
}
.hero__txt { max-width: 640px; }
.badge {
  display: inline-flex; align-items: center; gap: 6px;
  font-family: var(--font-m); font-size: 11px; letter-spacing: 0.04em;
  color: var(--indigo); background: #fff; border: 1px solid var(--indigo-soft);
  padding: 4px 10px; border-radius: 999px;
}
.badge .dot { width: 6px; height: 6px; border-radius: 50%; background: var(--green); animation: pulse 2s infinite; }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.35; } }
.hero h1 { font-family: var(--font-d); font-size: 30px; font-weight: 800; letter-spacing: -0.02em; margin: 14px 0 10px; color: var(--ink); }
.hero p { font-size: 14px; line-height: 1.7; color: var(--ink-2); margin: 0; }
.hero__acts { display: flex; align-items: center; gap: 18px; margin-top: 18px; flex-wrap: wrap; }
.cta {
  display: inline-block; text-decoration: none;
  background: var(--indigo); color: #fff; font-weight: 600; font-size: 13px;
  padding: 10px 18px; border-radius: 10px; transition: transform 0.15s, box-shadow 0.15s;
  box-shadow: 0 8px 20px -10px rgba(67, 56, 202, 0.6);
}
.cta:hover { transform: translateY(-1px); }
.hint { font-size: 12.5px; color: var(--ink-3); }
.hint b { color: var(--indigo); font-weight: 600; }
.hero__deco { color: var(--indigo); flex: none; opacity: 0.85; }
.hero__deco svg { width: 180px; height: 144px; animation: spin 40s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* —— KPI 行 —— */
.kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }

/* —— 浏览网格 —— */
.browse h2 { font-family: var(--font-d); font-size: 16px; font-weight: 700; color: var(--ink); margin: 0 0 14px; }
.grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
.card {
  position: relative; display: flex; flex-direction: column; gap: 6px;
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--rad);
  padding: 18px 20px; text-decoration: none; box-shadow: var(--sh-sm);
  transition: transform 0.16s, border-color 0.16s, box-shadow 0.16s;
}
.card:hover { transform: translateY(-2px); border-color: var(--indigo-soft); box-shadow: 0 14px 30px -18px rgba(67, 56, 202, 0.4); }
.card__ico { display: flex; color: var(--indigo); background: var(--indigo-soft); width: 40px; height: 40px; border-radius: 11px; align-items: center; justify-content: center; margin-bottom: 6px; }
.card__ico :deep(svg) { width: 22px; height: 22px; }
.card__t { font-family: var(--font-d); font-weight: 700; font-size: 15px; color: var(--ink); }
.card__d { font-size: 12.5px; color: var(--ink-3); }
.card__go { font-size: 12px; color: var(--indigo); font-weight: 600; margin-top: 6px; }

@media (max-width: 1040px) {
  .kpis { grid-template-columns: repeat(2, 1fr); }
  .grid { grid-template-columns: repeat(2, 1fr); }
  .hero__deco { display: none; }
}
@media (max-width: 680px) {
  .kpis, .grid { grid-template-columns: 1fr; }
}
</style>
