<template>
  <div v-if="order">
    <el-card>
      <template #header>
        <div class="head">
          <div class="left">
            <el-button link type="primary" @click="goBack">← 返回订单列表</el-button>
            <span>求助 #{{ order.id }} · {{ order.title }}</span>
          </div>
          <el-tag :type="statusTag(order.status)">{{ ORDER_STATUS_LABELS[order.status] || order.status }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="类型">{{ ORDER_TYPE_LABELS[order.orderType] }}</el-descriptions-item>
        <el-descriptions-item label="急标">{{ order.urgentFlag === 1 ? '急' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="紧急度">{{ order.urgencyScore }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ order.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="发布时间期望">{{ order.timeExpect }}</el-descriptions-item>
        <el-descriptions-item label="浏览">{{ order.viewCount }}</el-descriptions-item>
        <el-descriptions-item label="轮次/释放">{{ order.currentRound }} 轮 / {{ order.releaseCount }} 次</el-descriptions-item>
        <el-descriptions-item label="地址">{{ order.addressArea || '—' }} {{ order.addressDetail || '' }}</el-descriptions-item>
        <el-descriptions-item label="标签">{{ (order.tags || []).join('、') || '—' }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="3">{{ order.description || '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-descriptions v-if="order.cancelReason" :column="1" border size="small" class="mt">
        <el-descriptions-item label="取消原因">{{ order.cancelReason }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card class="mt">
      <template #header>参与双方</template>
      <div class="parties">
        <div class="party">
          发布者：{{ order.requester?.nickname || '—' }}
          <el-tag size="small" :type="userTag(order.requester?.status)">{{ userStatus(order.requester?.status) }}</el-tag>
          <span class="muted">uid={{ order.requester?.id }}</span>
        </div>
        <div class="party">
          接单者：{{ order.helper?.nickname || '（未接单）' }}
          <el-tag v-if="order.helper" size="small" :type="userTag(order.helper.status)">{{ userStatus(order.helper.status) }}</el-tag>
          <span v-if="order.helper" class="muted">uid={{ order.helper.id }}</span>
        </div>
      </div>
    </el-card>

    <!-- 4C 保底时长风险核实 -->
    <el-card v-if="order.suspicious || order.riskReviewedAt" class="mt" :class="{ 'risk-box': order.suspicious }">
      <template #header>
        <span :class="order.suspicious ? 'risk-title' : ''">保底时长风险</span>
        <span class="muted">
          实际用时 {{ order.durationMinutes }} 分钟 / 保底 {{ order.minMinutes }} 分钟（{{ (order.riskCategories || []).join('、') }}）
        </span>
      </template>
      <div>
        <el-tag v-if="order.riskReviewedAt" :type="order.riskReviewResult === 'DEDUCT' ? 'danger' : 'success'">
          {{ order.riskReviewResult === 'DEDUCT' ? '已核实：确认有问题（已扣分）' : '已核实：正常' }}
        </el-tag>
        <el-tag v-else type="danger">存疑：用时低于保底，请人工核实</el-tag>
        <span v-if="order.riskReviewedAt" class="muted">核实时间 {{ order.riskReviewedAt }}</span>
        <div v-if="order.riskReviewNote" class="muted">备注：{{ order.riskReviewNote }}</div>
      </div>
      <el-form v-if="order.riskReviewAvailable" inline class="mt">
        <el-form-item label="核实结果">
          <el-radio-group v-model="riskForm.result">
            <el-radio value="NORMAL">正常</el-radio>
            <el-radio value="DEDUCT">确认有问题（微光值已调整）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="riskForm.note" placeholder="联系情况/核实说明（可选）" style="width: 280px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="reviewing" @click="doRiskReview">标记已核实</el-button>
        </el-form-item>
      </el-form>
      <div v-if="order.riskReviewAvailable" class="muted">
        如需扣减微光值，请到发布者/接单者的用户详情使用「微光值调整」，再回到这里标记。
      </div>
    </el-card>

    <!-- 协商取消裁决操作区：仅挂起单显示（详设 §6.3） -->
    <el-card v-if="order.cancelArbitrateAvailable" class="mt arbitrate-box">
      <template #header>
        <span class="warn-title">协商取消裁决</span>
        <span class="muted">本单正挂起协商取消，等待管理员裁决</span>
      </template>
      <el-form inline>
        <el-form-item label="结论">
          <el-input v-model="arbitrateForm.conclusion" placeholder="裁决说明（必填）" style="width: 320px" />
        </el-form-item>
        <el-form-item>
          <el-button type="danger" :loading="arbitrating" @click="doArbitrate('CANCEL')">裁决取消</el-button>
          <el-button type="primary" :loading="arbitrating" @click="doArbitrate('KEEP')">维持原状态</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="mt">
      <template #header>
        <div class="head">
          <span>状态时间线</span>
          <el-button v-if="order.chatAvailable" size="small" @click="chatVisible = !chatVisible">
            {{ chatVisible ? '收起聊天记录' : '查看聊天记录' }}
          </el-button>
        </div>
      </template>
      <el-timeline>
        <el-timeline-item
          v-for="(item, index) in order.timeline"
          :key="index"
          :timestamp="item.createdAt"
          :type="timelineTag(item.eventType)"
        >
          {{ TIMELINE_LABELS[item.eventType] || item.eventType }}（{{ OPERATOR_ROLE_LABELS[item.operatorRole] || item.operatorRole }}）
          <div v-if="item.remark" class="muted">{{ item.remark }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <el-card v-if="chatVisible" class="mt">
      <template #header>聊天记录（仅申诉/上报关联单开放）</template>
      <div v-for="msg in order.chatMessages" :key="msg.msgId" class="chat-line">
        <span class="muted">#{{ msg.msgId }} [{{ msg.senderId === 0 ? '系统' : 'uid' + msg.senderId }}] {{ msg.createdAt }}</span>
        <div>{{ msg.content }}</div>
      </div>
      <el-empty v-if="!order.chatMessages || order.chatMessages.length === 0" description="暂无聊天记录" />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelArbitrate, getOrderDetail, riskReview } from '../api/admin'
import { ORDER_STATUS_LABELS, ORDER_TYPE_LABELS, TIMELINE_LABELS, OPERATOR_ROLE_LABELS } from '../utils/labels'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const chatVisible = ref(false)
const arbitrating = ref(false)
const arbitrateForm = reactive({ conclusion: '' })
const reviewing = ref(false)
const riskForm = reactive({ result: 'NORMAL', note: '' })

function statusTag(status) {
  if (status === 'COMPLETED') return 'success'
  if (status === 'CANCELLED' || status === 'EXPIRED') return 'info'
  if (status === 'APPEALING' || status === 'PENDING_CONFIRM') return 'warning'
  return 'primary'
}

function userTag(status) {
  if (status === 'BANNED') return 'danger'
  if (status === 'CANCELLED') return 'info'
  return 'success'
}

function userStatus(status) {
  return { NORMAL: '正常', BANNED: '已封禁', CANCELLED: '已注销' }[status] || status || '—'
}

function timelineTag(event) {
  if (event === 'ARBITRATE' || event === 'CANCEL' || event === 'EXPIRE') return 'danger'
  if (event === 'COMPLETE' || event === 'CONFIRM') return 'success'
  if (event === 'APPEAL_OPEN') return 'warning'
  return 'primary'
}

async function doArbitrate(decision) {
  const conclusion = arbitrateForm.conclusion.trim()
  if (!conclusion) {
    ElMessage.warning('请填写裁决说明')
    return
  }
  const label = decision === 'CANCEL' ? '裁决取消' : '维持原状态'
  try {
    await ElMessageBox.confirm(`确认对求助 #${order.value.id} 执行「${label}」？`, '协商取消裁决', {
      type: 'warning',
      confirmButtonText: '确认裁决',
      cancelButtonText: '再想想'
    })
  } catch {
    return
  }
  arbitrating.value = true
  try {
    await cancelArbitrate(order.value.id, { decision, conclusion })
    ElMessage.success('已裁决')
    order.value = await getOrderDetail(order.value.id)
    arbitrateForm.conclusion = ''
  } finally {
    arbitrating.value = false
  }
}

onMounted(async () => {
  order.value = await getOrderDetail(route.params.id)
})

async function doRiskReview() {
  if (riskForm.result === 'DEDUCT') {
    try {
      await ElMessageBox.confirm(
        '确认这单有问题？请确保已通过「用户详情 → 微光值调整」完成扣分；本操作只记录核实结果。',
        '风险核实',
        { type: 'warning', confirmButtonText: '确认已扣分', cancelButtonText: '再想想' }
      )
    } catch {
      return
    }
  }
  reviewing.value = true
  try {
    await riskReview(order.value.id, { result: riskForm.result, note: riskForm.note || undefined })
    ElMessage.success('已记录核实结果')
    order.value = await getOrderDetail(order.value.id)
    riskForm.note = ''
  } finally {
    reviewing.value = false
  }
}

function goBack() {
  router.push('/orders')
}
</script>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.mt {
  margin-top: 16px;
}

.parties {
  display: flex;
  gap: 40px;
}

.party {
  font-size: 14px;
}

.muted {
  color: #999;
  font-size: 12px;
  margin-left: 6px;
}

.arbitrate-box {
  border: 1px solid #e6a23c;
}

.risk-box {
  border: 1px solid #f56c6c;
}

.risk-title {
  color: #f56c6c;
  font-weight: 600;
  margin-right: 12px;
}

.warn-title {
  color: #e6a23c;
  font-weight: 600;
  margin-right: 12px;
}

.chat-line {
  padding: 6px 0;
  border-bottom: 1px dashed #ebeef5;
}
</style>