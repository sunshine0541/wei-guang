<template>
  <view class="wg-page wg-page-tab" :class="userStore.rootClass">
    <view class="wg-card wg-card-tint" style="background-color:#d9e2d6;color:#4f6b4a">
      <text class="wg-title">社区</text>
      <text class="wg-label" style="color:#4f6b4a;opacity:.8">邻里之间，互相照亮</text>
    </view>

    <!-- 顶部三块：助人之星 / 今日新求助 / 已成为微光人数 -->
    <view class="wg-card">
      <view class="wg-stat-row">
        <view class="wg-stat">
          <text class="wg-bignum">{{ summary.microLightCount || 0 }}</text>
          <text class="wg-label">人已成为微光</text>
        </view>
        <view class="wg-stat">
          <text class="wg-bignum">{{ summary.todayOrderCount || 0 }}</text>
          <text class="wg-label">今日新求助</text>
        </view>
      </view>
    </view>

    <view class="wg-card">
      <view class="wg-card-head">
        <text class="wg-subtitle">助人之星 · 近 30 天</text>
      </view>
      <view v-if="(summary.stars || []).length === 0" class="wg-label">还没有助人之星，等第一束光被点亮</view>
      <view v-for="(item, index) in summary.stars" :key="index" class="wg-row">
        <text class="wg-honor-rank">{{ index + 1 }}</text>
        <WgImage
          v-if="item.avatarUrl"
          class="wg-honor-avatar"
          :src="item.avatarUrl"
          :width="30"
          :height="30"
          mode="aspectFill"
        />
        <text class="wg-honor-name">{{ item.nickname || '邻居' }}</text>
        <text class="wg-strong">{{ item.helpCount }} 次</text>
      </view>
    </view>

    <!-- 发布入口：全宽按钮，替代原「今日新求助」冗余卡片 -->
    <view class="wg-post-publish" @click="goPublish">
      <u-icon name="edit-pen" :size="30" />
      <view class="wg-post-publish-text">
        <view class="wg-post-publish-title">我要发邻里动态</view>
        <view class="wg-post-publish-sub">说说身边的小事，邻居们都能看到</view>
      </view>
    </view>

    <!-- 三 Tab：邻里动态 / 互助故事 / 官方小贴士 -->
    <view class="wg-seg">
      <view
        v-for="item in tabs"
        :key="item.value"
        class="wg-seg-item"
        :class="{ 'wg-seg-item-active': tab === item.value }"
        @click="switchTab(item.value)"
      >
        {{ item.label }}
      </view>
    </view>

    <view class="wg-notice-lite">
      <u-icon name="info-circle" :size="16" />
      <text class="wg-label">这里是邻里分享，需要帮忙请去首页发布求助。</text>
    </view>

    <view v-if="loading && posts.length === 0" class="wg-card">
      <view class="wg-skel-line" style="width: 60%"></view>
      <view class="wg-skel-line" style="width: 90%"></view>
    </view>
    <view v-else-if="loadFailed" class="wg-card">
      <text>动态加载失败，请稍后重试</text>
      <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="loadPosts(true)">重新加载</view>
    </view>
    <view v-else-if="posts.length === 0" class="wg-empty">邻里还没有新动态，来说说身边的小事吧</view>

    <view v-for="post in posts" :key="post.id" class="wg-card wg-post" @click="openPost(post.id)">
      <view class="wg-post-head">
        <WgImage
          v-if="post.authorAvatarUrl"
          class="wg-honor-avatar"
          :src="post.authorAvatarUrl"
          :width="40"
          :height="40"
          mode="aspectFill"
        />
        <view v-else class="wg-post-avatar">{{ (post.authorNickname || '邻').slice(0, 1) }}</view>
        <view class="wg-post-author">
          <view class="wg-post-name-row">
            <text class="wg-post-name">{{ post.authorNickname || '邻居' }}</text>
            <text v-if="post.category === 'OFFICIAL'" class="wg-post-official">官方</text>
          </view>
          <text class="wg-label">{{ formatTime(post.createdAt) }}</text>
        </view>
      </view>

      <text v-if="post.title" class="wg-subtitle" style="margin-top: 8px">{{ post.title }}</text>
      <text class="wg-post-content">{{ post.content }}</text>

      <view v-if="post.images && post.images.length" class="wg-post-images">
        <WgImage
          v-for="(img, index) in post.images"
          :key="index"
          class="wg-post-image"
          :src="img"
          :width="100"
          :height="100"
          mode="aspectFill"
        />
      </view>

      <view class="wg-post-foot">
        <view class="wg-post-action" @click.stop="toggleLike(post)">
          <u-icon :name="post.liked ? 'thumb-up-fill' : 'thumb-up'" :size="18" />
          <text>{{ post.likeCount || 0 }}</text>
        </view>
        <view class="wg-post-action">
          <u-icon name="chat" :size="18" />
          <text>{{ post.commentCount || 0 }}</text>
        </view>
      </view>
    </view>

    <view v-if="posts.length && loading" class="wg-feed-more wg-label">正在加载…</view>
    <view v-else-if="posts.length && finished" class="wg-feed-more wg-label">已经到底啦</view>

    <WgTabBar :active="2" />
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { getCommunitySummary, listPosts, likePost, unlikePost } from '@/api/community'
import WgImage from '@/components/WgImage.vue'
import WgTabBar from '@/components/WgTabBar.vue'

const PAGE_SIZE = 10

