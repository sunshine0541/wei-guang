<template>
  <view class="wg-tabbar">
    <view
      v-for="(item, index) in items"
      :key="item.path"
      class="wg-tabbar-item"
      :class="{ 'wg-tabbar-item-active': index === active }"
      @click="go(index, item.path)"
    >
      <view class="wg-tabbar-icon">
        <u-icon :name="index === active ? item.iconActive : item.icon" :size="iconSize" />
        <!-- B：未读角标挂在「消息」项（按路径判断，避免增删 tab 时错位） -->
        <view v-if="item.path === '/pages/user/notify' && notifyStore.hasUnread" class="wg-tabbar-dot" />
      </view>
      <text class="wg-tabbar-text">{{ item.text }}</text>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { useNotifyStore } from '@/store/notify'

/**
 * 底部导航（自绘，本轮 5 项：首页 / 我的求助 / 社区 / 消息 / 我的）
 * —— 不注册原生 tabBar：避免 PNG 图标资源、便于将来扩项、适老可放大。
 * 「消息」指向现有通知中心 P16。
 * 图标取自 uview-plus 内置字体图标（<u-icon>），随字号缩放。
 */
export default {
  name: 'WgTabBar',
  props: {
    /** 当前激活项：0 首页 / 1 我的求助 / 2 社区 / 3 消息 / 4 我的 */
    active: { type: Number, default: 0 }
  },
  data() {
    return {
      items: [
        { text: '首页', path: '/pages/index/index', icon: 'home', iconActive: 'home-fill' },
        { text: '我的求助', path: '/pages/order/mine', icon: 'list', iconActive: 'list-dot' },
        { text: '社区', path: '/pages/community/index', icon: 'grid', iconActive: 'grid-fill' },
        { text: '消息', path: '/pages/user/notify', icon: 'chat', iconActive: 'chat-fill' },
        { text: '我的', path: '/pages/user/index', icon: 'account', iconActive: 'account-fill' }
      ]
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    notifyStore() {
      return useNotifyStore()
    },
    iconSize() {
      // 年长模式图标放大，配合 ≥56px 的可点区域
      return this.userStore.isElder ? 26 : 22
    }
  },
  methods: {
    go(index, path) {
      if (index === this.active) {
        return
      }
      uni.reLaunch({ url: path })
    }
  }
}
</script>

<style lang="scss">
.wg-tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 90;
  display: flex;
  align-items: center;
  justify-content: space-around;
  height: 64px;
  padding-bottom: env(safe-area-inset-bottom);
  background-color: var(--wg-surface, #ffffff);
  border-top: 1px solid var(--wg-line, #e6e4de);
}

.mode-elder .wg-tabbar {
  height: 72px;
  border-top: 2px solid var(--wg-line, #dedad0);
}

.wg-tabbar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--wg-text-weak, #8c8a82);
}

.wg-tabbar-item-active {
  color: var(--wg-primary-deep, #4f6b4a);
}

.wg-tabbar-text {
  font-size: 11px;
  margin-top: 3px;
}

/* B：未读角标 */
.wg-tabbar-icon {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.wg-tabbar-dot {
  position: absolute;
  top: -2px;
  right: -6px;
  width: 8px;
  height: 8px;
  border-radius: 4px;
  background-color: #c0392b;
}

.mode-elder .wg-tabbar-text {
  font-size: 13px;
}

.wg-tabbar-item-active .wg-tabbar-text {
  font-weight: 800;
}
</style>
