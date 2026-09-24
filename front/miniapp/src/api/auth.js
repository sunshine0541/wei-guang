import { request } from './request'

/** A1 微信静默登录 */
export function loginByCode(code) {
  return request({
    url: '/api/auth/wx-login',
    method: 'POST',
    data: { code },
    silent: true
  })
}

/** A2 退出登录（无状态 JWT，前端清 token 即可，服务端仅记日志） */
export function logout() {
  return request({ url: '/api/auth/logout', method: 'POST', silent: true })
}
