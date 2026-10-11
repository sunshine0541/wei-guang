import { request } from './request'

/** N1 通知列表（分页，按时间倒序） */
export function listNotifications(params) {
  return request({ url: '/api/notifications', data: params })
}

/** N2 首页横幅（最近 1 条未读+聚合） */
export function getNotifyBanner() {
  return request({ url: '/api/notifications/banner' })
}

/** N3 已读（幂等） */
export function readNotification(id) {
  return request({ url: `/api/notifications/${id}/read`, method: 'POST' })
}

/** N4 全部已读（{cleared: n}） */
export function readAllNotifications() {
  return request({ url: '/api/notifications/read-all', method: 'POST' })
}