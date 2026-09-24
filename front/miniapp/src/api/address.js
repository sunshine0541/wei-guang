import { request } from './request'

/** B1 地址簿列表 */
export function listAddresses() {
  return request({ url: '/api/addresses' })
}

/** B2 新增地址 */
export function createAddress(data) {
  return request({ url: '/api/addresses', method: 'POST', data })
}

/** B3 修改地址 */
export function updateAddress(id, data) {
  return request({ url: `/api/addresses/${id}`, method: 'PUT', data })
}

/** B4 删除地址 */
export function deleteAddress(id) {
  return request({ url: `/api/addresses/${id}`, method: 'DELETE' })
}

/** B5 设默认地址 */
export function setDefaultAddress(id) {
  return request({ url: `/api/addresses/${id}/default`, method: 'PUT' })
}
