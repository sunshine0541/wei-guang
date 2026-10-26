<template>
  <view class="wg-page wg-page-tab" :class="userStore.rootClass">
    <view class="wg-card wg-card-tint" style="background-color:#d9e2d6;color:#4f6b4a">
      <text class="wg-title">社区</text>
      <text class="wg-label" style="color:#4f6b4a;opacity:.8">邻里之间，互相照亮</text>
    </view>

    <view class="wg-card">
      <view class="wg-stat-row">
        <view class="wg-stat">
          <text class="wg-bignum">{{ todayOrderCount }}</text>
          <text class="wg-label">今日新求助</text>
        </view>
        <view class="wg-stat">
          <text class="wg-bignum">{{ lightPassedCount }}</text>
          <text class="wg-label">束光已传递</text>
        </view>
      </view>
    </view>

    <view class="wg-card">
      <view class="wg-card-head">
        <text class="wg-subtitle">助人之星 · 近 30 天</text>
      </view>
      <view v-if="honorList.length === 0" class="wg-label">还没有助人之星，等第一束光被点亮</view>
      <view v-for="(item, index) in honorList" :key="index" class="wg-row">
        <text class="wg-honor-rank">{{ index + 1 }}</text>
        <WgImage
          v-if="item.avatarUrl"
          class="wg-honor-avatar"
          :src="item.avatarUrl"
          :width="32"
          :height="32"
          mode="aspectFill"
        />
        <text class="wg-honor-name">{{ item.nickname || '邻居' }}</text>
        <text class="wg-strong">{{ item.helpCount }} 次</text>
      </view>
    </view>

    <view class="wg-card">
      <view class="wg-card-head">
        <text class="wg-subtitle">邻里动态</text>
      </view>
      <view v-if="feedLoading && feed.length === 0" class="wg-label">正在为您准备，请稍等</view>
      <view v-else-if="feed.length === 0" class="wg-empty">社区还没有动态，等第一束光被点亮</view>
      <view v-for="item in feed" :key="item.orderId" class="wg-feed-item" @click="goNeighbor(item)">
        <view class="wg-feed-head">
          <WgImage
            v-if="item.helperAvatarUrl"
            class="wg-honor-avatar"
            :src="item.helperAvatarUrl"
            :width="36"
            :height="36"
            mode="aspectFill"
          />
          <view class="wg-feed-title">
            <text class="wg-feed-name">{{ feedAction(item) }}</text>
            <text class="wg-feed-order">{{ item.title }}</text>
          </view>
        </view>
        <view v-if="item.tags && item.tags.length" class="wg-tags">
          <text v-for="tag in item.tags" :key="tag" class="wg-tag">{{ tag }}</text>
        </view>
        <text class="wg-label">{{ formatTime(item.completedAt) }}</text>
      </view>
      <view v-if="feed.length && feedLoading" class="wg-feed-more wg-label">正在加载…</view>
      <view v-else-if="feed.length && feedFinished" class="wg-feed-more wg-label">已经到底啦</view>
    </view>

    <WgTabBar :active="2" />
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { getHomeSummary } from '@/api/home'
import { listCommunityFeed } from '@/api/community'
import WgImage from '@/components/WgImage.vue'
import WgTabBar from '@/components/WgTabBar.vue'

const PAGE_SIZE = 10

export default {
  components: { WgImage, WgTabBar },
  data() {
    return {
      honorList: [],
      todayOrderCount: 0,
      lightPassedCount: 0,
      feed: [],
      feedPage: 0,
      feedLoading: false,
      feedFinished: false,
      entered: false
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    }
  },
  onLoad() {
    this.refresh()
  },
  onShow() {
    if (this.entered) {
      this.refresh()
    }
    this.entered = true
  },
  onReachBottom() {
    this.loadFeed(false)
  },
  methods: {
    async refresh() {
      await Promise.all([this.loadSummary(), this.loadFeed(true)])
    },
    async loadSummary() {
      try {
        const summary = await getHomeSummary()
        this.honorList = summary.honorTop10 || []
        this.lightPassedCount = summary.lightPassedCount || 0
        this.todayOrderCount = summary.todayOrderCount || 0
      } catch (e) {
        console.error('[微光] 社区聚合加载失败', e)
      }
    },
    async loadFeed(reset) {
      if (this.feedLoading || (!reset && this.feedFinished)) {
        return
      }
      if (reset) {
        this.feed = []
        this.feedPage = 0
        this.feedFinished = false
      }
      this.feedLoading = true
      const page = this.feedPage + 1
      try {
        const result = await listCommunityFeed({ page, pageSize: PAGE_SIZE })
        const records = result.records || []
        this.feed = reset ? records : this.feed.concat(records)
        this.feedPage = page
        const total = result.total || 0
        this.feedFinished = records.length < PAGE_SIZE || this.feed.length >= total
      } catch (e) {
        console.error('[微光] 邻里动态加载失败', e)
      } finally {
        this.feedLoading = false
      }
    },
    feedAction(item) {
      const helper = item.helperNickname || '一位邻居'
      const requester = item.requesterNickname || '邻居'
      return `${helper} 帮助了 ${requester}`
    },
    formatTime(value) {
      return value ? String(value).replace('T', ' ').slice(0, 16) : ''
    },
    goNeighbor(item) {
      if (item.helperId) {
        uni.navigateTo({ url: `/pages/neighbor/profile?id=${item.helperId}` })
      }
    }
  }
}
</script>

<style lang="scss">
.wg-stat-row {
  display: flex;
  align-items: center;
}

.wg-stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.wg-bignum {
  font-size: calc(var(--wg-font-title) + 6px);
  font-weight: 800;
  color: var(--wg-primary-deep);
}

.wg-honor-rank {
  width: 26px;
  color: var(--wg-primary-deep);
  font-weight: 800;
  flex-shrink: 0;
}

.wg-honor-avatar {
  margin-right: 8px;
  background-color: var(--wg-surface3);
}

.wg-honor-name {
  flex: 1;
}

.wg-feed-item {
  padding: 12px 0;
  border-bottom: 1px dashed #e7e2d8;
}

.wg-feed-item:last-child {
  border-bottom: none;
}

.wg-feed-head {
  display: flex;
  align-items: flex-start;
}

.wg-feed-title {
  flex: 1;
  min-width: 0;
}

.wg-feed-name {
  display: block;
  font-weight: 700;
  color: var(--wg-text);
}

.wg-feed-order {
  display: block;
  margin-top: 2px;
  color: var(--wg-text-soft);
}

.wg-feed-more {
  margin-top: 10px;
  text-align: center;
}
</style>
