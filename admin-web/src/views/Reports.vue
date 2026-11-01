<template>
  <div class="wg-page">
    <div class="wg-toolbar">
      <p>共 <b>{{ total }}</b> 条上报记录<span v-if="query.status"> · 当前筛选「{{ REPORT_STATUS_LABELS[query.status] }}」</span></p>
      <button class="wg-ghost-btn" :disabled="loading" @click="load(query.page)">
        <AdminIcon name="refresh" :size="14" />刷新
      </button>
    </div>

    <div class="filter-card">
      <div class="wg-chips">
        <button class="wg-chip" :class="{ active: !query.status }" @click="pickStatus('')">全部</button>
        <button
          v-for="(label, value) in REPORT_STATUS_LABELS"
          :key="value"
          class="wg-chip"
          :class="{ active: query.status === value }"
          @click="pickStatus(value)"
        >
          {{ label }}
        </button>
      </div>
    </div>

    <div class="table-card">
      <el-table :data="list" v-loading="loading">
        <el-table-column prop="id" label="ID" width="64">
          <template #default="{ row }">
            <span class="record-id">#{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="目标" width="104">
          <template #default="{ row }">
            <span class="wg-pill is-gray no-dot">{{ targetLabel(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="被举报内容" min-width="260">
          <template #default="{ row }">
            <div class="content-cell">
              <span class="content-title">{{ row.targetTitle || targetLabel(row) }}</span>
              <span class="content-text">{{ row.targetContent || '—' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="targetUserId" label="被上报人" width="96">
          <template #default="{ row }">
            <span>{{ row.targetUserId ? 'uid ' + row.targetUserId : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="举报理由" min-width="190">
          <template #default="{ row }">
            <span class="reason-text">{{ row.evidence || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="104">
          <template #default="{ row }">
            <span class="wg-pill" :class="'is-' + reportStatusTone(row.status)">
              {{ REPORT_STATUS_LABELS[row.status] || row.status }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="联动" width="100">
          <template #default="{ row }">
            <span class="muted">{{ actionLabel(row.action) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="上报时间" width="118">
          <template #default="{ row }">{{ formatShort(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="132" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <span v-if="row.status === 'PENDING'" class="wg-link" @click="openHandle(row)">处置</span>
              <span v-if="row.targetPostId" class="wg-link" @click="viewTarget(row)">看原内容</span>
              <span v-else-if="row.orderId" class="wg-link" @click="viewOrder(row)">看订单</span>
              <span v-if="row.status !== 'PENDING' && !row.targetPostId && !row.orderId" class="muted">—</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="query.pageSize"
        :page-sizes="[10, 20, 50]"
        :current-page="query.page"
        @current-change="load"
        @size-change="onSizeChange"
      />
    </div>

    <el-dialog v-model="dialogVisible" title="上报处置" width="560px">
      <div class="dialog-target">
        <span class="wg-pill is-gray no-dot">{{ targetLabel(currentRow || {}) }}</span>
        <p>{{ (currentRow && (currentRow.targetTitle || currentRow.evidence)) || '—' }}</p>
      </div>
      <el-form :model="form" label-width="92px">
        <el-form-item label="处置结果">
          <el-radio-group v-model="form.result">
            <el-radio value="CONFIRMED">确认违规</el-radio>
            <el-radio value="REJECTED">判定误报</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" required>
          <el-input
            v-model="form.note"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="处置说明（必填）"
          />
        </el-form-item>
        <el-form-item v-if="form.result === 'CONFIRMED'" label="联动动作">
          <el-select v-model="form.action" style="width: 220px">
            <el-option label="无联动" value="NONE" />
            <el-option label="微光值扣减" value="CREDIT_MINUS" />
            <el-option label="封禁账号" value="BAN" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.result === 'CONFIRMED' && form.action === 'CREDIT_MINUS'" label="扣减幅度">
          <el-radio-group v-model="form.creditDelta">
            <el-radio :value="-10">-10</el-radio>
            <el-radio :value="-20">-20</el-radio>
          </el-radio-group>
        </el-form-item>
        <div v-if="form.result === 'CONFIRMED'" class="dialog-note">
          确认违规后保持两步处置：本页只记录结果与联动；内容隐藏/删除请到「社区审核」执行。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交处置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AdminIcon from '../components/AdminIcon.vue'
import { handleReport, listReports } from '../api/admin'
import { REPORT_STATUS_LABELS } from '../utils/labels'
import { formatShort } from '../utils/format'
import { reportStatusTone } from '../utils/status'

const route = useRoute()
const router = useRouter()

const query = reactive({ status: '', page: 1, pageSize: 20 })
const list = ref([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)
const currentId = ref(null)
const currentRow = ref(null)
const form = reactive({ result: 'CONFIRMED', note: '', action: 'NONE', creditDelta: -20 })

function actionLabel(action) {
  return { NONE: '无', CREDIT_MINUS: '微光值扣减', BAN: '封禁' }[action] || action || '—'
}

function targetLabel(row) {
  if (!row || !row.targetType) return '—'
  if (row.targetType === 'POST') return `动态 #${row.targetId}`
  if (row.targetType === 'COMMENT') return `评论 #${row.targetId}`
  if (row.targetType === 'ORDER') return `订单 #${row.orderId || row.targetId}`
  return row.targetType
}

function viewTarget(row) {
  router.push({ path: '/community', query: { postId: row.targetPostId } })
}

function viewOrder(row) {
  if (row.orderId) router.push(`/orders/${row.orderId}`)
}

function pickStatus(status) {
  query.status = status
  load(1)
}

async function load(page) {
  query.page = page || query.page
  loading.value = true
  try {
    const data = await listReports({ status: query.status || undefined, page: query.page, pageSize: query.pageSize })
    list.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    // 请求层已提示，保留旧列表
  } finally {
    loading.value = false
  }
}

function onSizeChange(size) {
  query.pageSize = size
  load(1)
}

function openHandle(row) {
  currentId.value = row.id
  currentRow.value = row
  form.result = 'CONFIRMED'
  form.note = ''
  form.action = 'NONE'
  form.creditDelta = -20
  dialogVisible.value = true
}

async function submit() {
  if (!form.note.trim()) {
    ElMessage.warning('请填写处置备注')
    return
  }
  const payload = { result: form.result, note: form.note.trim() }
  if (form.result === 'CONFIRMED' && form.action !== 'NONE') {
    payload.action = form.action
    if (form.action === 'CREDIT_MINUS') {
      payload.creditDelta = form.creditDelta
    }
  }
  submitting.value = true
  try {
    await handleReport(currentId.value, payload)
    ElMessage.success('已处置')
    dialogVisible.value = false
    load(query.page)
  } catch (e) {
    // 请求层已提示
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await load(1)
  const reportId = Number(route.query.reportId)
  if (reportId) {
    const row = list.value.find((item) => item.id === reportId)
    if (row && row.status === 'PENDING') {
      openHandle(row)
    } else if (row) {
      ElMessage.warning('该上报已处置，可在「全部」中查看结果')
    } else {
      ElMessage.warning('该上报不在当前列表，请按状态筛选后再处置')
    }
  }
})
</script>

<style scoped>
.filter-card {
  padding: 12px 16px;
  margin-bottom: 12px;
  border: 1px solid var(--wg-line);
  border-radius: 12px;
  background: var(--wg-panel);
}

.table-card {
  padding: 0 0 12px;
  border: 1px solid var(--wg-line);
  border-radius: 12px;
  background: var(--wg-panel);
  overflow: hidden;
}

.record-id {
  color: var(--wg-muted);
  font-variant-numeric: tabular-nums;
}

.content-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.content-title {
  font-size: 12.5px;
  color: var(--wg-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.content-text {
  font-size: 11px;
  color: var(--wg-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reason-text {
  font-size: 12px;
  color: var(--wg-text-soft);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.muted {
  color: var(--wg-muted);
  font-size: 12px;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
}

.pager {
  margin: 12px 16px 0;
  justify-content: flex-end;
}

.dialog-target {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  padding: 10px 12px;
  border: 1px solid #f0e9de;
  border-radius: 9px;
  background: #fdfaf4;
}

.dialog-target p {
  margin: 0;
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: var(--wg-text-soft);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dialog-note {
  margin: 0 0 4px 92px;
  font-size: 10.5px;
  line-height: 1.6;
  color: var(--wg-muted);
}
</style>
