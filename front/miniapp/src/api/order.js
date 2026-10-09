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
