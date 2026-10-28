import request from './request'

/** 管理端登录（限流复用 rate.login_per_minute，键 rate:admin:login:） */
export function adminLogin(data) {
  return request.post('/admin/auth/login', data)
}

export function getOverview() {
  return request.get('/admin/overview')
}

export function listOrders(params) {
  return request.get('/admin/orders', { params })
}

export function getOrderDetail(id) {
  return request.get(`/admin/orders/${id}`)
}

export function cancelArbitrate(id, data) {
  return request.post(`/admin/orders/${id}/cancel-arbitrate`, data)
}

/** 4C 风险核实：result=NORMAL/DEDUCT + note */
export function riskReview(id, data) {
  return request.post(`/admin/orders/${id}/risk-review`, data)
}

export function listReports(params) {
  return request.get('/admin/reports', { params })
}

export function handleReport(id, data) {
  return request.post(`/admin/reports/${id}/handle`, data)
}

export function listUsers(params) {
  return request.get('/admin/users', { params })
}

export function getUserDetail(id) {
  return request.get(`/admin/users/${id}`)
}

export function banUser(id) {
  return request.post(`/admin/users/${id}/ban`)
}

export function unbanUser(id) {
  return request.post(`/admin/users/${id}/unban`)
}

export function creditAdjust(id, data) {
  return request.post(`/admin/users/${id}/credit-adjust`, data)
}

export function listConfigs() {
  return request.get('/admin/configs')
}

// ---------- 社区 2.0（v1.3） ----------

export function listCommunityPosts(params) {
  return request.get('/admin/community/posts', { params })
}

export function getCommunityPost(id) {
  return request.get(`/admin/community/posts/${id}`)
}

export function reviewCommunityPost(id, data) {
  return request.post(`/admin/community/posts/${id}/review`, data)
}

export function hideCommunityPost(id) {
  return request.post(`/admin/community/posts/${id}/hide`)
}

export function restoreCommunityPost(id) {
  return request.post(`/admin/community/posts/${id}/restore`)
}

export function deleteCommunityPost(id) {
  return request.delete(`/admin/community/posts/${id}`)
}

export function createOfficialPost(data) {
  return request.post('/admin/community/official', data)
}

export function reviewCommunityComment(id, data) {
  return request.post(`/admin/community/comments/${id}/review`, data)
}

export function deleteCommunityComment(id) {
  return request.delete(`/admin/community/comments/${id}`)
}

export function updateConfig(key, value) {
  return request.put(`/admin/configs/${key}`, { value })
}

export function listAppeals(params) {
  return request.get('/admin/appeals', { params })
}

export function getAppealDetail(id) {
  return request.get(`/admin/appeals/${id}`)
}

export function arbitrateAppeal(id, data) {
  return request.post(`/admin/appeals/${id}/arbitrate`, data)
}

export function replyFeedback(id, conclusion) {
  return request.post(`/admin/feedbacks/${id}/reply`, { conclusion })
}