export default {
  components: { WgImage, WgTabBar },
  data() {
    return {
      tabs: [
        { label: '邻里动态', value: 'NEIGHBOR' },
        { label: '互助故事', value: 'STORY' },
        { label: '官方小贴士', value: 'OFFICIAL' }
      ],
      tab: 'NEIGHBOR',
      summary: { stars: [], todayOrders: [], microLightCount: 0 },
      posts: [],
      page: 0,
      loading: false,
      loadFailed: false,
      finished: false,
      entered: false,
      postSeq: 0
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    }
  },
  onLoad() {
    this.hideHome()
    this.refresh()
  },
  onShow() {
    this.hideHome()
    if (this.entered) {
      this.refresh()
    }
    this.entered = true
  },
  onReachBottom() {
    this.loadPosts(false)
  },
  methods: {
    /** 隐藏微信自动加在左上角的「返回首页⌂」，避免与底部导航重复 */
    hideHome() {
      if (typeof wx !== 'undefined' && typeof wx.hideHomeButton === 'function') {
        wx.hideHomeButton()
      }
    },
    async refresh() {
      await Promise.all([this.loadSummary(), this.loadPosts(true)])
    },
    async loadSummary() {
      try {
        const data = await getCommunitySummary()
        this.summary = data || { stars: [], todayOrders: [], microLightCount: 0 }
      } catch (e) {
        console.error('[微光] 社区聚合加载失败', e)
      }
    },
    switchTab(tab) {
      if (this.tab === tab) {
        return
      }
      this.tab = tab
      this.loadPosts(true)
    },
    async loadPosts(reset) {
      if (!reset && (this.loading || this.finished)) {
        return
      }
      if (reset) {
        this.posts = []
        this.page = 0
        this.finished = false
        this.loadFailed = false
      }
      const seq = ++this.postSeq
      this.loading = true
      const page = reset ? 1 : this.page + 1
      try {
        const result = await listPosts({ tab: this.tab, page, pageSize: PAGE_SIZE })
        if (seq !== this.postSeq) {
          return
        }
        const records = result.records || []
        this.posts = reset ? records : this.posts.concat(records)
        this.page = page
        const total = result.total || 0
        this.finished = records.length < PAGE_SIZE || this.posts.length >= total
      } catch (e) {
        console.error('[微光] 社区动态加载失败', e)
        if (reset && seq === this.postSeq) {
          this.loadFailed = true
        }
      } finally {
        if (seq === this.postSeq) {
          this.loading = false
        }
      }
    },
    async toggleLike(post) {
      const liked = !!post.liked
      post.liked = !liked
      post.likeCount = Math.max(0, (post.likeCount || 0) + (liked ? -1 : 1))
      try {
        if (liked) {
          await unlikePost(post.id)
        } else {
          await likePost(post.id)
        }
      } catch (e) {
        post.liked = liked
        post.likeCount = Math.max(0, (post.likeCount || 0) + (liked ? 1 : -1))
        console.error('[微光] 点赞失败', e)
      }
    },
    openPost(id) {
      uni.navigateTo({ url: `/pages/community/detail?id=${id}` })
    },
    goPublish() {
      uni.navigateTo({ url: '/pages/community/publish' })
    },
    formatTime(value) {
      return value ? String(value).replace('T', ' ').slice(0, 16) : ''
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

.wg-notice-lite {
  display: flex;
  align-items: center;
  padding: 6px 0 10px;
}

.wg-notice-lite .wg-label {
  margin-left: 6px;
}

.wg-post {
  margin-bottom: 12px;
}

.wg-post-head {
  display: flex;
  align-items: center;
}

.wg-post-avatar {
  width: 40px;
  height: 40px;
  line-height: 40px;
  margin-right: 8px;
  border-radius: 20px;
  text-align: center;
  font-weight: 800;
  color: #4f6b4a;
  background-color: #d9e2d6;
}

.wg-post-author {
  flex: 1;
  min-width: 0;
}

.wg-post-name-row {
  display: flex;
  align-items: center;
}

.wg-post-name {
  font-weight: 700;
}

.wg-post-official {
  margin-left: 6px;
  padding: 0 6px;
  border-radius: 6px;
  font-size: var(--wg-font-sm);
  color: #8a7648;
  background-color: #ede3d0;
}

.wg-post-content {
  display: block;
  margin-top: 8px;
  line-height: 1.7;
  color: var(--wg-text);
  white-space: pre-wrap;
}

.wg-post-images {
  display: flex;
  flex-wrap: wrap;
  margin-top: 8px;
}

.wg-post-image {
  margin: 0 8px 8px 0;
  border-radius: var(--wg-radius);
  background-color: var(--wg-surface3);
}

.wg-post-foot {
  display: flex;
  align-items: center;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #e7e2d8;
}

.wg-post-action {
  display: flex;
  align-items: center;
  margin-right: 22px;
  padding: 10px 0;
  color: var(--wg-text-soft);
}

.wg-post-action text {
  margin-left: 4px;
}

.wg-post-publish {
  display: flex;
  align-items: center;
  padding: 18px;
  margin-bottom: 12px;
  border-radius: var(--wg-radius);
  color: #4f6b4a;
  background-color: #d9e2d6;
}

.wg-post-publish-text {
  margin-left: 12px;
}

.wg-post-publish-title {
  font-size: var(--wg-font-base);
  font-weight: 800;
}

.wg-post-publish-sub {
  margin-top: 4px;
  opacity: 0.85;
}

.wg-feed-more {
  margin-top: 10px;
  text-align: center;
}
</style>
