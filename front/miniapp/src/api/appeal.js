import { request } from './request'

/** F1 提交申诉（orderId + type:POST_ORDER + reason） */
export function submitAppeal(data) {
  return request({ url: '/api/appeals', method: 'POST', data })
}

/** F2 提交反馈（内容 ≤500 字） */
export function submitFeedback(data) {
  return request({ url: '/api/feedbacks', method: 'POST', data })
}

/** F3 我的申诉/反馈列表（tab=APPEAL|FEEDBACK） */
export function getMyAppeals(params) {
  return request({ url: '/api/appeals/mine', data: params })
}

/** B：撤回本人处理中的申诉或反馈 */
export function withdrawAppeal(id) {
  return request({ url: `/api/appeals/${id}/withdraw`, method: 'POST' })
}