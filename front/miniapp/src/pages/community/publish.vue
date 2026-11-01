<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view class="wg-card">
      <view class="wg-label">发到哪里</view>
      <view class="wg-seg" style="margin-top: 8px">
        <view
          class="wg-seg-item"
          :class="{ 'wg-seg-item-active': category === 'NORMAL' }"
          @click="category = 'NORMAL'"
        >
          邻里动态
        </view>
        <view
          class="wg-seg-item"
          :class="{ 'wg-seg-item-active': category === 'STORY' }"
          @click="category = 'STORY'"
        >
          互助故事
        </view>
      </view>
    </view>

    <view class="wg-card">
      <view class="wg-label">标题（选填，最多 50 字）</view>
      <input class="wg-input" maxlength="50" :value="title" @input="title = $event.detail.value" placeholder="给这条动态起个标题" />

      <view class="wg-label" style="margin-top: 12px">想和邻居说的话</view>
      <textarea
        class="wg-textarea"
        maxlength="500"
        :value="content"
        @input="content = $event.detail.value"
        placeholder="分享身边的小事、互助故事，或给邻居提个醒"
      />
      <view class="wg-count">{{ content.length }}/500</view>

      <view class="wg-label" style="margin-top: 12px">配图（选填，最多 3 张）</view>
      <view class="wg-post-images">
        <WgImage
          v-for="(img, index) in images"
          :key="index"
          class="wg-post-image"
          :src="img"
          :width="100"
          :height="100"
          mode="aspectFill"
        />
        <view v-if="images.length < 3" class="wg-add-image" @click="pickImage">＋</view>
      </view>

      <view class="wg-label" style="margin-top: 12px">
        请分享邻里间的真实小事，不要填写密码、银行卡、身份证号等私人信息。
      </view>
    </view>

    <view class="wg-btn" :class="{ 'wg-btn-off': !content.trim() || submitting }" @click="submit">
      {{ submitting ? '正在发布…' : '发布' }}
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { createPost } from '@/api/community'
import { uploadImage } from '@/utils/upload'
import WgImage from '@/components/WgImage.vue'

export default {
  components: { WgImage },
  data() {
    return {
      category: 'NORMAL',
      title: '',
      content: '',
      images: [],
      submitting: false
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    }
  },
  methods: {
    async pickImage() {
      const remain = 3 - this.images.length
      if (remain <= 0) {
        return
      }
      uni.chooseImage({
        count: remain,
        success: async (res) => {
          const files = res.tempFilePaths || []
          for (const file of files) {
            try {
              const url = await uploadImage(file)
              this.images.push(url)
            } catch (e) {
              console.error('[微光] 图片上传失败', e)
            }
          }
        }
      })
    },
    async submit() {
      if (!this.content.trim() || this.submitting) {
        return
      }
      this.submitting = true
      try {
        const result = await createPost({
          category: this.category,
          title: this.title.trim() || null,
          content: this.content.trim(),
          images: this.images
        })
        const status = result && result.status
        const message = status === 'PENDING_MANUAL'
          ? '已提交，需要人工确认一下'
          : status === 'PUBLISHED'
            ? '已发布，邻居们可以看到啦'
            : '已发布，审核通过后邻居们就能看到'
        uni.showToast({ title: message, icon: 'none', duration: 2500 })
        setTimeout(() => {
          uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/community/index' }) })
        }, 800)
      } catch (e) {
        console.error('[微光] 发布动态失败', e)
        this.submitting = false
      }
    }
  }
}
</script>

<style lang="scss">
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

.wg-add-image {
  width: 100px;
  height: 100px;
  line-height: 96px;
  text-align: center;
  font-size: 30px;
  color: var(--wg-text-soft);
  border: 2px dashed var(--wg-line);
  border-radius: var(--wg-radius);
  background-color: var(--wg-surface);
}
</style>
