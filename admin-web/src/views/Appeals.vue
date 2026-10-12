<template>
  <div>
    <el-card>
      <el-form inline>
        <el-form-item label="类型">
          <el-select v-model="query.type" clearable placeholder="全部" style="width: 150px">
            <el-option v-for="(label, value) in APPEAL_TYPE_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px">
            <el-option v-for="(label, value) in APPEAL_STATUS_LABELS" :key="value" :label="label" :value="value" />
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
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ APPEAL_TYPE_LABELS[row.type] || row.type }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PENDING' ? 'warning' : 'success'">
              {{ APPEAL_STATUS_LABELS[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="initiatorNickname" label="发起人" width="110" />
        <el-table-column prop="orderTitle" label="关联求助" min-width="160">
          <template #default="{ row }">{{ row.orderTitle || '（反馈无关联）' }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="理由" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="提交时间" width="170" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">{{ row.status === 'PENDING' ? '处理' : '查看' }}</el-button>
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

    <el-dialog v-model="dialogVisible" :title="detail?.type === 'FEEDBACK' ? '反馈处理' : '申诉仲裁'" width="640px">
      <div v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="发起人">{{ detail.initiatorNickname || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ APPEAL_STATUS_LABELS[detail.status] }}</el-descriptions-item>
          <el-descriptions-item label="理由" :span="2">{{ detail.reason }}</el-descriptions-item>
          <template v-if="detail.result">
            <el-descriptions-item label="结果">{{ detail.result === 'TO_COMPLETED' ? '落回已完成' : '落回已取消' }}</el-descriptions-item>
            <el-descriptions-item label="结论">{{ detail.conclusion }}</el-descriptions-item>
          </template>
        </el-descriptions>

        <template v-if="detail.orderId">
          <div class="sub-title">关联求助</div>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="标题">{{ detail.orderTitle }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ ORDER_STATUS_LABELS[detail.orderStatus] || detail.orderStatus }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ ORDER_TYPE_LABELS[detail.orderType] || detail.orderType }}</el-descriptions-item>
            <el-descriptions-item label="发布者 uid">{{ detail.requesterId }}</el-descriptions-item>
            <el-descriptions-item label="帮助者 uid">{{ detail.helperId ?? '—' }}</el-descriptions-item>
          </el-descriptions>

          <div class="sub-title">状态时间线</div>
          <el-timeline>
            <el-timeline-item v-for="(item, index) in detail.timeline" :key="index" :timestamp="item.createdAt">
              {{ TIMELINE_LABELS[item.eventType] || item.eventType }}（{{ OPERATOR_ROLE_LABELS[item.operatorRole] || item.operatorRole }}）
              <div v-if="item.remark" class="muted">{{ item.remark }}</div>
            </el-timeline-item>
          </el-timeline>

          <template v-if="detail.chatMessages && detail.chatMessages.length">
            <div class="sub-title">聊天记录</div>
            <div class="chat-box">
              <div v-for="msg in detail.chatMessages" :key="msg.msgId" class="chat-line">
                <span class="muted">[{{ msg.senderId === 0 ? '系统' : 'uid' + msg.senderId }}] {{ msg.createdAt }}</span>
                <div>{{ msg.content }}</div>
              </div>
            </div>
          </template>
        </template>

        <template v-if="detail.type === 'FEEDBACK' && detail.status === 'PENDING'">
          <div class="sub-title">处理反馈</div>
          <el-input v-model="feedbackConclusion" type="textarea" :rows="3" maxlength="200" placeholder="处理结论（将通知用户）" />
        </template>

        <template v-if="detail.type !== 'FEEDBACK' && detail.status === 'PENDING'">
          <div class="sub-title">仲裁</div>
          <el-form :model="arbitrateForm" label-width="90px">
            <el-form-item label="裁决结果">
              <el-radio-group v-model="arbitrateForm.result">
                <el-radio value="TO_COMPLETED">落回已完成（补发结算）</el-radio>
                <el-radio value="TO_CANCELLED">落回已取消（追回结算）</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="结论" required>
              <el-input v-model="arbitrateForm.conclusion" maxlength="200" placeholder="裁决说明（必填）" />
            </el-form-item>
            <el-form-item label="败诉方处置">
              <el-checkbox v-model="penaltyRequester">发布者（uid {{ detail.requesterId }}）扣微光值</el-checkbox>
              <el-checkbox v-if="detail.helperId" v-model="penaltyHelper">帮助者（uid {{ detail.helperId }}）扣微光值</el-checkbox>
            </el-form-item>
          </el-form>
        </template>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button v-if="detail && detail.status === 'PENDING' && detail.type === 'FEEDBACK'" type="primary" :loading="submitting" @click="submitFeedback">
          提交结论
        </el-button>
        <el-button
          v-if="detail && detail.status === 'PENDING' && detail.type !== 'FEEDBACK'"
          type="primary"
          :loading="submitting"
          @click="submitArbitrate"
        >
          提交仲裁
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { arbitrateAppeal, getAppealDetail, listAppeals, replyFeedback } from '../api/admin'
import {
  APPEAL_STATUS_LABELS,
  APPEAL_TYPE_LABELS,
  OPERATOR_ROLE_LABELS,
  ORDER_STATUS_LABELS,
  ORDER_TYPE_LABELS,
  TIMELINE_LABELS
} from '../utils/labels'

const query = reactive({ type: '', status: '', page: 1, pageSize: 20 })
const list = ref([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const detail = ref(null)
const submitting = ref(false)
const feedbackConclusion = ref('')
const arbitrateForm = reactive({ result: 'TO_COMPLETED', conclusion: '' })
const penaltyRequester = ref(false)
const penaltyHelper = ref(false)

async function load(page) {
  query.page = page || query.page
  loading.value = true
  try {
    const data = await listAppeals({
      type: query.type || undefined,
      status: query.status || undefined,
      page: query.page,
      pageSize: query.pageSize
    })
    list.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  detail.value = await getAppealDetail(row.id)
  feedbackConclusion.value = ''
  arbitrateForm.result = 'TO_COMPLETED'
  arbitrateForm.conclusion = ''
  penaltyRequester.value = false
  penaltyHelper.value = false
  dialogVisible.value = true
}

async function submitFeedback() {
  if (!feedbackConclusion.value.trim()) {
    ElMessage.warning('请填写处理结论')
    return
  }
  submitting.value = true
  try {
    await replyFeedback(detail.value.id, feedbackConclusion.value.trim())
    ElMessage.success('已处理，用户会收到通知')
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function submitArbitrate() {
  if (!arbitrateForm.conclusion.trim()) {
    ElMessage.warning('请填写裁决结论')
    return
  }
  const penalties = []
  if (penaltyRequester.value) {
    penalties.push({ userId: detail.value.requesterId, reasonCode: 'APPEAL_LOSS' })
  }
  if (penaltyHelper.value && detail.value.helperId) {
    penalties.push({ userId: detail.value.helperId, reasonCode: 'APPEAL_LOSS' })
  }
  const payload = { result: arbitrateForm.result, conclusion: arbitrateForm.conclusion.trim() }
  if (penalties.length) {
    payload.penalties = penalties
  }
  submitting.value = true
  try {
    await arbitrateAppeal(detail.value.id, payload)
    ElMessage.success('已仲裁')
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

onMounted(() => load(1))
</script>

<style scoped>
.table-card {
  margin-top: 16px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.sub-title {
  margin: 14px 0 8px;
  font-weight: 600;
}

.muted {
  color: #999;
  font-size: 12px;
}

.chat-box {
  max-height: 220px;
  overflow: auto;
  border: 1px solid #ebeef5;
  padding: 8px;
}

.chat-line {
  padding: 4px 0;
  border-bottom: 1px dashed #ebeef5;
}
</style>