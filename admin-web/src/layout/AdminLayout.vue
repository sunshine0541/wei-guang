<template>
  <div class="wg-shell">
    <aside class="wg-aside">
      <div class="brand">
        <div class="brand-mark">微</div>
        <div>
          <h1>微光</h1>
          <p>社区互助 · 管理后台</p>
        </div>
      </div>

      <nav class="wg-nav">
        <template v-for="group in NAV" :key="group.label">
          <p class="group">{{ group.label }}</p>
          <div
            v-for="item in group.items"
            :key="item.key"
            class="nav-item"
            :class="{ active: activeKey === item.key }"
            @click="go(item.key)"
          >
            <AdminIcon :name="item.icon" :size="17" />
            <span>{{ item.label }}</span>
            <i v-if="badgeOf(item.badge)" class="badge">{{ badgeOf(item.badge) }}</i>
          </div>
        </template>
      </nav>

      <div class="aside-foot">
        <div class="health">
          <i></i>
          <span>服务运行中</span>
          <small>{{ todo.total ? todo.total + ' 项待办' : '暂无待办' }}</small>
        </div>
        <div class="admin-row">
          <div class="avatar">{{ avatarText }}</div>
          <div class="admin-meta">
            <p>{{ auth.username || '管理员' }}</p>
            <span>运营管理员</span>
          </div>
          <button class="logout" title="退出登录" @click="onLogout">
            <AdminIcon name="logout" :size="15" />
          </button>
        </div>
      </div>
    </aside>

    <div class="wg-main">
      <header class="wg-header">
        <div class="title">
          <h1>{{ pageMeta.title }}</h1>
          <p>{{ pageMeta.sub }}</p>
        </div>
        <div class="header-actions">
          <span class="date">{{ todayText }}</span>
          <div class="search">
            <AdminIcon name="search" :size="14" />
            <input
              v-model="searchKeyword"
              type="text"
              placeholder="搜索订单号 / 标题 / 发布者"
              @keyup.enter="onSearch"
            />
          </div>
          <button class="icon-btn" title="查看待办" @click="router.push('/overview')">
            <AdminIcon name="bell" :size="16" />
            <i v-if="todo.total" class="dot"></i>
          </button>
        </div>
      </header>

      <main class="wg-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminIcon from '../components/AdminIcon.vue'
import { useAuthStore } from '../store/auth'
import { useTodoStore } from '../store/todo'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const todo = useTodoStore()
const searchKeyword = ref('')

const NAV = [
  {
    label: '运营',
    items: [
      { key: '/overview', label: '工作台', icon: 'dashboard' },
      { key: '/orders', label: '求助订单', icon: 'clipboard', badge: 'risk' },
      { key: '/community', label: '社区审核', icon: 'message', badge: 'posts' },
      { key: '/reports', label: '举报处置', icon: 'shield', badge: 'reports' },
      { key: '/appeals', label: '申诉仲裁', icon: 'scale', badge: 'appeals' }
    ]
  },
  {
    label: '系统',
    items: [
      { key: '/users', label: '用户管理', icon: 'users' },
      { key: '/configs', label: '配置管理', icon: 'settings' }
    ]
  }
]

const activeKey = computed(() => {
  const path = route.path
  if (path.startsWith('/orders')) return '/orders'
  if (path.startsWith('/community')) return '/community'
  if (path.startsWith('/reports')) return '/reports'
  if (path.startsWith('/appeals')) return '/appeals'
  if (path.startsWith('/users')) return '/users'
  if (path.startsWith('/configs')) return '/configs'
  return '/overview'
})

const pageMeta = computed(() => {
  const path = route.path
  if (path.startsWith('/orders/')) {
    return { title: '订单详情', sub: '订单信息、时间线与处置' }
  }
  if (path.startsWith('/users/')) {
    return { title: '用户详情', sub: '账号状态、微光值与账本明细' }
  }
  const map = {
    '/overview': { title: '工作台', sub: '今天要处理的事，都在这里' },
    '/orders': { title: '求助 / 订单管理', sub: '跟踪发布、接单、服务与完成' },
    '/community': { title: '社区审核', sub: '动态审核、评论与举报联动' },
    '/reports': { title: '上报处置', sub: '防诈上报与内容举报的处置队列' },
    '/appeals': { title: '申诉 / 反馈处理', sub: '仲裁落回与用户反馈回复' },
    '/users': { title: '用户管理', sub: '账号状态、微光值与明细' },
    '/configs': { title: '配置管理', sub: '业务参数修改，保存即生效' }
  }
  return map[path] || { title: route.meta.title || '', sub: '' }
})

