<template>
  <div>
    <el-card>
      <el-form inline>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px">
            <el-option v-for="(label, value) in REPORT_STATUS_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load(1)">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table :data="list" v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="目标" width="130">
          <template #default="{ row }">
            {{ targetLabel(row) }}
          </template>
        </el-table-column>
        <el-table-column label="被举报内容" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.targetContent">{{ row.targetTitle ? row.targetTitle + ' · ' : '' }}{{ row.targetContent }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="targetUserId" label="被上报人" width="90" />
        <el-table-column prop="evidence" label="举报理由" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PENDING' ? 'warning' : row.status === 'CONFIRMED' ? 'danger' : 'info'">
              {{ REPORT_STATUS_LABELS[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="联动" width="110">
          <template #default="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column prop="handleNote" label="处置备注" min-width="140" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="上报时间" width="170" />
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" link type="primary" @click="openHandle(row)">处置</el-button>
            <el-button v-if="row.targetPostId" link type="primary" @click="viewTarget(row)">看原内容</el-button>
            <el-button v-else-if="row.orderId" link type="primary" @click="viewOrder(row)">看订单</el-button>
            <span v-if="row.status !== 'PENDING' && !row.targetPostId && !row.orderId" class="muted">—</span>
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

    <el-dialog v-model="dialogVisible" title="上报处置" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="处置结果">
          <el-radio-group v-model="form.result">
            <el-radio value="CONFIRMED">确认违规</el-radio>
            <el-radio value="REJECTED">判定误报</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" required>
          <el-input v-model="form.note" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="处置说明（必填）" />
        </el-form-item>
        <el-form-item v-if="form.result === 'CONFIRMED'" label="联动动作">
          <el-select v-model="form.action" style="width: 200px">
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
import { handleReport, listReports } from '../api/admin'
import { REPORT_STATUS_LABELS } from '../utils/labels'

const route = useRoute()
const router = useRouter()

const query = reactive({ status: '', page: 1, pageSize: 20 })
const list = ref([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)
const currentId = ref(null)
const form = reactive({ result: 'CONFIRMED', note: '', action: 'NONE', creditDelta: -20 })

function actionLabel(action) {
  return { NONE: '无', CREDIT_MINUS: '微光值扣减', BAN: '封禁' }[action] || action || '—'
}

function targetLabel(row) {
  if (row.targetType === 'POST') return `动态 #${row.targetId}`
  if (row.targetType === 'COMMENT') return `评论 #${row.targetId}`
  if (row.targetType === 'ORDER') return `订单 #${row.orderId || '—'}`
  return row.targetType || '—'
}

function viewTarget(row) {
  router.push({ path: '/community', query: { postId: row.targetPostId } })
}

function viewOrder(row) {
  if (row.orderId) router.push(`/orders/${row.orderId}`)
}

async function load(page) {
  query.page = page || query.page
  loading.value = true
  try {
    const data = await listReports({ status: query.status || undefined, page: query.page, pageSize: query.pageSize })
    list.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    // 请求层已提示
  } finally {
    loading.value = false
  }
}

function openHandle(row) {
  currentId.value = row.id
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
    load()
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
    } else {
      ElMessage.warning('该上报不在当前列表，请按状态筛选后再处置')
    }
  }
})
</script>

<style scoped>
.table-card {
  margin-top: 16px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.muted {
  color: #999;
}
</style>