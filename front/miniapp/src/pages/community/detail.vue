<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view v-if="loading" class="wg-card">
      <view class="wg-skel-line" style="width: 60%"></view>
      <view class="wg-skel-line" style="width: 90%"></view>
    </view>

    <template v-else-if="post">
      <view v-if="post.status !== 'PUBLISHED'" class="wg-card wg-status-banner">
        <text class="wg-label">{{ statusText }}</text>
      </view>

      <view class="wg-card">
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
          <text v-if="!isAuthor" class="wg-report-link" @click="reportPost">举报</text>
        </view>

        <text v-if="post.title" class="wg-subtitle" style="margin-top: 10px">{{ post.title }}</text>
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
          <view class="wg-post-action" @click="toggleLike">
            <u-icon :name="post.liked ? 'thumb-up-fill' : 'thumb-up'" :size="18" />
            <text>{{ post.likeCount || 0 }}</text>
          </view>
          <view class="wg-post-action">
            <u-icon name="chat" :size="18" />
            <text>{{ post.commentCount || 0 }}</text>
          </view>
          <view v-if="isAuthor" class="wg-post-action" style="margin-left: auto" @click="removePost">
            <u-icon name="trash" :size="18" />
            <text>删除</text>
          </view>
        </view>
      </view>

      <view class="wg-card">
        <view class="wg-card-head">
          <text class="wg-subtitle">评论</text>
        </view>
        <view v-if="comments.length === 0" class="wg-label">还没有评论，来说一句暖心话吧</view>
        <view v-for="item in comments" :key="item.id" class="wg-comment">
          <view class="wg-comment-head">
            <text class="wg-comment-name">{{ item.authorNickname || '邻居' }}</text>
            <text class="wg-label">{{ formatTime(item.createdAt) }}</text>
          </view>
          <text class="wg-comment-content">{{ item.content }}</text>
          <view class="wg-comment-actions">
            <text v-if="item.authorId === myUid" class="wg-report-link" @click="removeComment(item)">删除</text>
            <text v-else class="wg-report-link" @click="reportComment(item)">举报</text>
          </view>
        </view>
        <view v-if="comments.length && commentsLoading" class="wg-feed-more wg-label">正在加载…</view>
        <view v-else-if="comments.length && commentsFinished" class="wg-feed-more wg-label">已经到底啦</view>
      </view>

      <view v-if="post.status === 'PUBLISHED'" class="wg-card">
        <textarea
          class="wg-textarea"
          maxlength="200"
          :value="commentText"
          @input="commentText = $event.detail.value"
          placeholder="说一句暖心话吧"
        />
        <view class="wg-count">{{ commentText.length }}/200</view>
        <view class="wg-btn" :class="{ 'wg-btn-off': !commentText.trim() || commenting }" @click="submitComment">
          {{ commenting ? '正在发送…' : '发表评论' }}
        </view>
      </view>
    </template>

    <view v-else class="wg-card">
      <text>这个页面暂时不能看哦</text>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import {
  getPost,
  listComments,
  createComment,
  deletePost,
  deleteComment,
  likePost,
  unlikePost,
  reportContent
} from '@/api/community'
import WgImage from '@/components/WgImage.vue'

const PAGE_SIZE = 10

