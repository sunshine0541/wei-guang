/* 管理端时间与数值格式化 */

function toDate(value) {
  if (!value) {
    return null
  }
  const text = String(value)
    .replace('T', ' ')
    .replace(/-/g, '/')
    .replace(/\.\d+$/, '')
  const date = new Date(text)
  return Number.isNaN(date.getTime()) ? null : date
}

function pad(value) {
  return String(value).padStart(2, '0')
}

/** yyyy-MM-dd HH:mm */
export function formatDateTime(value) {
  const date = toDate(value)
  if (!date) {
    return '—'
  }
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** MM-dd HH:mm */
export function formatShort(value) {
  const date = toDate(value)
  if (!date) {
    return '—'
  }
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 等待时长：{ text, overdue, minutes }；无法解析返回 null */
export function waitingSince(value) {
  const date = toDate(value)
  if (!date) {
    return null
  }
  const minutes = Math.max(0, Math.floor((Date.now() - date.getTime()) / 60000))
  let text = '刚刚'
  if (minutes >= 1440) {
    text = Math.floor(minutes / 1440) + ' 天'
  } else if (minutes >= 60) {
    text = Math.floor(minutes / 60) + ' 小时'
  } else if (minutes >= 1) {
    text = minutes + ' 分钟'
  }
  return { text, overdue: minutes > 1440, minutes }
}

export function percent(part, total) {
  const p = Number(part) || 0
  const t = Number(total) || 0
  if (t <= 0) {
    return 0
  }
  return Math.round((p / t) * 100)
}
