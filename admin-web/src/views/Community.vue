<template>
  <div class="wg-page">
    <div class="wg-toolbar">
      <p>共 <b>{{ total }}</b> 条动态<span v-if="query.status"> · 当前筛选「{{ STATUS_LABELS[query.status] }}」</span></p>
      <div class="toolbar-actions">
        <button class="wg-ghost-btn" :disabled="loading" @click="load(query.page)">
          <AdminIcon name="refresh" :size="14" />刷新
        </button>
        <button class="primary-btn" @click="openOfficial">
          <AdminIcon name="message" :size="14" />发布官方动态
        </button>
      </div>
    </div>

    <div class="audit-grid">
      <aside class="queue-panel wg-panel">
        <div class="queue-filters">
          <div class="wg-chips queue-chips">
            <button class="wg-chip" :class="{ active: !query.status }" @click="pickStatus('')">全部</button>
            <button
              v-for="(label, value) in STATUS_LABELS"
              :key="value"
              class="wg-chip"
              :class="{ active: query.status === value }"
              @click="pickStatus(value)"
            >
              {{ label }}
            </button>
          </div>
          <div class="filter-row">
            <el-select v-model="query.category" class="cat-select" placeholder="全部分类" clearable @change="load(1)">
              <el-option v-for="(label, value) in CATEGORY_LABELS" :key="value" :label="label" :value="value" />
            </el-select>
            <el-input
              v-model="query.keyword"
              class="kw-input"
              placeholder="正文关键词"
              clearable
              @keyup.enter="load(1)"
              @clear="load(1)"
            />
            <button class="wg-text-btn" @click="reset">重置</button>
          </div>
        </div>

        <div v-loading="loading" class="queue-list">
          <div
            v-for="row in list"
            :key="row.id"
            class="queue-item"
            :class="{ active: selectedId === row.id }"
            @click="openDetail(row)"
          >
            <div class="q-top">
              <span class="q-author">{{ row.authorNickname || '匿名邻居' }}</span>
              <span class="q-time">{{ formatShort(row.createdAt) }}</span>
            </div>
            <p class="q-title">{{ row.title || row.content }}</p>
            <p class="q-content">{{ row.content }}</p>
            <div class="q-bottom">
              <span class="wg-pill" :class="'is-' + postStatusTone(row.status)">
                {{ STATUS_LABELS[row.status] || row.status }}
              </span>
              <span class="q-cat">{{ CATEGORY_LABELS[row.category] || row.category }}</span>
              <span class="q-stat">{{ row.likeCount || 0 }} 赞 · {{ row.commentCount || 0 }} 评</span>
            </div>
          </div>
          <div v-if="!list.length && !loading" class="queue-empty">没有符合条件的动态</div>
        </div>

        <div class="queue-pager">
          <el-pagination
            layout="prev, pager, next"
            :total="total"
            :page-size="query.pageSize"
            :current-page="query.page"
            @current-change="load"
          />
        </div>
      </aside>

      <section class="detail-panel wg-panel" v-loading="detailLoading">
        <template v-if="detail.post">
          <div class="detail-head">
            <div class="detail-title-wrap">
              <div class="detail-title-row">
                <h2>{{ detail.post.title || '（无标题动态）' }}</h2>
                <span class="wg-pill" :class="'is-' + postStatusTone(detail.post.status)">
                  {{ STATUS_LABELS[detail.post.status] || detail.post.status }}
                </span>
              </div>
              <p class="detail-meta">
                #{{ detail.post.id }} · {{ detail.post.authorNickname || '匿名邻居' }} · {{ CATEGORY_LABELS[detail.post.category] || detail.post.category }} · {{ formatDateTime(detail.post.createdAt) }}
              </p>
            </div>
            <div class="detail-actions">
              <el-button
                v-if="detail.post.status === 'PENDING_MANUAL' || detail.post.status === 'PENDING_REVIEW'"
                type="primary"
                size="small"
                :loading="actionLoading"
                @click="openReview('APPROVE')"
              >
                通过
              </el-button>
              <el-button
                v-if="detail.post.status === 'PENDING_MANUAL' || detail.post.status === 'PENDING_REVIEW'"
                size="small"
                :loading="actionLoading"
                @click="openReview('REJECT')"
              >
                拒绝
              </el-button>
              <el-button
                v-if="detail.post.status === 'PUBLISHED'"
                size="small"
                :loading="actionLoading"
                @click="hidePost"
              >
                隐藏
              </el-button>
              <el-button
                v-if="detail.post.status === 'HIDDEN'"
                type="success"
                size="small"
                :loading="actionLoading"
                @click="restorePost"
              >
                恢复
              </el-button>
              <el-button
                v-if="detail.post.status !== 'DELETED'"
                type="danger"
                size="small"
                plain
                :loading="actionLoading"
                @click="removePost"
              >
                删除
              </el-button>
            </div>
          </div>

          <div v-if="detail.post.reviewReason" class="review-reason">
            <AdminIcon name="alert" :size="14" />
            <span>审核说明：{{ detail.post.reviewReason }}</span>
            <span v-if="detail.post.reviewDegraded === 1" class="wg-pill is-amber no-dot">AI 降级放行</span>
          </div>

          <div class="content-block">
            <p>{{ detail.post.content }}</p>
            <div v-if="(detail.post.images || []).length" class="image-hint">
              含 {{ detail.post.images.length }} 张图片（管理端暂不预览，请在用户端核对）
            </div>
          </div>

          <div class="stat-strip">
            <span>{{ detail.post.likeCount || 0 }} 赞</span>
            <span>{{ detail.post.commentCount || 0 }} 评论</span>
            <span>{{ (detail.reports || []).length }} 条关联举报</span>
          </div>

          <section class="sub-section">
            <div class="sub-head">
              <h3>评论（{{ (detail.comments || []).length }}）</h3>
            </div>
            <div v-if="(detail.comments || []).length" class="comment-list">
              <div v-for="row in detail.comments" :key="row.id" class="comment-item">
                <div class="comment-main">
                  <div class="comment-top">
                    <span class="comment-author">{{ row.authorNickname || '匿名' }}</span>
                    <span class="comment-time">{{ formatShort(row.createdAt) }}</span>
                    <span v-if="row.status !== 'PUBLISHED'" class="wg-pill is-amber no-dot">
                      {{ row.status === 'PENDING_MANUAL' || row.status === 'PENDING_REVIEW' ? '待审核' : row.status }}
                    </span>
                  </div>
                  <p>{{ row.content }}</p>
                </div>
                <div class="comment-actions">
                  <span
                    v-if="row.status === 'PENDING_MANUAL' || row.status === 'PENDING_REVIEW'"
                    class="wg-link"
                    @click="approveComment(row)"
                  >
                    通过
                  </span>
                  <span class="wg-link danger" @click="removeComment(row)">删除</span>
                </div>
              </div>
            </div>
            <p v-else class="muted">暂无评论</p>
          </section>

          <section class="sub-section">
            <div class="sub-head">
              <h3>关联举报（{{ (detail.reports || []).length }}）</h3>
            </div>
            <div v-if="(detail.reports || []).length" class="report-list">
              <div v-for="row in detail.reports" :key="row.id" class="report-item">
                <div class="report-main">
                  <div class="report-top">
                    <span class="wg-pill no-dot is-gray">{{ targetTypeLabel(row.targetType) }}</span>
                    <span class="wg-pill" :class="'is-' + reportStatusTone(row.status)">
                      {{ REPORT_STATUS_LABELS[row.status] || row.status }}
                    </span>
                    <span class="comment-time">{{ formatShort(row.createdAt) }}</span>
                  </div>
                  <p>{{ row.evidence || '—' }}</p>
                </div>
                <span v-if="row.status === 'PENDING'" class="wg-link" @click="goHandleReport(row)">去处置 →</span>
                <span v-else class="muted">已处理</span>
              </div>
            </div>
            <p v-else class="muted">暂无关联举报</p>
          </section>
        </template>
        <el-empty v-else-if="!detailLoading" description="从左侧选择一条动态查看详情" />
      </section>
    </div>

    <el-dialog v-model="reviewVisible" :title="reviewForm.result === 'APPROVE' ? '通过动态' : '拒绝动态'" width="480px">
      <el-form label-width="70px">
        <el-form-item label="备注">
          <el-input
            v-model="reviewForm.note"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="可选：审核说明"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReview">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="officialVisible" title="发布官方动态" width="560px">
      <el-form label-width="70px">
        <el-form-item label="标题">
          <el-input v-model="officialForm.title" maxlength="50" placeholder="选填" />
        </el-form-item>
        <el-form-item label="正文" required>
          <el-input v-model="officialForm.content" type="textarea" :rows="5" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="officialVisible = false">取消</el-button>
        <el-button type="primary" @click="submitOfficial">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminIcon from '../components/AdminIcon.vue'
