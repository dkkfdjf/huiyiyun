<template>
  <div class="card">
    <div class="card__head">
      <h3>{{ title }}</h3>
      <span v-if="more" class="more" @click="emit('more')">{{ more }}</span>
    </div>
    <div class="card__body tbl">
      <table>
        <thead>
          <tr>
            <th>药品</th>
            <th>库存水位</th>
            <th class="r">状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(r, i) in rows" :key="i" :class="{ alertrow: st(r).key !== 'ok' }">
            <td>
              <div class="drug">
                <div class="drug__ic">{{ (r.drugName || '药').slice(0, 1) }}</div>
                <div>
                  <b>{{ r.drugName }}</b>
                  <small v-if="sub(r)">{{ sub(r) }}</small>
                </div>
              </div>
            </td>
            <td>
              <div class="lvl">
                <div class="lvl__top">
                  <b class="lvl__qty" :class="st(r).key">{{ r.stockQty }}</b>
                  <span class="lvl__th">安全线 {{ r.threshold }}</span>
                </div>
                <div class="lvl__track">
                  <i class="lvl__fill" :style="{ width: lvl(r).fill + '%', background: st(r).color }"></i>
                  <i class="lvl__mark" :style="{ left: lvl(r).mark + '%' }" :title="'安全线 ' + r.threshold"></i>
                </div>
              </div>
            </td>
            <td class="r">
              <span class="pillst" :class="st(r).key">{{ st(r).label }}</span>
              <a v-if="remindable && r.id" class="remind" @click="emit('remind', r)">提醒补货</a>
            </td>
          </tr>
          <tr v-if="!rows.length"><td colspan="3" class="empty">暂无预警</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { stockState } from '../utils/format'

defineProps({
  title: { type: String, default: '库存预警' },
  more: { type: String, default: '查看全部 ›' },
  rows: { type: Array, default: () => [] }, // {drugName,spec,stockQty,threshold,locationName?,companyName?,id?}
  remindable: { type: Boolean, default: false } // 管理员库存页传 true → 每行显示「提醒补货」
})
// 点「查看全部」交由父组件决定去向(管理员→库存预警页;详情页传 null 隐藏)
const emit = defineEmits(['more', 'remind'])
const st = (r) => stockState(r.stockQty, r.threshold)
// 副标题:规格 · 归属公司 · 网点(按存在性拼接,空值跳过)
const sub = (r) => [r.spec, r.companyName, r.locationName].filter(Boolean).join(' · ')
// 水位条:库存填充 + 虚线阈值标记。max 取 max(库存,阈值)×1.25 留余量,阈值不贴边、两者皆可见。
const lvl = (r) => {
  const q = Number(r.stockQty) || 0
  const th = Number(r.threshold) || 1
  const max = (Math.max(q, th) || 1) * 1.25
  return { fill: Math.min(100, Math.round((q / max) * 100)), mark: Math.min(100, Math.round((th / max) * 100)) }
}
</script>

<style scoped>
.card { background: var(--surface); border: 1px solid var(--line); border-radius: var(--rad); box-shadow: var(--sh-sm); }
.card__head { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 15px 20px; border-bottom: 1px solid var(--line-2); }
.card__head h3 { font-family: var(--font-d); font-weight: 700; font-size: 15px; }
.card__head .more { font-family: var(--font-m); font-size: 11px; color: var(--ink-3); cursor: pointer; }
.card__head .more:hover { color: var(--indigo); }
.tbl { padding: 6px 20px 14px; }
table { width: 100%; border-collapse: collapse; }
th { font-family: var(--font-m); font-size: 10.5px; color: var(--ink-3); letter-spacing: 0.04em; text-align: left; font-weight: 500; padding: 0 14px 10px; }
th.r, td.r { text-align: right; }
td { padding: 11px 14px; border-top: 1px solid var(--line-2); font-size: 13px; }
tbody tr { transition: background 0.15s; }
tbody tr:hover { background: var(--field); }
.drug { display: flex; align-items: center; gap: 10px; }
.drug__ic { width: 30px; height: 30px; border-radius: 8px; background: var(--indigo-glow); display: grid; place-items: center; color: var(--indigo); font-family: var(--font-m); font-weight: 700; font-size: 11px; flex: none; }
.drug b { font-weight: 600; font-size: 13px; }
.drug small { display: block; font-family: var(--font-m); font-size: 10.5px; color: var(--ink-3); }
.lvl { min-width: 140px; }
.lvl__top { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; margin-bottom: 5px; }
.lvl__qty { font-family: var(--font-m); font-weight: 700; font-size: 14px; }
.lvl__qty.lo { color: var(--red); }
.lvl__qty.mid { color: var(--amber); }
.lvl__qty.ok { color: var(--green); }
.lvl__th { font-family: var(--font-m); font-size: 10.5px; color: var(--ink-3); }
.lvl__track { position: relative; height: 8px; border-radius: 5px; background: var(--line-2); }
.lvl__fill { position: absolute; left: 0; top: 0; height: 100%; border-radius: 5px; transition: width 0.4s ease; }
.lvl__mark { position: absolute; top: -3px; bottom: -3px; width: 0; border-left: 2px dashed var(--ink-3); }
.pillst { font-family: var(--font-m); font-size: 10.5px; padding: 3px 9px; border-radius: 6px; font-weight: 600; }
.pillst.lo { background: rgba(220, 42, 69, 0.1); color: var(--red); }
.pillst.mid { background: var(--amber-soft); color: var(--amber); }
.pillst.ok { background: rgba(14, 156, 143, 0.1); color: var(--green); }
.remind { display: inline-block; margin-left: 10px; font-family: var(--font-m); font-size: 11px; color: var(--indigo); cursor: pointer; }
.remind:hover { text-decoration: underline; }
.alertrow .drug__ic { background: rgba(220, 42, 69, 0.1); color: var(--red); }
.empty { text-align: center; color: var(--ink-3); padding: 18px 0; }
</style>
