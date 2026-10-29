/**
 * 服务端时间解析：后端统一返回 yyyy-MM-dd HH:mm:ss，
 * iOS 的 JavaScriptCore 对带空格的连字符格式解析不稳定，统一转成 yyyy/MM/dd HH:mm:ss。
 */
export function parseServerTime(value) {
  if (!value) {
    return null
  }
  const normalized = String(value).replace(/-/g, '/').replace('T', ' ')
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}

export function serverTimeMs(value) {
  const date = parseServerTime(value)
  return date ? date.getTime() : 0
}
