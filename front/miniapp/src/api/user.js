import { request } from './request'

/** U1 我的档案 */
export function getProfile() {
  return request({ url: '/api/user/profile' })
}

/** U2 更新画像（返回 {profile, helpDirectionsDraft}） */
export function updateProfile(data) {
  return request({ url: '/api/user/profile', method: 'PUT', data })
}

/** U3 切换模式（同时重置动效默认值） */
export function setMode(uiMode) {
  return request({ url: '/api/user/mode', method: 'PUT', data: { uiMode } })
}

/** U4 动效开关 */
export function setMotion(enabled) {
  return request({ url: '/api/user/motion', method: 'PUT', data: { enabled } })
}

/** U5 微光邀请应答 */
export function lightInvite(choice) {
  return request({ url: '/api/user/light-invite', method: 'POST', data: { choice } })
}

/** U6 注销账号 */
export function cancelAccount() {
  return request({ url: '/api/user/cancel-account', method: 'POST' })
}

/** A3 隐私同意 */
export function privacyAgree(version) {
  return request({ url: '/api/user/privacy-agree', method: 'POST', data: { version } })
}
