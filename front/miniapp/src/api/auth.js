import { request } from './request'

/** A1 微信静默登录（D31 后前端不再调用，保留可回退） */
export function loginByCode(code) {
  return request({
    url: '/api/auth/wx-login',
    method: 'POST',
    data: { code },
    silent: true
  })
}

/** 账号注册（D31 账密登录主导；错误由登录页自行展示） */
export function registerAccount(data) {
  return request({ url: '/api/auth/register', method: 'POST', data, silent: true })
}

/** 账号密码登录（错误由登录页自行展示） */
export function loginAccount(data) {
  return request({ url: '/api/auth/login', method: 'POST', data, silent: true })
}

/** A2 退出登录（无状态 JWT，前端清 token 即可，服务端仅记日志） */
export function logout() {
  return request({ url: '/api/auth/logout', method: 'POST', silent: true })
}