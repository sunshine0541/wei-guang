import { BASE_URL, MESSAGES, STORAGE_KEYS } from '@/utils/constants'

let reloginPromise = null

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
 * 统一请求封装（详设 T05）：
 * - 自动带 token
 * - 20001/20002 时静默 wx.login 重登后重放一次（详设 §3.1 已注销/凭证过期处理）
 * - 统一 toast 温柔文案
 * options.silent=true 时不弹 toast（用于登录等由页面自行提示的调用）
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
        if (needRelogin && !retried && options.url !== '/api/auth/wx-login') {
          silentRelogin()
            .then(() => request(options, true))
            .then(resolve)
            .catch(reject)
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

/** 静默重登：wx.login 取 code → /api/auth/wx-login → 落 token */
export function silentRelogin() {
  if (!reloginPromise) {
    reloginPromise = new Promise((resolve, reject) => {
      uni.login({
        provider: 'weixin',
        success: (loginRes) => {
          uni.request({
            url: BASE_URL + '/api/auth/wx-login',
            method: 'POST',
            data: { code: loginRes.code },
            header: { 'Content-Type': 'application/json' },
            success: (res) => {
              const body = res.data
              if (body && body.code === 0) {
                setToken(body.data.token)
                resolve(body.data)
              } else {
                clearToken()
                reject(body)
              }
            },
            fail: () => {
              clearToken()
              reject(new Error('relogin fail'))
            }
          })
        },
        fail: () => {
          clearToken()
          reject(new Error('uni.login fail'))
        }
      })
    }).finally(() => {
      reloginPromise = null
    })
  }
  return reloginPromise
}
