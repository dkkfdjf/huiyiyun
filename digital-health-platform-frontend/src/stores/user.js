import { defineStore } from 'pinia'
import { getToken, setToken, clearToken, getProfile } from '../utils/auth'

// 角色常量(与后端 RoleConstants 对齐);代码里用 ROLE.X 代替裸数字
export const ROLE = { ADMIN: 0, COMPANY: 1, INSTITUTION: 2, DOCTOR: 3, GUEST: 4 }
const ROLE_LABEL = { [ROLE.ADMIN]: '管理员', [ROLE.COMPANY]: '药企', [ROLE.INSTITUTION]: '医疗机构', [ROLE.DOCTOR]: '医师', [ROLE.GUEST]: '游客' }

export const useUserStore = defineStore('user', {
  state: () => {
    const p = getProfile()
    return {
      token: getToken(),
      userId: p?.userId ?? null,
      username: p?.username ?? '',
      role: p?.role ?? null,
      realName: p?.realName ?? p?.username ?? '',
      companyId: p?.companyId ?? null,
      institutionId: p?.institutionId ?? null,
      doctorId: p?.doctorId ?? null
    }
  },
  getters: {
    loggedIn: (s) => !!s.token,
    roleLabel: (s) => ROLE_LABEL[s.role] || '用户',
    isGuest: (s) => s.role === ROLE.GUEST,
    homePath: (s) => ({ [ROLE.ADMIN]: '/admin', [ROLE.COMPANY]: '/company', [ROLE.INSTITUTION]: '/institution', [ROLE.DOCTOR]: '/doctor', [ROLE.GUEST]: '/guest' }[s.role] || '/admin')
  },
  actions: {
    setLogin(vo) {
      setToken(vo.token)
      this.token = vo.token
      const p = getProfile() || {}
      this.userId = p.userId ?? null
      this.username = p.username ?? vo.realName ?? ''
      this.role = vo.role ?? p.role ?? null
      this.realName = vo.realName ?? p.username ?? ''
      this.companyId = vo.companyId ?? p.companyId ?? null
      this.institutionId = vo.institutionId ?? p.institutionId ?? null
      this.doctorId = p.doctorId ?? null
    },
    logout() {
      clearToken()
      this.$reset()
    }
  }
})
