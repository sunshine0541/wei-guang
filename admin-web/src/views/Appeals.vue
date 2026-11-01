<template>
  <div class="wg-page">
    <div class="wg-toolbar">
      <p>共 <b>{{ total }}</b> 条申诉与反馈<span v-if="query.status"> · 当前筛选「{{ APPEAL_STATUS_LABELS[query.status] }}」</span></p>
      <button class="wg-ghost-btn" :disabled="loading" @click="load(query.page)">
        <AdminIcon name="refresh" :size="14" />刷新
      </button>
    </div>

    <div class="filter-card">
      <div class="wg-chips">
        <button class="wg-chip" :class="{ active: !query.status }" @click="pickStatus('')">全部</button>
        <button
          v-for="(label, value) in APPEAL_STATUS_LABELS"
          :key="value"
          class="wg-chip"
          :class="{ active: query.status === value }"
          @click="pickStatus(value)"
        >
          {{ label }}
        </button>
      </div>
      <el-select v-model="query.type" class="type-select" placeholder="全部类型" clearable @change="load(1)">
        <el-option v-for="(label, value) in APPEAL_TYPE_LABELS" :key="value" :label="label" :value="value" />
      </el-select>
    </div>

    <div class="table-card">
      <el-table :data="list" v-loading="loading">
        <el-table-column prop="id" label="ID" width="64">
          <template #default="{ row }">
            <span class="record-id">#{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="108">
          <template #default="{ row }">
            <span class="wg-pill no-dot" :class="row.type === 'FEEDBACK' ? 'is-blue' : 'is-amber'">
              {{ APPEAL_TYPE_LABELS[row.type] || row.type }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="wg-pill" :class="'is-' + appealStatusTone(row.status)">
              {{ APPEAL_STATUS_LABELS[row.status] || row.status }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="initiatorNickname" label="发起人" width="110">
          <template #default="{ row }">{{ row.initiatorNickname || '—' }}</template>
        </el-table-column>
        <el-table-column label="关联求助" min-width="170">
          <template #default="{ row }">
            <span v-if="row.orderTitle" class="order-title">{{ row.orderTitle }}</span>
            <span v-else class="muted">{{ row.type === 'FEEDBACK' ? '反馈无关联订单' : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="理由" min-width="220">
          <template #default="{ row }">
            <span class="reason-text">{{ row.reason || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="118">
          <template #default="{ row }">{{ formatShort(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <span class="wg-link" @click="openDetail(row)">{{ row.status === 'PENDING' ? '处理' : '查看' }}</span>
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

    <el-drawer v-model="drawerVisible" size="760px" :with-header="false">
      <div v-loading="detailLoading" class="drawer-body">
        <template v-if="detail">
          <div class="drawer-top">
            <div>
              <p class="drawer-id">#{{ detail.id }}</p>
              <h3>{{ detail.type === 'FEEDBACK' ? '用户反馈' : '订单申诉' }}</h3>
            </div>
            <span class="wg-pill" :class="'is-' + appealStatusTone(detail.status)">
              {{ APPEAL_STATUS_LABELS[detail.status] || detail.status }}
            </span>
          </div>

          <div class="meta-row">
            <span>{{ APPEAL_TYPE_LABELS[detail.type] || detail.type }}</span>
            <span>·</span>
            <span>发起人 {{ detail.initiatorNickname || '—' }}</span>
            <span>·</span>
            <span>{{ formatShort(detail.createdAt) }} 提交</span>
          </div>

          <section class="block">
            <h4>{{ detail.type === 'FEEDBACK' ? '反馈内容' : '申诉理由' }}</h4>
            <p class="paragraph">{{ detail.reason || '—' }}</p>
          </section>

          <section v-if="detail.result || detail.conclusion" class="block">
            <h4>处理结果</h4>
            <div class="result-card">
              <span class="wg-pill is-green no-dot">
                {{ detail.result === 'TO_COMPLETED' ? '落回已完成' : detail.result === 'TO_CANCELLED' ? '落回已取消' : detail.result === 'AUTO_RESTORED' ? '超时自动恢复' : '已处理' }}
              </span>
              <p>{{ detail.conclusion || '—' }}</p>
            </div>
          </section>

          <template v-if="detail.orderId">
            <section class="block">
              <h4>关联求助</h4>
              <div class="order-card">
                <div class="order-head">
                  <span class="order-title">{{ detail.orderTitle }}</span>
                  <span class="wg-pill" :class="'is-' + orderStatusTone(detail.orderStatus)">
                    {{ ORDER_STATUS_LABELS[detail.orderStatus] || detail.orderStatus }}
                  </span>
                </div>
                <div class="order-meta">
                  <span>{{ ORDER_TYPE_LABELS[detail.orderType] || detail.orderType }}</span>
                  <span>发布者 uid {{ detail.requesterId }}</span>
                  <span>帮助者 uid {{ detail.helperId ?? '—' }}</span>
                  <span class="wg-link" @click="router.push('/orders/' + detail.orderId)">查看订单 →</span>
                </div>
              </div>
            </section>

            <section class="block">
              <h4>状态时间线</h4>
              <div v-if="(detail.timeline || []).length" class="timeline">
                <div v-for="(item, index) in detail.timeline" :key="index" class="tl-item">
                  <i :class="'tl-dot tone-' + timelineTone(item.eventType)"></i>
                  <div class="tl-body">
                    <p>{{ TIMELINE_LABELS[item.eventType] || item.eventType }}
                      <span class="role">{{ OPERATOR_ROLE_LABELS[item.operatorRole] || item.operatorRole }}</span>
                    </p>
                    <p v-if="item.remark" class="tl-remark">{{ item.remark }}</p>
                    <span class="tl-time">{{ formatShort(item.createdAt) }}</span>
                  </div>
                </div>
              </div>
              <p v-else class="muted">暂无时间线</p>
            </section>

            <section v-if="(detail.chatMessages || []).length" class="block">
              <h4>聊天记录</h4>
              <div class="chat-box">
                <div v-for="msg in detail.chatMessages" :key="msg.msgId" class="chat-line">
                  <div class="chat-meta">
                    <span class="chat-who">{{ msg.senderId === 0 ? '系统' : 'uid' + msg.senderId }}</span>
                    <span>{{ formatShort(msg.createdAt) }}</span>
                  </div>
                  <p>{{ msg.content }}</p>
                </div>
              </div>
            </section>
          </template>

          <section v-if="detail.type === 'FEEDBACK' && detail.status === 'PENDING'" class="block">
            <h4>处理反馈</h4>
            <el-input
              v-model="feedbackConclusion"
              type="textarea"
              :rows="3"
              maxlength="200"
              show-word-limit
              placeholder="处理结论（提交后将通知用户）"
            />
          </section>

          <section v-if="detail.type !== 'FEEDBACK' && detail.status === 'PENDING'" class="block">
            <h4>仲裁</h4>
            <el-form :model="arbitrateForm" label-width="94px">
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
          </section>

          <div class="drawer-actions">
            <el-button @click="drawerVisible = false">关闭</el-button>
            <el-button
              v-if="detail.status === 'PENDING' && detail.type === 'FEEDBACK'"
              type="primary"
              :loading="submitting"
              @click="submitFeedback"
            >
              提交结论
            </el-button>
            <el-button
              v-if="detail.status === 'PENDING' && detail.type !== 'FEEDBACK'"
              type="primary"
              :loading="submitting"
              @click="submitArbitrate"
            >
              提交仲裁
            </el-button>
          </div>
        </template>
        <p v-else-if="!detailLoading" class="muted">加载失败，请稍后重试</p>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminIcon from '../components/AdminIcon.vue'
import { arbitrateAppeal, getAppealDetail, listAppeals, replyFeedback } from '../api/admin'
import {
  APPEAL_STATUS_LABELS,
  APPEAL_TYPE_LABELS,
  OPERATOR_ROLE_LABELS,
  ORDER_STATUS_LABELS,
  ORDER_TYPE_LABELS,
  TIMELINE_LABELS
} from '../utils/labels'
import { formatShort } from '../utils/format'
import { appealStatusTone, orderStatusTone } from '../utils/status'

const route = useRoute()
const router = useRouter()
const query = reactive({ type: '', status: '', page: 1, pageSize: 20 })
const list = ref([])
const total = ref(0)
const loading = ref(false)
const drawerVisible = ref(false)
const detailLoading = ref(false)
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

function onSizeChange(size) {
  query.pageSize = size
  load(1)
}

async function openDetail(row) {
  await openDetailById(row.id)
}

async function openDetailById(id) {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const data = await getAppealDetail(id)
    detail.value = data
    feedbackConclusion.value = ''
    arbitrateForm.result = 'TO_COMPLETED'
    arbitrateForm.conclusion = ''
    penaltyRequester.value = false
    penaltyHelper.value = false
  } catch (e) {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

function timelineTone(type) {
  if (['ARBITRATE', 'CANCEL', 'EXPIRE', 'CANCEL_CONFIRM_REJECT'].includes(type)) return 'red'
  if (['COMPLETE', 'CONFIRM'].includes(type)) return 'green'
  if (['APPEAL_OPEN', 'RELEASE', 'CANCEL_REQUEST'].includes(type)) return 'amber'
  return 'blue'
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
    drawerVisible.value = false
    load(query.page)
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
  const confirmed = await ElMessageBox.confirm('确定提交裁决吗？结果将直接落回订单并通知双方。', '裁决确认', {
    type: 'warning'
  })
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return
  submitting.value = true
  try {
    await arbitrateAppeal(detail.value.id, payload)
    ElMessage.success('已仲裁')
    drawerVisible.value = false
    load(query.page)
  } catch (e) {
    // 请求层已提示
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await load(1)
  const appealId = Number(route.query.appealId)
  if (appealId) {
    await openDetailById(appealId)
  }
})
</script>

<style scoped>
.filter-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 12px;
  border: 1px solid var(--wg-line);
  border-radius: 12px;
  background: var(--wg-panel);
}

.type-select {
  width: 150px;
  flex-shrink: 0;
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

.order-title {
  font-size: 12.5px;
  color: var(--wg-text);
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

.pager {
  margin: 12px 16px 0;
  justify-content: flex-end;
}

.drawer-body {
  min-height: 300px;
}

.drawer-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.drawer-id {
  margin: 0;
  font-size: 11.5px;
  color: var(--wg-muted);
  font-variant-numeric: tabular-nums;
}

.drawer-top h3 {
  margin: 4px 0 0;
  font-size: 18px;
  font-weight: 600;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
  font-size: 11.5px;
  color: var(--wg-muted);
}

.block {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid #f0e9de;
}

.block h4 {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
}

.paragraph {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.75;
  color: var(--wg-text-soft);
  white-space: pre-wrap;
}

.result-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #d6e3d3;
  border-radius: 9px;
  background: #eff5ee;
}

.result-card p {
  margin: 0;
  flex: 1;
  font-size: 12px;
  line-height: 1.6;
  color: #4f6b4c;
}

.order-card {
  padding: 11px 12px;
  border: 1px solid #f0e9de;
  border-radius: 9px;
  background: #fdfaf4;
}

.order-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.order-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 7px;
  font-size: 10.5px;
  color: var(--wg-muted);
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.tl-item {
  display: flex;
  gap: 9px;
}

.tl-dot {
  width: 6px;
  height: 6px;
  margin-top: 6px;
  flex-shrink: 0;
  border-radius: 50%;
}

.tl-dot.tone-red {
  background: var(--wg-danger);
}

.tl-dot.tone-green {
  background: var(--wg-success);
}

.tl-dot.tone-amber {
  background: var(--wg-amber);
}

.tl-dot.tone-blue {
  background: var(--wg-info);
}

.tl-body p {
  margin: 0;
  font-size: 12px;
  color: var(--wg-text-soft);
}

.role {
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 4px;
  background: #f4efe6;
  color: var(--wg-muted);
  font-size: 10.5px;
}

.tl-remark {
  margin-top: 3px !important;
  font-size: 11.5px !important;
}

.tl-time {
  font-size: 10.5px;
  color: var(--wg-muted-light);
}

.chat-box {
  max-height: 240px;
  overflow-y: auto;
  padding: 8px;
  border: 1px solid #f0e9de;
  border-radius: 9px;
  background: #fdfaf4;
}

.chat-line {
  padding: 6px 0;
  border-bottom: 1px dashed #ece4d8;
}

.chat-meta {
  display: flex;
  justify-content: space-between;
  font-size: 10.5px;
  color: var(--wg-muted);
}

.chat-who {
  color: var(--wg-clay);
  font-weight: 500;
}

.chat-line p {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--wg-text-soft);
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px solid #f0e9de;
}
</style>
