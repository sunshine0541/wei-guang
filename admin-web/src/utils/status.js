/* 状态色语义：统一管理端 pill 的色调，避免各页面重复 statusTag */

const ORDER_TONES = {
  PENDING: 'amber',
  ACCEPTED: 'blue',
  IN_SERVICE: 'blue',
  PENDING_CONFIRM: 'amber',
  COMPLETED: 'green',
  CANCELLED: 'gray',
  EXPIRED: 'gray',
  APPEALING: 'red'
}

const REPORT_TONES = {
  PENDING: 'amber',
  CONFIRMED: 'red',
  REJECTED: 'gray'
}

const APPEAL_TONES = {
  PENDING: 'amber',
  RESOLVED: 'green',
  WITHDRAWN: 'gray'
}

const POST_TONES = {
  PENDING_REVIEW: 'amber',
  PENDING_MANUAL: 'amber',
  PUBLISHED: 'green',
  HIDDEN: 'gray',
  REJECTED: 'red',
  DELETED: 'gray'
}

const USER_TONES = {
  NORMAL: 'green',
  BANNED: 'red',
  CANCELLED: 'gray'
}

export function orderStatusTone(status) {
  return ORDER_TONES[status] || 'gray'
}

export function reportStatusTone(status) {
  return REPORT_TONES[status] || 'gray'
}

export function appealStatusTone(status) {
  return APPEAL_TONES[status] || 'gray'
}

export function postStatusTone(status) {
  return POST_TONES[status] || 'gray'
}

export function userStatusTone(status) {
  return USER_TONES[status] || 'gray'
}

/** 风险列：已核实正常=green；已核实扣分/存疑=red；未标记=gray */
export function riskTone(row) {
  if (row && row.riskReviewedAt) {
    return row.riskReviewResult === 'DEDUCT' ? 'red' : 'green'
  }
  return row && row.suspicious ? 'red' : 'gray'
}

export function pillClass(tone) {
  return 'wg-pill is-' + (tone || 'gray')
}
