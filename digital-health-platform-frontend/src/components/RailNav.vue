<template>
  <nav class="rail">
    <div class="rail__mk" title="慧医云">
      <svg viewBox="0 0 32 32" fill="none" stroke="#fff" stroke-width="1.8" stroke-linejoin="round">
        <path d="M16 4l10.4 6v12L16 28 5.6 22V10z" fill="rgba(255,255,255,.16)" />
        <circle cx="16" cy="16" r="1.6" fill="#fff" stroke="none" />
        <circle cx="16" cy="8.4" r="1.5" fill="#fff" stroke="none" />
        <circle cx="22.8" cy="19.8" r="1.5" fill="#fff" stroke="none" />
        <circle cx="9.2" cy="19.8" r="1.5" fill="#fff" stroke="none" />
        <path d="M16 8.4V16M16 16l6.8 3.8M16 16l-6.8 3.8" />
      </svg>
    </div>
    <div class="rail__nav">
      <template v-for="(it, idx) in items" :key="it.to || it.group || ('d' + idx)">
        <div v-if="it.divider" class="rail__sep"></div>

        <!-- 可展开分组:点头部展开/收起,默认展开含当前路由的组 -->
        <div v-else-if="it.group" class="grp" :class="{ open: isOpen(it) }">
          <button
            class="rit rit--grp"
            type="button"
            :class="{ on: isOpen(it) }"
            :title="it.group"
            :aria-expanded="isOpen(it)"
            @click="toggleGroup(it.group)"
          >
            <span class="ico" v-html="it.svg"></span>
            <span>{{ it.group }}</span>
            <i class="chev" v-html="CHEV"></i>
          </button>
          <div v-show="isOpen(it)" class="grp__kids">
            <router-link
              v-for="c in it.children"
              :key="c.to"
              class="rit rit--kid"
              :to="c.to"
              :class="{ on: route.path === c.to }"
              :title="c.label"
            >
              <span class="ico" v-html="c.svg"></span>
              <span>{{ c.label }}</span>
            </router-link>
          </div>
        </div>

        <!-- 普通叶子 -->
        <router-link
          v-else-if="!it.soon"
          class="rit"
          :to="it.to"
          :class="{ on: route.path === it.to && !hasOpen }"
          :title="it.label"
        >
          <span class="ico" v-html="it.svg"></span>
          <span>{{ it.label }}</span>
        </router-link>
        <div v-else class="rit soon" :title="`${it.label}(即将上线)`">
          <span class="ico" v-html="it.svg"></span>
          <span>{{ it.label }}</span>
        </div>
      </template>
    </div>
    <div class="rail__bot">
      <router-link
        v-if="user.role === ROLE.ADMIN"
        class="rit"
        to="/admin/system"
        :class="{ on: route.path === '/admin/system' && !hasOpen }"
        title="系统监控"
      >
        <span class="ico" v-html="SYS"></span>
        <span>系统</span>
      </router-link>
    </div>
  </nav>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore, ROLE } from '../stores/user'

const route = useRoute()
const user = useUserStore()

const DASH = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="3" y="3" width="7" height="9" rx="1.5" /><rect x="14" y="3" width="7" height="5" rx="1.5" /><rect x="14" y="12" width="7" height="9" rx="1.5" /><rect x="3" y="16" width="7" height="5" rx="1.5" /></svg>'
const DRUG = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="3" y="8" width="18" height="8" rx="4" transform="rotate(-35 12 12)" /><path d="M9.5 7.5l5 9" transform="rotate(-35 12 12)" /></svg>'
const STOCK = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M3 7l9-4 9 4v10l-9 4-9-4z" /><path d="M3 7l9 4 9-4M12 11v10" /></svg>'
const FLOW = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="6" cy="6" r="2.4" /><circle cx="18" cy="18" r="2.4" /><path d="M8 7c5 0 8 3 8 8" /></svg>'
const LOC = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M5 9l7-5 7 5v9a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1z" /><path d="M9 19v-6h6v6" /></svg>'
const MSG = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M4 5h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H9l-4 4v-4H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z" /><path d="M8 10h8M8 13h5" /></svg>'
const INST = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M5 9h14v10a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1z" /><path d="M12 6v6M9 9h6" /><path d="M3 9V7a1 1 0 0 1 1-1h16a1 1 0 0 1 1 1v2" /></svg>'
const DOC = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="9" cy="8" r="3.2" /><path d="M3.5 20a5.5 5.5 0 0 1 11 0" /><path d="M17 11l2 2 3.5-3.5" /></svg>'
const PHARMA = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M3 21h18" /><path d="M5 21V8l5-3v16" /><path d="M10 21V5l9 3v13" /><path d="M8 10h.01M8 13h.01M14 10h.01M14 13h.01" /></svg>'
const CITY = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M12 21s-7-6.5-7-11a7 7 0 0 1 14 0c0 4.5-7 11-7 11z" /><circle cx="12" cy="10" r="2.5" /></svg>'
const DEPT = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="3" y="3" width="7" height="7" rx="1.5" /><rect x="14" y="3" width="7" height="7" rx="1.5" /><rect x="3" y="14" width="7" height="7" rx="1.5" /><rect x="14" y="14" width="7" height="7" rx="1.5" /></svg>'
const POLICY = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M7 3h7l4 4v14a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z" /><path d="M14 3v4h4" /><path d="M9 12h6M9 16h4" /></svg>'
const MATERIAL = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="3" y="4" width="18" height="4" rx="1" /><path d="M5 8v11a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8" /><path d="M10 12h4" /></svg>'
const USER = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="9" cy="8" r="3.2" /><path d="M3.5 20a5.5 5.5 0 0 1 11 0" /><path d="M17 6.5l1.5 1.5L21 5.5" /></svg>'
const LOG = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M6 3h9l4 4v14a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z" /><path d="M15 3v4h4" /><path d="M8.5 12h7M8.5 16h5" /></svg>'
const SYS = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="12" cy="12" r="3" /><path d="M12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1" /></svg>'
const KB = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M4 5h6a2 2 0 0 1 2 2v13a1.5 1.5 0 0 0-1.5-1.5H4z" /><path d="M20 5h-6a2 2 0 0 0-2 2v13a1.5 1.5 0 0 1 1.5-1.5H20z" /></svg>'
// 分组头部图标(管理员侧)
const RES = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M3 21h18" /><path d="M5 21V8l5-3v16" /><path d="M10 21V5l9 3v13" /><path d="M7 10h.01M7 13h.01M13 9h.01M13 12h.01M16 9h.01M16 12h.01" /></svg>'
const MAP = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M9 4L3 6v14l6-2 6 2 6-2V4l-6 2-6-2z" /><path d="M9 4v14M15 6v14" /></svg>'
const MEGA = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M4 9v6h3l8 4V5L7 9H4z" /><path d="M17 8a4 4 0 0 1 0 8" /></svg>'
const SHIELD = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M12 3l8 3v5c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V6l8-3z" /><path d="M9 12l2 2 4-4" /></svg>'
const CHEV = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M6 9l6 6 6-6" /></svg>'

