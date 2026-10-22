import { request } from './request'

/** C1 当前轮会话（详设 §3.2；含接单者专属地址/紧急联系人，见 D10）
 *  A3：非可聊天状态（如 PENDING 单尚未建会话）后端返回 40002，属正常情况，
 *  故 silent=true 不弹全局 toast，由页面按自身状态决定是否请求/展示 */
export function getSession(orderId) {
  return request({ url: `/api/orders/${orderId}/session`, silent: true })
}

/** O15 照护提示卡（仅接单者、服务有效状态内；非接单者 20003 静默处理） */
export function getCareTip(orderId) {
  return request({ url: `/api/orders/${orderId}/care-tip`, silent: true })
}

/** C2 消息历史（分页，倒序） */
export function listMessages(sessionId, params) {
  return request({ url: `/api/sessions/${sessionId}/messages`, data: params })
}

/** C3 发消息（REST 兜底通道） */
export function sendMessage(sessionId, data) {
  return request({ url: `/api/sessions/${sessionId}/messages`, method: 'POST', data, silent: true })
}

/** C4 会话已读（未读数清零） */
export function readSession(sessionId) {
  return request({ url: `/api/sessions/${sessionId}/read`, method: 'POST', silent: true })
}

/** C5 快捷短语（按角色 HELPER/REQUESTER 两套，固定 5 条） */
export function listQuickPhrases(role) {
  return request({ url: '/api/quick-phrases', data: { role } })
}