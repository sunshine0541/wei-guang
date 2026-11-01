import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '',
  timeout: 20000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('wg_admin_token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (!body || typeof body.code === 'undefined') {
      ElMessage.error('服务响应异常')
      return Promise.reject(new Error('bad response'))
    }
    if (body.code === 0) {
      return body.data
    }
    if (body.code === 20001 || body.code === 20002) {
      localStorage.removeItem('wg_admin_token')
      localStorage.removeItem('wg_admin_name')
      if (router.currentRoute.value.path !== '/login') {
        ElMessage.error('登录已失效，请重新登录')
        router.push('/login')
      }
    } else if (body.code === 20003) {
      // 20003 是业务无权访问（如订单不存在/已无权查看），不能当登录失效踢出后台
      ElMessage.error(body.message || '无权访问该资源')
    } else {
      ElMessage.error(body.message || '操作失败')
    }
    return Promise.reject(body)
  },
  (error) => {
    ElMessage.error('网络异常，请确认后端已启动')
    return Promise.reject(error)
  }
)

export default request