// 管理员 rail:平铺项只放顶/底边界,中间 4 组连续,避免 平铺/分组 交错显得混乱。
// 顶部:工作台 / 药企(常用直达);中间:医疗资源 ▾ / 地域覆盖 ▾ / 内容发布 ▾ / 平台治理 ▾;底部:反馈。
const ADMIN = [
  { to: '/admin', label: '工作台', svg: DASH },
  { to: '/admin/companies', label: '药企', svg: PHARMA },
  { group: '医疗资源', svg: RES, children: [
    { to: '/admin/institutions', label: '机构', svg: INST },
    { to: '/admin/departments', label: '科室', svg: DEPT },
    { to: '/admin/doctors', label: '医师', svg: DOC }
  ] },
  { group: '地域覆盖', svg: MAP, children: [
    { to: '/admin/locations', label: '网点', svg: LOC },
    { to: '/admin/stocks', label: '库存', svg: STOCK },
    { to: '/admin/cities', label: '城市', svg: CITY }
  ] },
  { group: '内容发布', svg: MEGA, children: [
    { to: '/admin/materials', label: '材料', svg: MATERIAL },
    { to: '/admin/policies', label: '公告', svg: POLICY }
  ] },
  { group: '平台治理', svg: SHIELD, children: [
    { to: '/admin/users', label: '账号', svg: USER },
    { to: '/admin/logs', label: '日志', svg: LOG },
    { to: '/admin/kb', label: '知识库', svg: KB }
  ] },
  { to: '/admin/demands', label: '反馈', svg: MSG }
]
// 公司 rail:工作台/药品 已通,库存/网点 随 M4-part2/M6 开通
const COMPANY = [
  { to: '/company', label: '工作台', svg: DASH },
  { to: '/company/drugs', label: '药品', svg: DRUG },
  { to: '/company/stocks', label: '库存', svg: STOCK },
  { to: '/company/inventory', label: '流向', svg: FLOW },
  { to: '/company/locations', label: '网点', svg: LOC },
  { to: '/company/demands', label: '反馈', svg: MSG },
  { to: '/company/policies', label: '公告', svg: POLICY }
]

// 医师 rail:工作台 / 临床反馈 / 药企公告 / 必备材料
const DOCTOR = [
  { to: '/doctor', label: '工作台', svg: DASH },
  { to: '/doctor/demands', label: '反馈', svg: MSG },
  { to: '/doctor/policies', label: '公告', svg: POLICY },
  { to: '/doctor/materials', label: '材料', svg: MATERIAL }
]

// 机构 rail:工作台 / 本院科室 / 本院医师 / 医师公告 / 可售药品 / 药企公告 / 必备材料
const INSTITUTION = [
  { to: '/institution', label: '工作台', svg: DASH },
  { to: '/institution/departments', label: '科室', svg: DEPT },
  { to: '/institution/doctors', label: '医师', svg: DOC },
  { to: '/institution/demands', label: '反馈', svg: MSG },
  { to: '/institution/announce', label: '通知', svg: MEGA },
  { to: '/institution/drugs', label: '药品', svg: DRUG },
  { to: '/institution/policies', label: '公告', svg: POLICY },
  { to: '/institution/materials', label: '材料', svg: MATERIAL }
]

