import { request } from './request'

/** O11 标记完成（发起双确认） */
export function completeOrder(id) {
  return request({ url: `/api/orders/${id}/complete`, method: 'POST' })
}

/** O12 确认/拒绝完成（agree=false 需 reason） */
export function confirmOrder(id, data) {
  return request({ url: `/api/orders/${id}/confirm`, method: 'POST', data, silent: true })
}

/** S1 提交评价（仅受助者、一单一评） */
export function submitEvaluation(id, data) {
  return request({ url: `/api/orders/${id}/evaluation`, method: 'POST', data })
}

/** S3 提交感谢信（仅受助者、一单一封） */
export function submitLetter(id, data) {
  return request({ url: `/api/orders/${id}/thank-letter`, method: 'POST', data })
}

/** S2 感谢信 AI 起头（返回 {letterDraft, degraded}） */
export function aiDraftLetter(id) {
  return request({ url: `/api/orders/${id}/thank-letter/ai-draft`, method: 'POST' })
}

/** S4 发送悄悄话（双方各一条） */
export function submitWhisper(id, data) {
  return request({ url: `/api/orders/${id}/whisper`, method: 'POST', data })
}

/** S5 查看故事卡 */
export function getStoryCard(id) {
  return request({ url: `/api/orders/${id}/story-card`, silent: true })
}

/** U8 微光值余额与明细 */
export function getCreditLedger(params) {
  return request({ url: '/api/user/credit', data: params })
}

/** U9 积分余额与明细 */
export function getPointLedger(params) {
  return request({ url: '/api/user/points', data: params })
}

/** U10 我收到的感谢 */
export function getMyThanks(params) {
  return request({ url: '/api/user/thanks', data: params })
}

/** U11 邻居公开页 */
export function getNeighborProfile(id) {
  return request({ url: `/api/users/${id}/profile` })
}

/** U12 邻居收到的评价列表 */
export function getNeighborEvaluations(id, params) {
  return request({ url: `/api/users/${id}/evaluations`, data: params })
}