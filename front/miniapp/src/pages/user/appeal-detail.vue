<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view v-if="!item" class="wg-card">
      <text>这个页面暂时不能看哦</text>
      <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="back">返回上一页</view>
    </view>

    <template v-else>
      <view class="wg-card">
        <view class="wg-appeal-detail-head">
          <text class="wg-tag" style="margin: 0">{{ item.type === 'FEEDBACK' ? '反馈' : '申诉' }}</text>
          <text class="wg-label">{{ statusLabel }}</text>
        </view>
        <view v-if="item.orderTitle" class="wg-label">关联求助：{{ item.orderTitle }}</view>
        <view class="wg-label">提交时间：{{ item.createdAt }}</view>
        <view v-if="item.resolvedAt" class="wg-label">处理时间：{{ item.resolvedAt }}</view>
      </view>

      <view class="wg-card">
        <view class="wg-label">{{ item.type === 'FEEDBACK' ? '反馈内容' : '申诉理由' }}</view>
        <view class="wg-appeal-detail-text">{{ item.reason }}</view>
        <WgImage
          v-if="item.evidenceUrl"
          class="wg-appeal-detail-img"
          :src="item.evidenceUrl"
          :width="96"
          :height="96"
          radius="8px"
          mode="aspectFill"
          @click="previewImage"
        />
      </view>

      <view class="wg-card">
        <view class="wg-label">处理结果</view>
        <view class="wg-appeal-detail-text">{{ item.conclusion || statusLabel }}</view>
      </view>

      <view class="wg-btn wg-btn-plain" @click="back">返回上一页</view>
    </template>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { STORAGE_KEYS } from '@/utils/constants'
import { resolveLocalImage } from '@/utils/image'
import WgImage from '@/components/WgImage.vue'

/**
 * P17 处理详情（B：查看处理详情）：数据由申诉/反馈列表点击时写入本地，
 * 避免为单条详情新增接口；没有数据时按无权访问处理。
 */
export default {
  components: { WgImage },
  data() {
    return {
      item: null
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    statusLabel() {
      if (!this.item) {
        return ''
      }
      if (this.item.status === 'WITHDRAWN') {
        return '已撤回'
      }
      return this.item.status === 'RESOLVED' ? '已处理' : '正在处理'
    }
  },
  onLoad() {
    this.item = uni.getStorageSync(STORAGE_KEYS.APPEAL_DETAIL) || null
  },
  methods: {
    async previewImage() {
      if (!this.item || !this.item.evidenceUrl) {
        return
      }
      const src = await resolveLocalImage(this.item.evidenceUrl)
      if (src) {
        uni.previewImage({ urls: [src] })
      }
    },
    back() {
      uni.navigateBack({
        fail: () => {
          uni.reLaunch({ url: '/pages/user/appeal' })
        }
      })
    }
  }
}
</script>

<style lang="scss">
.wg-appeal-detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.wg-appeal-detail-text {
  margin-top: 8px;
  line-height: 1.7;
  word-break: break-all;
}

.wg-appeal-detail-img {
  display: block;
  width: 96px;
  height: 96px;
  margin-top: 8px;
  border-radius: var(--wg-radius-sm);
  background-color: #f7f6f2;
}
</style>
