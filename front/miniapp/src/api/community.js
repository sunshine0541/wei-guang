import { request } from './request'

/** 社区邻里动态（只读最近完成的互助） */
export function listCommunityFeed(params) {
  return request({ url: '/api/community/feed', data: params })
}
