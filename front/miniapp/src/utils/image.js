import { BASE_URL } from './constants'

/**
 * 4D-补5：远程图片地址解析。
 * 微信开发者工具不再允许 <image> 直接加载 HTTP 图片，本地后端只有 HTTP，
 * 因此 http:// 先尝试下载成本地临时路径，失败再用接口二进制转 base64。
 * https://、wxfile://、data: 等直接返回。
 */
const CACHE = {}

const MIME_MAP = {
  png: 'image/png',
  gif: 'image/gif',
  webp: 'image/webp'
}

export function imageUrl(raw) {
  if (!raw) {
    return ''
  }
  return /^https?:\/\//.test(raw) ? raw : BASE_URL + raw
}

function mimeOf(url) {
  const ext = (url.split('?')[0].split('.').pop() || '').toLowerCase()
  return MIME_MAP[ext] || 'image/jpeg'
}

function loadByImageInfo(url) {
  return new Promise((resolve) => {
    uni.getImageInfo({
      src: url,
      success: (res) => resolve(res && res.path ? res.path : ''),
      fail: () => resolve('')
    })
  })
}

function loadByRequest(url) {
  return new Promise((resolve) => {
    uni.request({
      url,
      responseType: 'arraybuffer',
      success: (res) => {
        if (res.statusCode !== 200 || !res.data) {
          resolve('')
          return
        }
        try {
          const base64 = uni.arrayBufferToBase64(res.data)
          resolve(`data:${mimeOf(url)};base64,${base64}`)
        } catch (e) {
          resolve('')
        }
      },
      fail: () => resolve('')
    })
  })
}

/** 返回可直接给 <image> 或 uni.previewImage 使用的地址（本地路径/HTTPS/data URI） */
export function resolveLocalImage(raw) {
  const full = imageUrl(raw)
  if (!full) {
    return Promise.resolve('')
  }
  if (!/^http:\/\//.test(full)) {
    return Promise.resolve(full)
  }
  if (CACHE[full]) {
    return Promise.resolve(CACHE[full])
  }
  return loadByImageInfo(full).then((local) => {
    if (local) {
      CACHE[full] = local
      return local
    }
    return loadByRequest(full).then((dataUrl) => {
      if (dataUrl) {
        CACHE[full] = dataUrl
      }
      return dataUrl
    })
  })
}
