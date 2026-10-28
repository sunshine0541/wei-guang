import { request } from './request'

/** CM1 社区顶部三块聚合 */
export function getCommunitySummary() {
  return request({ url: '/api/community/summary' })
}

/** CM2 动态列表（tab=NEIGHBOR/STORY/OFFICIAL） */
export function listPosts(params) {
  return request({ url: '/api/community/posts', data: params })
}

/** CM3 发布动态 */
export function createPost(data) {
  return request({ url: '/api/community/posts', method: 'POST', data })
}

/** CM4 动态详情 */
export function getPost(id) {
  return request({ url: `/api/community/posts/${id}` })
}

/** CM5 删除动态（作者） */
export function deletePost(id) {
  return request({ url: `/api/community/posts/${id}`, method: 'DELETE' })
}

/** CM6 我的动态（含审核中/未通过） */
export function listMyPosts(params) {
  return request({ url: '/api/community/posts/mine', data: params })
}

/** CM7 点赞 */
export function likePost(id) {
  return request({ url: `/api/community/posts/${id}/like`, method: 'POST' })
}

/** CM8 取消点赞 */
export function unlikePost(id) {
  return request({ url: `/api/community/posts/${id}/like`, method: 'DELETE' })
}

/** CM9 评论列表 */
export function listComments(id, params) {
  return request({ url: `/api/community/posts/${id}/comments`, data: params })
}

/** CM10 发表评论 */
export function createComment(id, data) {
  return request({ url: `/api/community/posts/${id}/comments`, method: 'POST', data })
}

/** CM11 删除评论（作者） */
export function deleteComment(id) {
  return request({ url: `/api/community/comments/${id}`, method: 'DELETE' })
}

/** CM12 举报动态/评论 */
export function reportContent(data) {
  return request({ url: '/api/community/reports', method: 'POST', data })
}
