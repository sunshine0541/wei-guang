<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view v-if="loading" class="wg-card">
      <text>正在为您准备，请稍等</text>
    </view>

    <template v-else-if="profile">
      <view class="wg-card wg-neighbor-head">
        <WgImage v-if="profile.avatarUrl" class="wg-neighbor-avatar" :src="profile.avatarUrl" :width="88" :height="88" mode="aspectFill" />
        <text class="wg-title">{{ profile.nickname || '邻居' }}</text>
        <text v-if="profile.signature" class="wg-label">{{ profile.signature }}</text>
      </view>

      <view class="wg-card">
        <view class="wg-card-head">
          <text class="wg-label">收到的评价</text>
          <view class="wg-neighbor-avg">
            <u-icon name="star-fill" :size="16" />
            <text class="wg-bignum wg-neighbor-avg-text">{{ avgText }}</text>
          </view>
        </view>
      </view>

      <view v-if="evaluations.length === 0" class="wg-empty">{{ emptyText }}</view>
      <view v-else class="wg-rows">
        <view v-for="(item, index) in evaluations" :key="item.createdAt + '-' + index" class="wg-row wg-neighbor-item">
          <view class="wg-neighbor-item-head">
            <view class="wg-neighbor-stars">
              <u-icon v-for="n in item.stars" :key="n" name="star-fill" :size="14" />
            </view>
            <text class="wg-label">{{ item.requesterNickname || '邻居' }} · {{ item.createdAt }}</text>
          </view>
          <text v-if="item.advice" class="wg-neighbor-advice">{{ item.advice }}</text>
        </view>
      </view>

      <view class="wg-card">
        <text class="wg-subtitle">{{ profile.nickname || '邻居' }} 收到的感谢</text>
        <view v-if="profile.thanks.length === 0" class="wg-empty" style="margin-top: 8px">{{ emptyText }}</view>
        <view v-for="item in profile.thanks" :key="item.letterId" class="wg-thanks-item">
          <view class="wg-label">{{ item.senderNickname || '邻居' }} · {{ item.title }}</view>
          <text>{{ item.content }}</text>
        </view>
      </view>
    </template>

    <view v-else class="wg-card">
      <text>这个页面暂时不能看哦</text>
      <view class="wg-label">可能是网络问题，请稍后重试</view>
      <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="load">重新加载</view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { getNeighborEvaluations, getNeighborProfile } from '@/api/incentive'
import { MESSAGES } from '@/utils/constants'
import WgImage from '@/components/WgImage.vue'

export default {
  components: { WgImage },
  data() {
    return {
      loading: true,
      userId: null,
      profile: null,
      evaluations: [],
      emptyText: MESSAGES.EMPTY
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    avgText() {
      if (!this.profile || this.profile.evaluationAvg === null || this.profile.evaluationAvg === undefined) {
        return '暂无评价'
      }
      return `${this.profile.evaluationAvg} 分 · ${this.profile.evaluationCount} 条`
    }
  },
  onLoad(options) {
    this.userId = options.id
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        this.profile = await getNeighborProfile(this.userId)
      } catch (e) {
        console.error('[微光] 邻居主页加载失败', e)
        this.profile = null
        this.loading = false
        return
      }
      // 评价失败只清空列表，不把已经拿到的昵称/感谢一起丢掉
      try {
        const result = await getNeighborEvaluations(this.userId, { page: 1, pageSize: 20 })
        this.evaluations = result.records || []
      } catch (e) {
        console.error('[微光] 邻居评价加载失败', e)
        this.evaluations = []
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style lang="scss">
.wg-neighbor-head {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.wg-neighbor-avatar {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  margin-bottom: 8px;
  background-color: var(--sg-sand);
}

.wg-neighbor-avg {
  display: flex;
  align-items: center;
}

.wg-neighbor-avg-text {
  margin-left: 6px;
}

/* 评价列表项：复用 wg-row 容器，纵向排布 */
.wg-rows .wg-neighbor-item {
  flex-direction: column;
  align-items: stretch;
  justify-content: flex-start;
}

.wg-neighbor-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.wg-neighbor-stars {
  display: flex;
  color: var(--sg-sand-d);
}

.wg-neighbor-advice {
  font-size: var(--wg-font-base);
  line-height: 1.6;
}

.wg-thanks-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--wg-line);
}

.wg-thanks-item:last-child {
  border-bottom: none;
}
</style>