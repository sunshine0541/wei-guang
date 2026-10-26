import { request } from './request'

/** 4D 高德 POI 搜索（后端代理，Key 不下发；silent=true 由页面自行提示） */
export function searchPois(keyword) {
  return request({ url: '/api/geo/pois', data: { keyword }, silent: true })
}

/** 4D-补 输入提示（边输边联想；失败静默，不打断手动填写） */
export function suggestPois(keyword) {
  return request({ url: '/api/geo/tips', data: { keyword }, silent: true })
}