import {
  listCommunityPosts,
  getCommunityPost,
  reviewCommunityPost,
  hideCommunityPost,
  restoreCommunityPost,
  deleteCommunityPost,
  createOfficialPost,
  reviewCommunityComment,
  deleteCommunityComment
} from '../api/admin'
import { REPORT_STATUS_LABELS } from '../utils/labels'
import { formatDateTime, formatShort } from '../utils/format'
import { postStatusTone, reportStatusTone } from '../utils/status'

const route = useRoute()
const router = useRouter()

const STATUS_LABELS = {
  PENDING_REVIEW: '审核中',
  PENDING_MANUAL: '待人工审核',
  PUBLISHED: '已公开',
  HIDDEN: '已隐藏',
  REJECTED: '未通过',
  DELETED: '已删除'
}
const CATEGORY_LABELS = { NORMAL: '邻里动态', STORY: '互助故事', OFFICIAL: '官方小贴士' }

const query = reactive({ status: '', category: '', keyword: '', page: 1, pageSize: 10 })
const list = ref([])
const total = ref(0)
const loading = ref(false)
const selectedId = ref(null)
const detailLoading = ref(false)
const detail = reactive({ post: null, comments: [], reports: [] })
const actionLoading = ref(false)

const reviewVisible = ref(false)
const reviewForm = reactive({ id: null, result: 'APPROVE', note: '' })
const officialVisible = ref(false)
const officialForm = reactive({ title: '', content: '' })