// 游客 rail:只读浏览(工作台 + 7 个浏览目的地;无反馈/库存/账号/日志/知识库管理等写或敏感页)
const GUEST = [
  { to: '/guest', label: '工作台', svg: DASH },
  { to: '/guest/companies', label: '药企', svg: PHARMA },
  { to: '/guest/institutions', label: '机构', svg: INST },
  { to: '/guest/departments', label: '科室', svg: DEPT },
  { to: '/guest/doctors', label: '医师', svg: DOC },
  { to: '/guest/locations', label: '网点', svg: LOC },
  { to: '/guest/materials', label: '材料', svg: MATERIAL },
  { to: '/guest/policies', label: '公告', svg: POLICY }
]

const items = computed(() =>
  user.role === ROLE.GUEST ? GUEST
    : user.role === ROLE.COMPANY ? COMPANY
    : user.role === ROLE.DOCTOR ? DOCTOR
    : user.role === ROLE.INSTITUTION ? INSTITUTION
    : ADMIN
)

/* —— 可展开分组(手风琴):同一时间只展开一个组——展开新的自动收起其他;路由进入某组时自动展开该组 —— */
const open = ref(new Set())
function expandActiveGroup() {
  // 路由进入某组 → 只展开该组;平铺路由(工作台/药企/反馈,不在任何组)→ 收起全部,让平铺项独占高亮
  const active = items.value.find((it) => it.group && it.children?.some((c) => route.path === c.to))
  open.value = active ? new Set([active.group]) : new Set()
}
watch(() => route.path, expandActiveGroup, { immediate: true })
function toggleGroup(g) {
  // 手风琴:展开 g 并收起其他;若 g 已展开则收起(全闭合)
  open.value = open.value.has(g) ? new Set() : new Set([g])
}
const isOpen = (it) => open.value.has(it.group)
// 有组展开时,平铺项(工作台/药企/反馈/系统)让位不高亮,避免与展开组头双高亮;收起后平铺项恢复高亮
const hasOpen = computed(() => open.value.size > 0)
</script>

<style scoped>
.rail {
  background: var(--field);
  border-right: 1px solid var(--line);
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 0 14px;
  z-index: 30;
}
.rail__mk {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: #fff;
  flex: none;
  margin-bottom: 16px;
  background: linear-gradient(150deg, var(--indigo-hi), var(--indigo));
  box-shadow: 0 10px 22px -10px rgba(67, 56, 202, 0.6);
}
.rail__mk svg { width: 24px; height: 24px; }
.rail__nav { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px; width: 100%; overflow-y: auto; }
.rit {
  position: relative;
  width: 68px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  cursor: pointer;
  color: var(--ink-2);
  text-decoration: none;
  transition: background 0.18s, color 0.18s;
}
.rit .ico { display: flex; }
.rit :deep(svg) { width: 20px; height: 20px; flex: none; }
.rit span { font-size: 10.5px; }
.rit:hover { background: var(--surface); color: var(--ink); }
.rit.on { color: var(--indigo); background: var(--indigo-soft); }
.rit.on::before {
  content: '';
  position: absolute;
  left: -1px;
  top: 14px;
  bottom: 14px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--indigo);
}
.rit.soon { opacity: 0.42; cursor: not-allowed; }
.rit.soon:hover { background: transparent; color: var(--ink-2); }
.rail__sep { width: 26px; height: 1px; background: var(--line); opacity: 0.55; margin: 6px auto; }
.rail__bot { margin-top: auto; display: flex; flex-direction: column; align-items: center; gap: 8px; }

/* —— 可展开分组 —— */
.grp { width: 100%; display: flex; flex-direction: column; align-items: center; }
.rit--grp { background: none; border: 0; padding: 0; font-family: inherit; }
.rit--grp .chev {
  position: absolute; right: 5px; top: 50%; transform: translateY(-50%);
  display: flex; opacity: 0.45; transition: transform 0.2s, opacity 0.2s;
}
.rit--grp .chev :deep(svg) { width: 11px; height: 11px; }
.rit--grp:hover .chev { opacity: 0.8; }
.grp.open .chev { transform: translateY(-50%) rotate(180deg); }
.grp__kids {
  width: 100%; display: flex; flex-direction: column; align-items: center;
  gap: 2px; margin-top: 2px; padding-bottom: 4px;
}
.rit--kid { width: 60px; height: 46px; gap: 3px; }
.rit--kid :deep(svg) { width: 16px; height: 16px; }
.rit--kid span { font-size: 10px; }
/* 非选中子项左侧靛蓝细竖线,提示归属上一级分组;选中态沿用 .rit.on 的左侧条 */
.rit--kid:not(.on)::before {
  content: ''; position: absolute; left: 12px; top: 8px; bottom: 8px;
  width: 2px; border-radius: 2px; background: var(--indigo-soft);
}

@media (max-width: 1040px) {
  .rit { width: 52px; }
  .rit span { display: none; }
}
@media (max-width: 680px) {
  .rit span { display: none; }
}
</style>
