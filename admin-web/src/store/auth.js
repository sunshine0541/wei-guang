import { defineStore } from 'pinia'
import { adminLogin } from '../api/admin'

/**
 * 管理端登录态（独立 JWT，identity=admin；与小程序端完全隔离）。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('wg_admin_token') || '',
    username: localStorage.getItem('wg_admin_name') || ''
  }),
  actions: {
    async login(username, password) {
      const data = await adminLogin({ username, password })
      this.token = data.token
      this.username = username
      localStorage.setItem('wg_admin_token', data.token)
      localStorage.setItem('wg_admin_name', username)
    },
    logout() {
      this.token = ''
      this.username = ''
      localStorage.removeItem('wg_admin_token')
      localStorage.removeItem('wg_admin_name')
    }
  }
})