async function load(page = 1) {
  query.page = page
  loading.value = true
  try {
    const data = await listCommunityPosts({
      status: query.status || undefined,
      category: query.category || undefined,
      keyword: query.keyword || undefined,
      page: query.page,
      pageSize: query.pageSize
    })
    list.value = data.records || []
    total.value = data.total || 0
    if (!list.value.find((item) => item.id === selectedId.value)) {
      if (list.value.length) {
        await openDetail(list.value[0])
      } else {
        selectedId.value = null
        detail.post = null
        detail.comments = []
        detail.reports = []
      }
    }
  } catch (e) {
    // 请求层已提示，保留旧列表
  } finally {
    loading.value = false
  }
}

function pickStatus(status) {
  query.status = status
  load(1)
}

function reset() {
  query.status = ''
  query.category = ''
  query.keyword = ''
  load(1)
}

async function openDetail(row) {
  selectedId.value = row.id
  detailLoading.value = true
  try {
    const data = await getCommunityPost(row.id)
    detail.post = data.post
    detail.comments = data.comments || []
    detail.reports = data.reports || []
  } catch (e) {
    selectedId.value = null
    detail.post = null
  } finally {
    detailLoading.value = false
  }
}

async function refreshDetail() {
  if (!selectedId.value) return
  await openDetail({ id: selectedId.value })
}

async function runAction(fn, okText) {
  if (actionLoading.value) return false
  actionLoading.value = true
  try {
    await fn()
    if (okText) ElMessage.success(okText)
    return true
  } catch (e) {
    return false
  } finally {
    actionLoading.value = false
  }
}

async function openReview(result) {
  reviewForm.id = detail.post.id
  reviewForm.result = result
  reviewForm.note = ''
  reviewVisible.value = true
}

async function submitReview() {
  const ok = await runAction(
    () => reviewCommunityPost(reviewForm.id, { result: reviewForm.result, note: reviewForm.note || null }),
    reviewForm.result === 'APPROVE' ? '已通过' : '已拒绝'
  )
  if (!ok) return
  reviewVisible.value = false
  await refreshDetail()
  await load(query.page)
}

async function hidePost() {
  if (await runAction(() => hideCommunityPost(detail.post.id), '已隐藏')) {
    await refreshDetail()
    await load(query.page)
  }
}

async function restorePost() {
  if (await runAction(() => restoreCommunityPost(detail.post.id), '已恢复')) {
    await refreshDetail()
    await load(query.page)
  }
}

async function removePost() {
  const confirmed = await ElMessageBox.confirm('删除后用户端不再展示，确定吗？', '删除动态', { type: 'warning' })
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return
  if (await runAction(() => deleteCommunityPost(detail.post.id), '已删除')) {
    selectedId.value = null
    detail.post = null
    await load(query.page)
  }
}

async function approveComment(row) {
  if (await runAction(() => reviewCommunityComment(row.id, { result: 'APPROVE', note: null }), '评论已通过')) {
    await refreshDetail()
    await load(query.page)
  }
}

async function removeComment(row) {
  const confirmed = await ElMessageBox.confirm('删除这条评论吗？', '删除评论', { type: 'warning' })
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return
  if (await runAction(() => deleteCommunityComment(row.id), '评论已删除')) {
    await refreshDetail()
    await load(query.page)
  }
}

function openOfficial() {
  officialForm.title = ''
  officialForm.content = ''
  officialVisible.value = true
}

async function submitOfficial() {
  if (!officialForm.content.trim()) {
    ElMessage.warning('请填写正文')
    return
  }
  const ok = await runAction(
    () => createOfficialPost({ title: officialForm.title || null, content: officialForm.content }),
    '官方动态已发布'
  )
  if (!ok) return
  officialVisible.value = false
  load(1)
}

