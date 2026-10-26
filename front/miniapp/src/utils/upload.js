import { BASE_URL, MESSAGES } from './constants'
import { getToken, toast } from '@/api/request'

/**
 * 图片上传（详设 §3.3 P1）：wx.uploadFile → POST /api/upload/image，返回 /files/** 相对 URL。
 */
export function uploadImage(filePath) {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: BASE_URL + '/api/upload/image',
      filePath,
      name: 'file',
      header: { Authorization: 'Bearer ' + getToken() },
      success: (res) => {
        let body = res.data
        try {
          body = JSON.parse(res.data)
        } catch (e) {
          toast(MESSAGES.ERROR)
          reject(new Error('upload parse fail'))
          return
        }
        if (body && body.code === 0) {
          resolve(body.data.url)
        } else {
          toast((body && body.message) || MESSAGES.ERROR)
          reject(body)
        }
      },
      fail: () => {
        toast(MESSAGES.NETWORK)
        reject(new Error('upload fail'))
      }
    })
  })
}
