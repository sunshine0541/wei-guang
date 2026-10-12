<template>
  <div v-if="order">
    <el-card>
      <template #header>
        <div class="head">
          <span>求助 #{{ order.id }} · {{ order.title }}</span>
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
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelArbitrate, getOrderDetail } from '../api/admin'
import { ORDER_STATUS_LABELS, ORDER_TYPE_LABELS, TIMELINE_LABELS, OPERATOR_ROLE_LABELS } from '../utils/labels'

const route = useRoute()
const order = ref(null)
const chatVisible = ref(false)
const arbitrating = ref(false)
const arbitrateForm = reactive({ conclusion: '' })

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
</script>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
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