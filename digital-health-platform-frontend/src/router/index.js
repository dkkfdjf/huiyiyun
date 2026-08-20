import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn, getProfile } from '../utils/auth'
import { ROLE } from '../stores/user'

// 角色主页:管理员 / 公司 / 机构 / 医师 / 游客
const roleHome = (role) => ({ [ROLE.ADMIN]: '/admin', [ROLE.COMPANY]: '/company', [ROLE.INSTITUTION]: '/institution', [ROLE.DOCTOR]: '/doctor', [ROLE.GUEST]: '/guest' }[role] ?? '/admin')

const routes = [
  {
    path: '/',
    name: 'landing',
    component: () => import('../views/landing/LandingView.vue')
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/login/LoginView.vue')
  },
  {
    path: '/admin',
    component: () => import('../layouts/WorkbenchLayout.vue'),
    meta: { auth: true, roles: [ROLE.ADMIN] },
    children: [
      { path: '', name: 'admin-home', component: () => import('../views/admin/DashboardView.vue') },
      { path: 'institutions', name: 'admin-institutions', component: () => import('../views/admin/InstitutionView.vue') },
      { path: 'departments', name: 'admin-departments', component: () => import('../views/admin/DepartmentView.vue') },
      { path: 'doctors', name: 'admin-doctors', component: () => import('../views/admin/DoctorView.vue') },
      { path: 'companies', name: 'admin-companies', component: () => import('../views/admin/CompanyView.vue') },
      { path: 'locations', name: 'admin-locations', component: () => import('../views/admin/LocationView.vue') },
      { path: 'stocks', name: 'admin-stocks', component: () => import('../views/admin/StockView.vue') },
      { path: 'demands', name: 'admin-demands', component: () => import('../views/admin/DemandView.vue') },
      { path: 'policies', name: 'admin-policies', component: () => import('../views/policy/PolicyView.vue') },
      { path: 'materials', name: 'admin-materials', component: () => import('../views/material/MaterialView.vue') },
      { path: 'users', name: 'admin-users', component: () => import('../views/admin/UserView.vue') },
      { path: 'logs', name: 'admin-logs', component: () => import('../views/admin/LogView.vue') },
      { path: 'kb', name: 'admin-kb', component: () => import('../views/admin/KbView.vue') },
      { path: 'cities', name: 'admin-cities', component: () => import('../views/admin/CityView.vue') },
      { path: 'system', name: 'admin-system', component: () => import('../views/admin/SystemView.vue') }
    ]
  },
  {
    path: '/company',
    component: () => import('../layouts/WorkbenchLayout.vue'),
    meta: { auth: true, roles: [ROLE.COMPANY] },
    children: [
      { path: '', name: 'company-home', component: () => import('../views/company/CompanyHome.vue') },
      { path: 'drugs', name: 'company-drugs', component: () => import('../views/company/DrugManage.vue') },
      { path: 'stocks', name: 'company-stocks', component: () => import('../views/company/StockManage.vue') },
      { path: 'inventory', name: 'company-inventory', component: () => import('../views/company/InventoryView.vue') },
      { path: 'locations', name: 'company-locations', component: () => import('../views/company/LocationView.vue') },
      { path: 'demands', name: 'company-demands', component: () => import('../views/company/DemandView.vue') },
      { path: 'policies', name: 'company-policies', component: () => import('../views/policy/PolicyView.vue') }
    ]
  },
  {
    path: '/doctor',
    component: () => import('../layouts/WorkbenchLayout.vue'),
    meta: { auth: true, roles: [ROLE.DOCTOR] },
    children: [
      { path: '', name: 'doctor-home', component: () => import('../views/doctor/DoctorHome.vue') },
      { path: 'demands', name: 'doctor-demands', component: () => import('../views/doctor/DoctorDemand.vue') },
      { path: 'policies', name: 'doctor-policies', component: () => import('../views/policy/PolicyView.vue') },
      { path: 'materials', name: 'doctor-materials', component: () => import('../views/material/MaterialView.vue') }
    ]
  },
  {
    path: '/institution',
    component: () => import('../layouts/WorkbenchLayout.vue'),
    meta: { auth: true, roles: [ROLE.INSTITUTION] },
    children: [
      { path: '', name: 'institution-home', component: () => import('../views/institution/InstitutionHome.vue') },
      { path: 'departments', name: 'institution-departments', component: () => import('../views/institution/InstitutionDepartmentView.vue') },
      { path: 'doctors', name: 'institution-doctors', component: () => import('../views/institution/InstitutionDoctorView.vue') },
      { path: 'demands', name: 'institution-demands', component: () => import('../views/institution/InstitutionDemandView.vue') },
      { path: 'announce', name: 'institution-announce', component: () => import('../views/institution/InstitutionAnnouncementView.vue') },
      { path: 'drugs', name: 'institution-drugs', component: () => import('../views/institution/InstitutionDrugView.vue') },
      { path: 'policies', name: 'institution-policies', component: () => import('../views/policy/PolicyView.vue') },
      { path: 'materials', name: 'institution-materials', component: () => import('../views/material/MaterialView.vue') }
    ]
  },
  // 游客只读体验:复用 admin/material/policy 视图组件(写按钮由各视图 isGuest gate),
  // 路由独立成树(/guest/*)便于权限隔离 + RailNav/KbActionParser 对齐。GuestHomeView 为专属概览。
  {
    path: '/guest',
    component: () => import('../layouts/WorkbenchLayout.vue'),
    meta: { auth: true, roles: [ROLE.GUEST] },
    children: [
      { path: '', name: 'guest-home', component: () => import('../views/guest/GuestHomeView.vue') },
      { path: 'companies', name: 'guest-companies', component: () => import('../views/admin/CompanyView.vue') },
      { path: 'institutions', name: 'guest-institutions', component: () => import('../views/admin/InstitutionView.vue') },
      { path: 'departments', name: 'guest-departments', component: () => import('../views/admin/DepartmentView.vue') },
      { path: 'doctors', name: 'guest-doctors', component: () => import('../views/admin/DoctorView.vue') },
      { path: 'locations', name: 'guest-locations', component: () => import('../views/admin/LocationView.vue') },
      { path: 'materials', name: 'guest-materials', component: () => import('../views/material/MaterialView.vue') },
      { path: 'policies', name: 'guest-policies', component: () => import('../views/policy/PolicyView.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({ history: createWebHistory(), routes, scrollBehavior: () => ({ top: 0 }) })

router.beforeEach((to) => {
  if (to.meta.auth && !isLoggedIn()) return '/login'
  const role = getProfile()?.role
  if (to.path === '/login' && isLoggedIn()) return roleHome(role)
  // 路由要求角色,但当前角色不符 → 回各自主页(越权拦截)
  if (to.meta.roles && !to.meta.roles.includes(role)) return roleHome(role)
  return true
})

export default router
