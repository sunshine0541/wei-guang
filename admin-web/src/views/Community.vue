<template>
  <div>
    <el-card>
      <el-form inline>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 150px">
            <el-option v-for="(label, value) in STATUS_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.category" clearable placeholder="全部" style="width: 140px">
            <el-option v-for="(label, value) in CATEGORY_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="正文关键词" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load(1)">查询</el-button>
          <el-button @click="openOfficial">发布官方动态</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table :data="list" v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="authorNickname" label="作者" width="120" />
        <el-table-column label="分类" width="110">
          <template #default="{ row }">{{ CATEGORY_LABELS[row.category] || row.category }}</template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
        <el-table-column prop="content" label="正文" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ STATUS_LABELS[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="互动" width="110">
          <template #default="{ row }">{{ row.likeCount || 0 }} 赞 / {{ row.commentCount || 0 }} 评</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="发布时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'PENDING_MANUAL'" link type="success" @click="openReview(row, 'APPROVE')">通过</el-button>
            <el-button v-if="row.status === 'PENDING_MANUAL'" link type="danger" @click="openReview(row, 'REJECT')">拒绝</el-button>
            <el-button v-if="row.status === 'PUBLISHED'" link type="warning" @click="hide(row)">隐藏</el-button>
            <el-button v-if="row.status === 'HIDDEN'" link type="success" @click="restore(row)">恢复</el-button>
            <el-button v-if="row.status !== 'DELETED'" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="query.pageSize"
        :current-page="query.page"
        @current-change="load"
      />
    </el-card>

    <el-dialog v-model="reviewVisible" :title="reviewForm.result === 'APPROVE' ? '通过动态' : '拒绝动态'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="备注">
          <el-input v-model="reviewForm.note" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="可选：审核说明" />
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

    <el-dialog v-model="detailVisible" title="动态详情" width="720px">
      <template v-if="detail.post">
        <div class="detail-head">
          <b>#{{ detail.post.id }} {{ detail.post.title || '（无标题）' }}</b>
          <el-tag size="small">{{ STATUS_LABELS[detail.post.status] || detail.post.status }}</el-tag>
        </div>
        <div class="detail-meta">
          {{ detail.post.authorNickname }} · {{ CATEGORY_LABELS[detail.post.category] || detail.post.category }} · {{ detail.post.createdAt }}
        </div>
        <div v-if="detail.post.reviewReason" class="detail-reason">审核说明：{{ detail.post.reviewReason }}</div>
        <div class="detail-content">{{ detail.post.content }}</div>

        <el-divider>评论（{{ (detail.comments || []).length }}）</el-divider>
        <el-table :data="detail.comments" size="small">
          <el-table-column prop="authorNickname" label="作者" width="110" />
          <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="时间" width="160" />
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'PENDING_MANUAL' || row.status === 'PENDING_REVIEW'"
                link
                type="success"
                @click="approveComment(row)"
              >通过</el-button>
              <el-button link type="danger" @click="removeComment(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <template v-if="(detail.reports || []).length">
          <el-divider>关联举报</el-divider>
          <el-table :data="detail.reports" size="small">
            <el-table-column prop="targetType" label="类型" width="90" />
            <el-table-column prop="evidence" label="举报理由" min-width="240" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">{{ REPORT_STATUS_LABELS[row.status] || row.status }}</template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" width="160" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button v-if="row.status === 'PENDING'" link type="primary" @click="goHandleReport(row)">去处置</el-button>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
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

const reviewVisible = ref(false)
const reviewForm = reactive({ id: null, result: 'APPROVE', note: '' })
const officialVisible = ref(false)
const officialForm = reactive({ title: '', content: '' })
const detailVisible = ref(false)
const detail = reactive({ post: null, comments: [], reports: [] })
const actionLoading = ref(false)

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

function statusTag(status) {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'PENDING_MANUAL' || status === 'PENDING_REVIEW') return 'warning'
  if (status === 'REJECTED' || status === 'DELETED') return 'danger'
  return 'info'
}

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
  } catch (e) {
    // 请求层已提示；保留旧列表，避免整页空白
  } finally {
    loading.value = false
  }
}

function openReview(row, result) {
  reviewForm.id = row.id
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
  load(query.page)
}

async function hide(row) {
  if (await runAction(() => hideCommunityPost(row.id), '已隐藏')) load(query.page)
}

async function restore(row) {
  if (await runAction(() => restoreCommunityPost(row.id), '已恢复')) load(query.page)
}

async function remove(row) {
  const confirmed = await ElMessageBox.confirm('删除后用户端不再展示，确定吗？', '删除动态', { type: 'warning' })
    .then(() => true).catch(() => false)
  if (!confirmed) return
  if (await runAction(() => deleteCommunityPost(row.id), '已删除')) load(query.page)
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

async function openDetail(row) {
  try {
    const data = await getCommunityPost(row.id)
    detail.post = data.post
    detail.comments = data.comments || []
    detail.reports = data.reports || []
    detailVisible.value = true
  } catch (e) {
    // 请求层已提示
  }
}

async function approveComment(row) {
  if (await runAction(() => reviewCommunityComment(row.id, { result: 'APPROVE', note: null }), '评论已通过')) {
    openDetail(detail.post)
  }
}

async function removeComment(row) {
  const confirmed = await ElMessageBox.confirm('删除这条评论吗？', '删除评论', { type: 'warning' })
    .then(() => true).catch(() => false)
  if (!confirmed) return
  if (await runAction(() => deleteCommunityComment(row.id), '评论已删除')) {
    openDetail(detail.post)
  }
}

function goHandleReport(row) {
  router.push({ path: '/reports', query: { reportId: row.id } })
}

onMounted(async () => {
  await load(1)
  const postId = Number(route.query.postId)
  if (postId) {
    openDetail({ id: postId })
  }
})
</script>

<style scoped>
.table-card {
  margin-top: 12px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.detail-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.detail-meta {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
}
.detail-reason {
  margin-top: 8px;
  color: #b06a00;
  font-size: 13px;
}
.detail-content {
  margin-top: 12px;
  white-space: pre-wrap;
  line-height: 1.7;
}
</style>
