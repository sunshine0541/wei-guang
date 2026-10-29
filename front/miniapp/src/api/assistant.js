import { request } from './request'

/** AI 助手对话：问答 / 补槽 / 生成确认草稿 */
export function assistantChat(data) {
  return request({ url: '/api/assistant/chat', method: 'POST', data })
}

/** AI 助手确认发布：confirmToken 一次性使用 */
export function assistantConfirm(data) {
  return request({ url: '/api/assistant/confirm', method: 'POST', data })
}

/** 恢复 AI 助手会话：校验服务端 2 小时会话是否还在 */
export function assistantSession(sessionId) {
  return request({ url: '/api/assistant/session', method: 'GET', data: { sessionId } })
}

/** 编辑 AI 助手草稿：修改后旧确认令牌作废 */
export function assistantUpdateDraft(data) {
  return request({ url: '/api/assistant/draft', method: 'PUT', data })
}
