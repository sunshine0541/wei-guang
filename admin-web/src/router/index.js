import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue'), meta: { title: '登录' } },
  {
    path: '/',
    component: () => import('../layout/AdminLayout.vue'),
    redirect: '/overview',
    children: [
      { path: 'overview', component: () => import('../views/Overview.vue'), meta: { title: '工作台' } },
      { path: 'orders', component: () => import('../views/Orders.vue'), meta: { title: '求助/订单管理' } },
      { path: 'orders/:id', component: () => import('../views/OrderDetail.vue'), meta: { title: '订单详情' } },
      { path: 'reports', component: () => import('../views/Reports.vue'), meta: { title: '上报处置' } },
      { path: 'users', component: () => import('../views/Users.vue'), meta: { title: '用户管理' } },
      { path: 'users/:id', component: () => import('../views/UserDetail.vue'), meta: { title: '用户详情' } },
      { path: 'configs', component: () => import('../views/Configs.vue'), meta: { title: '配置管理' } },
      { path: 'community', component: () => import('../views/Community.vue'), meta: { title: '社区管理' } },
      { path: 'appeals', component: () => import('../views/Appeals.vue'), meta: { title: '申诉/反馈处理' } }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const token = localStorage.getItem('wg_admin_token')
  if (to.path !== '/login' && !token) {
    return '/login'
  }
  if (to.path === '/login' && token) {
    return '/overview'
  }
  return true
})

export default router