function targetTypeLabel(type) {
  return { POST: '动态', COMMENT: '评论', ORDER: '订单' }[type] || type
}

function goHandleReport(row) {
  router.push({ path: '/reports', query: { reportId: row.id } })
}

onMounted(async () => {
  await load(1)
  const postId = Number(route.query.postId)
  if (postId) {
    await openDetail({ id: postId })
  }
})
</script>

<style scoped>
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.primary-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border: none;
  border-radius: 8px;
  background: var(--wg-clay);
  color: #fff6ec;
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
}

.primary-btn:hover {
  background: var(--wg-clay-deep);
}

.audit-grid {
  display: grid;
  grid-template-columns: 400px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.queue-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.queue-filters {
  padding: 13px 14px 10px;
  border-bottom: 1px solid #f0e9de;
}

.queue-chips {
  flex-wrap: wrap;
  overflow: visible;
  gap: 8px;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.cat-select {
  width: 112px;
  flex-shrink: 0;
}

.kw-input {
  flex: 1;
}

.queue-list {
  min-height: 240px;
  max-height: calc(100vh - 330px);
  overflow-y: auto;
}

.queue-item {
  padding: 12px 14px;
  border-bottom: 1px solid #f5efe5;
  cursor: pointer;
}

.queue-item:hover {
  background: #fbf7f0;
}

.queue-item.active {
  background: #fbf7f0;
  box-shadow: inset 3px 0 0 var(--wg-clay);
}

.q-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.q-author {
  font-size: 11.5px;
  color: var(--wg-text-soft);
  font-weight: 500;
}

.q-time {
  font-size: 10.5px;
  color: var(--wg-muted-light);
}

.q-title {
  margin: 5px 0 0;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--wg-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.q-content {
  margin: 3px 0 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--wg-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.q-bottom {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 7px;
}

.q-cat {
  font-size: 10.5px;
  color: var(--wg-muted);
}

.q-stat {
  margin-left: auto;
  font-size: 10.5px;
  color: var(--wg-muted-light);
}

.queue-empty {
  padding: 60px 16px;
  text-align: center;
  font-size: 12px;
  color: var(--wg-muted);
}

.queue-pager {
  display: flex;
  justify-content: center;
  padding: 8px 10px;
  border-top: 1px solid #f0e9de;
}

.detail-panel {
  min-height: 420px;
  padding: 18px 20px 20px;
}

.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.detail-title-row {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-wrap: wrap;
}

.detail-title-row h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  line-height: 1.4;
}

.detail-meta {
  margin: 5px 0 0;
  font-size: 11.5px;
  color: var(--wg-muted);
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  flex-shrink: 0;
}

.review-reason {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 9px 11px;
  border: 1px solid #ecd3ab;
  border-radius: 8px;
  background: #fbf0dc;
  font-size: 11.5px;
  color: #8a5f1e;
}

.content-block {
  margin-top: 14px;
  padding: 14px 16px;
  border: 1px solid #f0e9de;
  border-radius: 10px;
  background: #fdfaf4;
}

.content-block p {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--wg-text-soft);
  white-space: pre-wrap;
}

.image-hint {
  margin-top: 10px;
  font-size: 10.5px;
  color: var(--wg-muted);
}

.stat-strip {
  display: flex;
  gap: 18px;
  margin-top: 10px;
  font-size: 11px;
  color: var(--wg-muted);
}

.sub-section {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #f0e9de;
}

.sub-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.sub-head h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.comment-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 9px 11px;
  border: 1px solid #f2ece2;
  border-radius: 8px;
  background: #fdfbf6;
}

.comment-main {
  flex: 1;
  min-width: 0;
}

.comment-top {
  display: flex;
  align-items: center;
  gap: 7px;
}

.comment-author {
  font-size: 11.5px;
  font-weight: 500;
  color: var(--wg-text-soft);
}

.comment-time {
  font-size: 10.5px;
  color: var(--wg-muted-light);
}

.comment-main p {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--wg-text-soft);
}

.comment-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
  font-size: 11.5px;
}

.wg-link.danger {
  color: var(--wg-danger);
}

.report-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.report-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 11px;
  border: 1px solid #f2ece2;
  border-radius: 8px;
  background: #fdfbf6;
}

.report-main {
  flex: 1;
  min-width: 0;
}

.report-top {
  display: flex;
  align-items: center;
  gap: 7px;
}

.report-main p {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.55;
  color: var(--wg-text-soft);
}

.muted {
  color: var(--wg-muted);
  font-size: 12px;
}
</style>
