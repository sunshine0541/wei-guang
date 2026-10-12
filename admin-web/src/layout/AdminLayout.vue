<template>
  <el-container class="layout">
    <el-aside width="210px" class="aside">
      <div class="logo">微光 · 管理后台</div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item index="/overview">数据概览</el-menu-item>
        <el-menu-item index="/orders">求助/订单管理</el-menu-item>
        <el-menu-item index="/reports">上报处置</el-menu-item>
        <el-menu-item index="/users">用户管理</el-menu-item>
        <el-menu-item index="/configs">配置管理</el-menu-item>
        <el-menu-item index="/appeals">申诉/反馈处理</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="page-title">{{ $route.meta.title || '' }}</span>
        <div class="user-area">
          <span class="who">{{ auth.username }}</span>
          <el-button link type="primary" @click="onLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../store/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/** 详情页（/orders/:id、/users/:id）高亮对应一级菜单 */
const activeMenu = computed(() => {
  if (route.path.startsWith('/orders')) {
    return '/orders'
  }
  if (route.path.startsWith('/users')) {
    return '/users'
  }
  return route.path
})

function onLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
}

.aside {
  background-color: #ffffff;
  border-right: 1px solid #e4e7ed;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-weight: 600;
  color: #b06a00;
  font-size: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.menu {
  border-right: none;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #ffffff;
  border-bottom: 1px solid #e4e7ed;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
}

.who {
  margin-right: 12px;
  color: #666;
}
</style>