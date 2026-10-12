import { BASE_URL, MESSAGES, STORAGE_KEYS } from '@/utils/constants'

export function getToken() {
  return uni.getStorageSync(STORAGE_KEYS.TOKEN) || ''
}

export function setToken(token) {
  uni.setStorageSync(STORAGE_KEYS.TOKEN, token)
}

export function clearToken() {
  uni.removeStorageSync(STORAGE_KEYS.TOKEN)
}

export function toast(message) {
  uni.showToast({ title: message, icon: 'none', duration: 2500 })
}

/**
 * 清洗请求参数：去掉 undefined（否则会被序列化成字符串 "undefined" 传给后端 → 10001）；
 * query 类请求（GET/DELETE）额外去掉 null 与空串，避免出现 ?orderType= 这种空参数。
 * POST/PUT 的 body 保留 null 与空串（空串可能表示"清空该字段"）。
 */
function cleanParams(data, method) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    return data
  }
  const upper = (method || 'GET').toUpperCase()
  const isQuery = upper === 'GET' || upper === 'DELETE'
  const cleaned = {}
  Object.keys(data).forEach((key) => {
    const value = data[key]
    if (value === undefined) {
      return
    }
    if (isQuery && (value === null || value === '')) {
      return
    }
    cleaned[key] = value
  })
  return cleaned
}

/**
 * 统一请求封装：
 * - 自动带 token
 * - 20001/20002 → 清凭证回登录页（D31 账密登录主导；登录/注册接口自身除外）
 * - 统一 toast 温柔文案
 * options.silent=true 时不弹 toast（用于登录/注册等由页面自行提示的调用）
 */
export function request(options, retried = false) {
  return new Promise((resolve, reject) => {
    const header = { 'Content-Type': 'application/json' }
    const token = getToken()
    if (token) {
      header.Authorization = 'Bearer ' + token
    }
    const method = options.method || 'GET'
    uni.request({
      url: BASE_URL + options.url,
      method,
      data: cleanParams(options.data, method),
      header,
      timeout: 20000,
      success: (res) => {
        const body = res.data
        if (res.statusCode !== 200 || !body || typeof body.code === 'undefined') {
          toast(MESSAGES.NETWORK)
          reject(new Error('HTTP ' + res.statusCode))
          return
        }
        if (body.code === 0) {
          resolve(body.data)
          return
        }
        const needRelogin = body.code === 20001 || body.code === 20002
        if (needRelogin && !retried) {
          // D31 账密登录主导：凭证过期/已注销 → 清凭证回登录页，不再静默重登
          clearToken()
          if (options.url !== '/api/auth/login' && options.url !== '/api/auth/register') {
            uni.reLaunch({ url: '/pages/login/index' })
          }
          reject(body)
          return
        }
        if (body.code === 20004) {
          // 封禁拦截：按 §6.5 写死文案呈现（错误码表 message 仅为内部标注）
          toast(MESSAGES.BANNED)
          reject(body)
          return
        }
        if (options.silent !== true) {
          toast(body.message || MESSAGES.ERROR)
        }
        reject(body)
      },
      fail: () => {
        toast(MESSAGES.NETWORK)
        reject(new Error('network fail'))
      }
    })
  })
}