export default {
  components: { WgImage },
  data() {
    return {
      postId: null,
      post: null,
      loading: true,
      comments: [],
      commentPage: 0,
      commentsLoading: false,
      commentsFinished: false,
      commentText: '',
      commenting: false
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    myUid() {
      return this.userStore.userId
    },
    isAuthor() {
      return this.post && this.myUid && this.post.authorId === this.myUid
    },
    statusText() {
      if (!this.post) {
        return ''
      }
      if (this.post.status === 'PENDING_MANUAL') {
        return '这条动态需要人工确认一下，请耐心等待'
      }
      if (this.post.status === 'REJECTED') {
        return '这条动态未通过审核，请修改后再发布'
      }
      if (this.post.status === 'HIDDEN') {
        return '这条动态已被管理员隐藏'
      }
      return '这条动态正在审核中'
    }
  },
  onLoad(options) {
    this.postId = options.id
    this.load()
  },
  onReachBottom() {
    this.loadComments(false)
  },
  methods: {
    async load() {
      this.loading = true
      try {
        this.post = await getPost(this.postId)
        if (this.post.status === 'PUBLISHED' || this.isAuthor) {
          this.loadComments(true)
        }
      } catch (e) {
        console.error('[微光] 动态详情加载失败', e)
        this.post = null
      } finally {
        this.loading = false
      }
    },
    async loadComments(reset) {
      if (this.commentsLoading || (!reset && this.commentsFinished) || !this.post) {
        return
      }
      if (reset) {
        this.comments = []
        this.commentPage = 0
        this.commentsFinished = false
      }
      this.commentsLoading = true
      const page = this.commentPage + 1
      try {
        const result = await listComments(this.postId, { page, pageSize: PAGE_SIZE })
        const records = result.records || []
        this.comments = reset ? records : this.comments.concat(records)
        this.commentPage = page
        const total = result.total || 0
        this.commentsFinished = records.length < PAGE_SIZE || this.comments.length >= total
      } catch (e) {
        console.error('[微光] 评论加载失败', e)
      } finally {
        this.commentsLoading = false
      }
    },
    async toggleLike() {
      if (!this.post) {
        return
      }
      const liked = !!this.post.liked
      this.post.liked = !liked
      this.post.likeCount = Math.max(0, (this.post.likeCount || 0) + (liked ? -1 : 1))
      try {
        if (liked) {
          await unlikePost(this.post.id)
        } else {
          await likePost(this.post.id)
        }
      } catch (e) {
        this.post.liked = liked
        this.post.likeCount = Math.max(0, (this.post.likeCount || 0) + (liked ? 1 : -1))
        console.error('[微光] 点赞失败', e)
      }
    },
    async submitComment() {
      const content = this.commentText.trim()
      if (!content || this.commenting) {
        return
      }
      this.commenting = true
      try {
        await createComment(this.postId, { content })
        this.commentText = ''
        uni.showToast({ title: '评论已提交', icon: 'none' })
        this.loadComments(true)
        this.post.commentCount = (this.post.commentCount || 0) + 1
      } catch (e) {
        console.error('[微光] 评论失败', e)
      } finally {
        this.commenting = false
      }
    },
    removePost() {
      uni.showModal({
        title: '删除动态',
        content: '确定删除这条动态吗？',
        success: async (res) => {
          if (!res.confirm) {
            return
          }
          try {
            await deletePost(this.postId)
            uni.showToast({ title: '已删除', icon: 'none' })
            setTimeout(() => uni.navigateBack(), 600)
          } catch (e) {
            console.error('[微光] 删除动态失败', e)
          }
        }
      })
    },
    removeComment(item) {
      uni.showModal({
        title: '删除评论',
        content: '确定删除这条评论吗？',
        success: async (res) => {
          if (!res.confirm) {
            return
          }
          try {
            await deleteComment(item.id)
            this.post.commentCount = Math.max(0, (this.post.commentCount || 0) - 1)
            this.loadComments(true)
          } catch (e) {
            console.error('[微光] 删除评论失败', e)
          }
        }
      })
    },
    reportPost() {
      this.askReport('POST', this.post.id)
    },
    reportComment(item) {
      this.askReport('COMMENT', item.id)
    },
    askReport(targetType, targetId) {
      uni.showModal({
        title: '举报内容',
        editable: true,
        placeholderText: '请简单说明原因',
        success: async (res) => {
          if (!res.confirm) {
            return
          }
          const reason = (res.content || '').trim()
          if (!reason) {
            uni.showToast({ title: '写一句原因吧', icon: 'none' })
            return
          }
          try {
            await reportContent({ targetType, targetId, reason })
            uni.showToast({ title: '已提交举报，管理员会尽快处理', icon: 'none' })
          } catch (e) {
            console.error('[微光] 举报失败', e)
          }
        }
      })
    },
    formatTime(value) {
      return value ? String(value).replace('T', ' ').slice(0, 16) : ''
    }
  }
}
</script>

<style lang="scss">
.wg-status-banner {
  background-color: #ede3d0;
  color: #8a7648;
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
  color: var(--wg-text-soft);
}

.wg-post-action text {
  margin-left: 4px;
}

.wg-report-link {
  color: var(--wg-text-soft);
  font-size: var(--wg-font-sm);
  text-decoration: underline;
}

.wg-comment {
  padding: 10px 0;
  border-bottom: 1px dashed #e7e2d8;
}

.wg-comment:last-child {
  border-bottom: none;
}

.wg-comment-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.wg-comment-name {
  font-weight: 700;
}

.wg-comment-content {
  display: block;
  margin-top: 4px;
  line-height: 1.6;
}

.wg-comment-actions {
  margin-top: 4px;
  text-align: right;
}

.wg-feed-more {
  margin-top: 10px;
  text-align: center;
}
</style>
