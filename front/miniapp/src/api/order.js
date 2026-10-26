import { request } from './request'

/** T1 求助模板（热点缓存） */
export function listTemplates() {
  return request({ url: '/api/templates' })
}

/** O1 能力一：自由描述 → 类型/标签/紧急度/标题建议 */
export function aiSuggest(text) {
  return request({ url: '/api/orders/ai-suggest', method: 'POST', data: { text } })
}

/** O2 发布求助 */
export function publishOrder(data) {
  return request({ url: '/api/orders', method: 'POST', data })
}

/** O3 求助列表（tab=all|recommend） */
export function listOrders(params) {
  return request({ url: '/api/orders', data: params })
}

/** O4 求助详情 */
export function getOrder(id) {
  return request({ url: `/api/orders/${id}` })
}

/** O16 修改待响应求助（仅发布者、仅 PENDING） */
export function modifyOrder(id, data) {
  return request({ url: `/api/orders/${id}`, method: 'PUT', data })
}

/** O5 我的求助 */
export function listMyOrders(params) {
  return request({ url: '/api/orders/mine', data: params })
}

/** O6 接单（"我愿意帮助"）；silent：由详情页按错误码给出 P04 指定文案 */
export function acceptOrder(id) {
  return request({ url: `/api/orders/${id}/accept`, method: 'POST', silent: true })
}

/** O13 一键重发 */
export function republishOrder(id) {
  return request({ url: `/api/orders/${id}/republish`, method: 'POST' })
}

/** O7 确认开始（仅当前轮接单者） */
export function startOrder(id) {
  return request({ url: `/api/orders/${id}/start`, method: 'POST' })
}

/** O8 释放（reasonType: HELPER_QUIT/HELPLESS/HELPER_LOST） */
export function releaseOrder(id, data) {
  return request({ url: `/api/orders/${id}/release`, method: 'POST', data })
}

/** O9 取消（mode: CANCEL 单方 / NEGOTIATE 发起商量） */
export function cancelOrder(id, data) {
  return request({ url: `/api/orders/${id}/cancel`, method: 'POST', data })
}

/** O10 协商取消应答 */
export function cancelConfirmOrder(id, data) {
  return request({ url: `/api/orders/${id}/cancel-confirm`, method: 'POST', data })
}

/** O14 订单时间线 */
export function getOrderTimeline(id) {
  return request({ url: `/api/orders/${id}/timeline` })
}
