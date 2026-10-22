<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view v-if="loading" class="wg-skel">
      <view class="wg-skel-block"></view>
      <view class="wg-skel-block"></view>
      <text class="wg-label" style="margin-top: 6px">正在为您准备，请稍等</text>
    </view>

    <template v-else>
      <view v-if="list.length === 0" class="wg-empty">还没有收到感谢信，帮过邻居之后，他们会在这里留下话</view>
      <view v-for="item in list" :key="item.letterId" class="wg-card">
        <view class="wg-card-head">
          <text class="wg-subtitle">{{ item.senderNickname || '邻居' }}</text>
        </view>
        <view class="wg-label" style="margin-top: 4px">{{ item.title }}</view>
        <text class="wg-thanks-text">{{ item.content }}</text>
        <view class="wg-label" style="margin-top: 6px">{{ item.createdAt }}</view>
      </view>
    </template>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { getMyThanks } from '@/api/incentive'

/** P13 我收到的感谢（U10）：独立页面展示，个人中心仅保留入口与数量 */
export default {
  data() {
    return {
      loading: true,
      list: []
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    }
  },
  onLoad() {
    this.load()
  },
  methods: {
    async load() {
      try {
        const result = await getMyThanks({ page: 1, pageSize: 50 })
        this.list = result.records || []
      } catch (e) {
        console.error('[微光] 感谢信加载失败', e)
        this.list = []
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style lang="scss">
.wg-thanks-text {
  display: block;
  margin-top: 8px;
  font-size: var(--wg-font-base);
  line-height: 1.6;
  color: var(--wg-text);
}
</style>