const todayText = computed(() => {
  const now = new Date()
  const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][now.getDay()]
  return `${now.getFullYear()} 年 ${now.getMonth() + 1} 月 ${now.getDate()} 日 · ${week}`
})

const avatarText = computed(() => (auth.username || '管').slice(0, 1))

function badgeOf(key) {
  if (!key) return 0
  return todo.counts[key] || 0
}

function go(path) {
  if (route.path === path) return
  router.push(path)
}

function onSearch() {
  const keyword = searchKeyword.value.trim()
  if (!keyword) return
  router.push({ path: '/orders', query: { keyword } })
}

function onLogout() {
  auth.logout()
  todo.$reset()
  router.push('/login')
}

onMounted(() => {
  todo.load()
})
</script>

<style scoped>
.wg-shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--wg-canvas);
}

.wg-aside {
  width: 216px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--wg-ink);
  color: #e8e2d8;
}

.brand {
  height: 72px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  border-bottom: 1px solid #332d27;
}

.brand-mark {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: linear-gradient(135deg, #c77b2b, #8a4b22);
  color: #fff6ec;
  font-weight: 700;
  font-size: 15px;
}

.brand h1 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.2em;
  color: #f5ede2;
}

.brand p {
  margin: 3px 0 0;
  font-size: 10.5px;
  letter-spacing: 0.08em;
  color: #8b8175;
}

.wg-nav {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.group {
  margin: 0;
  padding: 10px 10px 6px;
  font-size: 10.5px;
  letter-spacing: 0.16em;
  color: #6e655b;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 40px;
  margin-bottom: 2px;
  padding: 0 10px;
  border-radius: 8px;
  color: #b7ada0;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.04);
  color: #e6dbcd;
}

.nav-item.active {
  background: #2a2521;
  color: #f5ede2;
}

.nav-item.active::before {
  content: '';
  position: absolute;
  left: -12px;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--wg-clay);
}

.badge {
  margin-left: auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--wg-clay);
  color: #fff6ec;
  font-size: 10px;
  font-style: normal;
  display: grid;
  place-items: center;
}

.aside-foot {
  padding: 12px;
  border-top: 1px solid #332d27;
}

.health {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 9px 10px;
  border: 1px solid #3a332d;
  border-radius: 10px;
  background: #2a2521;
  font-size: 11.5px;
  color: #d9cfc1;
}

.health i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #7fb07a;
  box-shadow: 0 0 0 3px rgba(127, 176, 122, 0.12);
}

.health small {
  margin-left: auto;
  color: #8b8175;
  font-size: 10.5px;
}

.admin-row {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 12px 4px 0;
}

.avatar {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #3b342d;
  color: #e8d9c8;
  font-size: 12px;
}

.admin-meta {
  min-width: 0;
}

.admin-meta p {
  margin: 0;
  font-size: 12px;
  color: #ede4d8;
}

.admin-meta span {
  font-size: 10.5px;
  color: #7e7468;
}

.logout {
  margin-left: auto;
  padding: 6px;
  border: none;
  border-radius: 6px;
  background: none;
  color: #6e655b;
  cursor: pointer;
}

.logout:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #e6dbcd;
}

.wg-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.wg-header {
  height: 66px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  border-bottom: 1px solid #e6ded1;
  background: rgba(247, 243, 236, 0.95);
  backdrop-filter: blur(6px);
}

.title h1 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.title p {
  margin: 2px 0 0;
  font-size: 11.5px;
  color: var(--wg-muted);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.date {
  margin-right: 4px;
  font-size: 11.5px;
  color: var(--wg-muted);
}

.search {
  position: relative;
}

.search :deep(svg) {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--wg-muted-light);
}

.search input {
  width: 258px;
  height: 36px;
  padding: 0 12px 0 32px;
  border: 1px solid #e2d8ca;
  border-radius: 8px;
  background: var(--wg-panel);
  color: var(--wg-text);
  font-size: 12.5px;
  outline: none;
}

.search input:focus {
  border-color: var(--wg-clay);
  box-shadow: 0 0 0 3px rgba(181, 102, 60, 0.12);
}

.icon-btn {
  position: relative;
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border: 1px solid #e2d8ca;
  border-radius: 8px;
  background: var(--wg-panel);
  color: #6b6259;
  cursor: pointer;
}

.icon-btn:hover {
  background: #f6f0e6;
}

.dot {
  position: absolute;
  right: 9px;
  top: 9px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--wg-danger);
}

.wg-content {
  flex: 1;
  overflow-y: auto;
  padding: 22px 28px 40px;
}

::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-thumb {
  border-radius: 4px;
  background: #d8cdbe;
}

::-webkit-scrollbar-track {
  background: transparent;
}
</style>
