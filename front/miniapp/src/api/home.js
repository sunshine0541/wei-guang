import { request } from './request'

/** H1 首页聚合：荣耀榜 TOP10 + 善意回流计数 */
export function getHomeSummary() {
  return request({ url: '/api/home/summary' })
}