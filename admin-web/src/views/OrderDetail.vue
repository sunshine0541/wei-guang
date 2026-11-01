<template>
  <div v-if="order" class="wg-page">
    <div class="detail-head">
      <button class="back-btn" @click="goBack">
        <AdminIcon name="arrowLeft" :size="15" />
        返回订单列表
      </button>
      <div class="head-right">
        <span class="wg-pill" :class="'is-' + orderStatusTone(order.status)">
          {{ ORDER_STATUS_LABELS[order.status] || order.status }}
        </span>
        <span v-if="order.cancelPendingBy" class="wg-pill is-gray no-dot">协商挂起</span>
      </div>
    </div>

    <h1 class="detail-title">{{ order.title }}</h1>
    <p class="detail-sub">
      #{{ order.id }} · {{ ORDER_TYPE_LABELS[order.orderType] || order.orderType }} ·
      {{ formatDateTime(order.createdAt) }} 发布 · 浏览 {{ order.viewCount ?? 0 }}
    </p>

    <div v-if="order.suspicious || order.riskReviewedAt" class="risk-banner" :class="{ reviewed: order.riskReviewedAt }">
      <div class="risk-icon"><AdminIcon name="alert" :size="17" /></div>
      <div class="risk-main">
        <p class="risk-title">
          {{ order.riskReviewedAt ? '风险已核实' : '用时低于保底，待人工核实' }}
        </p>
        <p class="risk-desc">
          实际用时 {{ order.durationMinutes }} 分钟 / 保底 {{ order.minMinutes }} 分钟
          <span v-if="(order.riskCategories || []).length">（{{ order.riskCategories.join('、') }}）</span>
        </p>
        <p v-if="order.riskReviewedAt" class="risk-desc">
          {{ order.riskReviewResult === 'DEDUCT' ? '确认有问题，已扣分' : '核实结论：正常' }}
          <span v-if="order.riskReviewNote">· {{ order.riskReviewNote }}</span>
        </p>
      </div>
      <button v-if="order.riskReviewAvailable" class="risk-jump" @click="focusRisk">立即核实</button>
    </div>

    <div class="detail-grid">
      <section class="card info-card">
        <div class="card-head"><h2>求助信息</h2></div>
        <div class="info-list">
          <div class="info-row"><span>类型</span><b>{{ ORDER_TYPE_LABELS[order.orderType] }}</b></div>
          <div class="info-row"><span>急标</span><b>{{ order.urgentFlag === 1 ? '急' : '否' }}</b></div>
          <div class="info-row"><span>紧急度</span><b>{{ order.urgencyScore }}</b></div>
          <div class="info-row"><span>时间期望</span><b>{{ order.timeExpect || '—' }}</b></div>
          <div class="info-row"><span>地址</span><b>{{ order.addressArea || '—' }} {{ order.addressDetail || '' }}</b></div>
          <div class="info-row"><span>发布</span><b>{{ formatDateTime(order.createdAt) }}</b></div>
          <div class="info-row"><span>待响应截止</span><b>{{ formatDateTime(order.publishDeadline) }}</b></div>
          <div class="info-row"><span>轮次 / 释放</span><b>{{ order.currentRound }} 轮 / {{ order.releaseCount }} 次</b></div>
        </div>
        <div v-if="(order.tags || []).length" class="tag-row">
          <span v-for="tag in order.tags" :key="tag" class="tag">{{ tag }}</span>
        </div>
        <div class="desc-block">
          <span>求助描述</span>
          <p>{{ order.description || '—' }}</p>
        </div>
        <div v-if="order.cancelReason" class="cancel-block">
          <span>取消原因</span>
          <p>{{ order.cancelReason }}</p>
        </div>
      </section>

      <section class="card stream-card">
        <div class="card-head stream-head">
          <h2>过程记录</h2>
          <div class="seg">
            <button class="seg-btn" :class="{ active: activeTab === 'timeline' }" @click="activeTab = 'timeline'">
              时间线
            </button>
            <button class="seg-btn" :class="{ active: activeTab === 'chat' }" @click="activeTab = 'chat'">
              聊天记录
            </button>
          </div>
        </div>

        <div v-if="activeTab === 'timeline'" class="timeline">
          <div v-for="(item, index) in (order.timeline || [])" :key="index" class="tl-item">
            <span class="tl-dot" :class="'tone-' + timelineTone(item.eventType)"></span>
            <div class="tl-body">
              <p class="tl-title">
                {{ TIMELINE_LABELS[item.eventType] || item.eventType }}
                <span class="tl-role">{{ OPERATOR_ROLE_LABELS[item.operatorRole] || item.operatorRole }}</span>
              </p>
              <p v-if="item.remark" class="tl-remark">{{ item.remark }}</p>
              <span class="tl-time">{{ formatDateTime(item.createdAt) }}</span>
            </div>
          </div>
          <p v-if="!(order.timeline || []).length" class="muted">暂无时间线</p>
        </div>

        <div v-else class="chat-box">
          <template v-if="order.chatAvailable">
            <div v-for="msg in (order.chatMessages || [])" :key="msg.msgId" class="chat-line">
              <div class="chat-meta">
                <span class="chat-who">{{ msg.senderId === 0 ? '系统' : 'uid' + msg.senderId }}</span>
                <span>{{ formatDateTime(msg.createdAt) }}</span>
              </div>
              <p>{{ msg.content }}</p>
            </div>
            <p v-if="!(order.chatMessages || []).length" class="muted">暂无聊天记录</p>
          </template>
          <div v-else class="chat-locked">
            <AdminIcon name="message" :size="18" />
            <p>本单没有申诉或高危上报关联，聊天记录不开放</p>
          </div>
        </div>
      </section>

      <aside class="right-col">
        <section class="card side-card">
          <div class="card-head"><h2>参与双方</h2></div>
          <div class="party" @click="openUser(order.requester)">
            <div class="party-avatar">{{ (order.requester?.nickname || '—').slice(0, 1) }}</div>
            <div class="party-main">
              <p>
                {{ order.requester?.nickname || '—' }}
                <span class="wg-pill" :class="'is-' + userStatusTone(order.requester?.status)">
                  {{ USER_STATUS_LABELS[order.requester?.status] || order.requester?.status }}
                </span>
              </p>
              <span>发布者 · uid {{ order.requester?.id }}</span>
            </div>
            <AdminIcon name="chevronRight" :size="14" class="party-arrow" />
          </div>
          <div class="party" :class="{ disabled: !order.helper }" @click="openUser(order.helper)">
            <div class="party-avatar helper">{{ (order.helper?.nickname || '待').slice(0, 1) }}</div>
            <div class="party-main">
              <p>
                {{ order.helper?.nickname || '待接单' }}
                <span v-if="order.helper" class="wg-pill" :class="'is-' + userStatusTone(order.helper.status)">
                  {{ USER_STATUS_LABELS[order.helper.status] || order.helper.status }}
                </span>
              </p>
              <span>{{ order.helper ? '接单者 · uid ' + order.helper.id : '尚未有人接单' }}</span>
            </div>
            <AdminIcon v-if="order.helper" name="chevronRight" :size="14" class="party-arrow" />
          </div>
        </section>

        <section v-if="order.cancelArbitrateAvailable" class="card side-card action-card warn-card">
          <div class="card-head">
            <div>
              <h2>协商取消裁决</h2>
              <p>本单挂起，等待管理员裁决</p>
            </div>
          </div>
          <div class="action-body">
            <div class="field">
              <label>裁决结果</label>
              <el-radio-group v-model="arbitrateForm.decision">
                <el-radio value="CANCEL">裁决取消</el-radio>
                <el-radio value="KEEP">维持原状态</el-radio>
              </el-radio-group>
            </div>
            <div class="field">
              <label>裁决说明 <i>*</i></label>
              <el-input v-model="arbitrateForm.conclusion" type="textarea" :rows="3" maxlength="200" placeholder="说明裁决依据（必填）" />
            </div>
            <div class="action-buttons">
              <el-button type="primary" :loading="arbitrating" @click="doArbitrate">提交裁决</el-button>
            </div>
            <p class="action-note">裁决结果会直接落回订单，并通知双方。</p>
          </div>
        </section>

        <section v-if="order.riskReviewAvailable" id="risk-panel" class="card side-card action-card risk-card">
          <div class="card-head">
            <div>
              <h2>风险核实</h2>
              <p>只记录核实结果，不直接扣分</p>
            </div>
          </div>
          <div class="action-body">
            <div class="field">
              <label>核实结果</label>
              <el-radio-group v-model="riskForm.result">
                <el-radio value="NORMAL">正常</el-radio>
                <el-radio value="DEDUCT">确认有问题</el-radio>
              </el-radio-group>
            </div>
            <div class="field">
              <label>备注</label>
              <el-input v-model="riskForm.note" type="textarea" :rows="2" maxlength="200" placeholder="联系情况或核实说明（可选）" />
            </div>
            <div class="action-buttons">
              <el-button type="primary" :loading="reviewing" @click="doRiskReview">标记已核实</el-button>
            </div>
            <p class="action-note">如需扣减微光值，请到用户详情使用「微光值调整」，再回来标记。</p>
          </div>
        </section>

        <section v-if="!order.cancelArbitrateAvailable && !order.riskReviewAvailable" class="card side-card no-action">
          <AdminIcon name="check" :size="18" />
          <p>本单暂无待处置项</p>
          <span>如需调整用户微光值，请进入用户详情操作。</span>
        </section>
      </aside>
    </div>
  </div>

  <div v-else class="wg-page loading-state">
    <div v-if="loading" class="wg-skeleton" style="height: 160px"></div>
    <div v-else class="load-fail">
      <p>订单加载失败</p>
      <el-button @click="loadOrder">重新加载</el-button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminIcon from '../components/AdminIcon.vue'
import { cancelArbitrate, getOrderDetail, riskReview } from '../api/admin'
import {
  OPERATOR_ROLE_LABELS,
  ORDER_STATUS_LABELS,
  ORDER_TYPE_LABELS,
  TIMELINE_LABELS,
  USER_STATUS_LABELS
} from '../utils/labels'
import { formatDateTime } from '../utils/format'
import { orderStatusTone, userStatusTone } from '../utils/status'

const route = useRoute()
const router = useRouter()

const order = ref(null)
const loading = ref(true)
const activeTab = ref('timeline')
const arbitrating = ref(false)
const reviewing = ref(false)
const arbitrateForm = reactive({ decision: 'CANCEL', conclusion: '' })
const riskForm = reactive({ result: 'NORMAL', note: '' })

function timelineTone(type) {
  if (['ARBITRATE', 'CANCEL', 'EXPIRE', 'CANCEL_CONFIRM_REJECT'].includes(type)) return 'red'
  if (['COMPLETE', 'CONFIRM'].includes(type)) return 'green'
  if (['APPEAL_OPEN', 'RELEASE', 'CANCEL_REQUEST'].includes(type)) return 'amber'
  return 'blue'
}

async function loadOrder() {
  loading.value = true
  try {
    order.value = await getOrderDetail(route.params.id)
  } catch (e) {
    order.value = null
  } finally {
    loading.value = false
  }
}

async function doArbitrate() {
  const conclusion = arbitrateForm.conclusion.trim()
  if (!conclusion) {
    ElMessage.warning('请填写裁决说明')
    return
  }
  const label = arbitrateForm.decision === 'CANCEL' ? '裁决取消' : '维持原状态'
  const confirmed = await ElMessageBox.confirm(`确认对求助 #${order.value.id} 执行「${label}」？`, '协商取消裁决', {
    type: 'warning',
    confirmButtonText: '确认裁决',
    cancelButtonText: '再想想'
  }).then(() => true).catch(() => false)
  if (!confirmed) return

  arbitrating.value = true
  try {
    await cancelArbitrate(order.value.id, { decision: arbitrateForm.decision, conclusion })
    ElMessage.success('已裁决')
    arbitrateForm.conclusion = ''
    await loadOrder()
  } catch (e) {
    // 请求层已提示
  } finally {
    arbitrating.value = false
  }
}

async function doRiskReview() {
  if (riskForm.result === 'DEDUCT') {
    const confirmed = await ElMessageBox.confirm(
      '确认这单有问题？请确保已通过「用户详情 → 微光值调整」完成扣分；本操作只记录核实结果。',
      '风险核实',
      { type: 'warning', confirmButtonText: '确认已扣分', cancelButtonText: '再想想' }
    ).then(() => true).catch(() => false)
    if (!confirmed) return
  }

  reviewing.value = true
  try {
    await riskReview(order.value.id, { result: riskForm.result, note: riskForm.note || undefined })
    ElMessage.success('已记录核实结果')
    riskForm.note = ''
    await loadOrder()
  } catch (e) {
    // 请求层已提示
  } finally {
    reviewing.value = false
  }
}

function focusRisk() {
  const panel = document.getElementById('risk-panel')
  if (panel) {
    panel.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
}

function openUser(user) {
  if (!user || !user.id) return
  router.push('/users/' + user.id)
}

function goBack() {
  router.push('/orders')
}

onMounted(loadOrder)
</script>

<style scoped>
.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 2px;
  border: none;
  background: none;
  color: var(--wg-muted);
  font-size: 12px;
  cursor: pointer;
}

.back-btn:hover {
  color: var(--wg-clay);
}

.head-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.detail-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.01em;
  line-height: 1.35;
}

.detail-sub {
  margin: 6px 0 0;
  font-size: 11.5px;
  color: var(--wg-muted);
}

.risk-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  padding: 12px 16px;
  border: 1px solid #ecd3ab;
  border-radius: 10px;
  background: #fbf0dc;
}

.risk-banner.reviewed {
  border-color: #d6e3d3;
  background: #eff5ee;
}

.risk-icon {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #f6e2c4;
  color: #9a6a22;
}

.risk-banner.reviewed .risk-icon {
  background: #dfeadd;
  color: #4f6b4c;
}

.risk-main {
  flex: 1;
  min-width: 0;
}

.risk-title {
  margin: 0;
  font-size: 12.5px;
  font-weight: 600;
  color: #8a5f1e;
}

.risk-banner.reviewed .risk-title {
  color: #4f6b4c;
}

.risk-desc {
  margin: 3px 0 0;
  font-size: 11.5px;
  color: #a9772f;
}

.risk-banner.reviewed .risk-desc {
  color: #5f7a5c;
}

.risk-jump {
  flex-shrink: 0;
  height: 30px;
  padding: 0 12px;
  border: 1px solid #d9b97f;
  border-radius: 7px;
  background: #fff8e9;
  color: #8a5f1e;
  font-size: 11.5px;
  cursor: pointer;
}

.risk-jump:hover {
  background: #fff2d8;
}

.detail-grid {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr) 360px;
  gap: 14px;
  margin-top: 14px;
  align-items: start;
}

.card {
  border: 1px solid var(--wg-line);
  border-radius: 12px;
  background: var(--wg-panel);
  box-shadow: 0 1px 2px rgba(46, 42, 38, 0.04);
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 15px 18px 10px;
}

.card-head h2 {
  margin: 0;
  font-size: 13.5px;
  font-weight: 600;
}

.card-head p {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--wg-muted);
}

.info-list {
  padding: 0 18px;
}

.info-row {
  display: flex;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px dashed #f2ece2;
  font-size: 12px;
  line-height: 1.5;
}

.info-row span {
  width: 78px;
  flex-shrink: 0;
  color: var(--wg-muted);
}

.info-row b {
  flex: 1;
  font-weight: 400;
  color: var(--wg-text-soft);
  word-break: break-all;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 10px 18px 2px;
}

.tag {
  padding: 2px 8px;
  border-radius: 5px;
  background: #f4efe6;
  color: #7a6a58;
  font-size: 10.5px;
}

.desc-block,
.cancel-block {
  margin: 12px 18px 16px;
  padding-top: 10px;
  border-top: 1px solid #f0e9de;
}

.desc-block span,
.cancel-block span {
  font-size: 11px;
  color: var(--wg-muted);
}

.desc-block p,
.cancel-block p {
  margin: 6px 0 0;
  font-size: 12.5px;
  line-height: 1.75;
  color: var(--wg-text-soft);
  white-space: pre-wrap;
}

.cancel-block p {
  color: var(--wg-danger);
}

.stream-head {
  align-items: center;
  border-bottom: 1px solid #f0e9de;
  padding-bottom: 12px;
}

.seg {
  display: flex;
  gap: 2px;
  padding: 3px;
  border-radius: 8px;
  background: #f8f4ed;
}

.seg-btn {
  height: 28px;
  padding: 0 12px;
  border: none;
  border-radius: 6px;
  background: none;
  color: var(--wg-muted);
  font-size: 12px;
  cursor: pointer;
}

.seg-btn.active {
  background: var(--wg-panel);
  color: var(--wg-text);
  font-weight: 500;
  box-shadow: 0 1px 2px rgba(46, 42, 38, 0.06);
}

.timeline {
  position: relative;
  padding: 16px 20px 20px;
}

.timeline::before {
  content: '';
  position: absolute;
  left: 25px;
  top: 24px;
  bottom: 26px;
  width: 1px;
  background: #e9e1d5;
}

.tl-item {
  position: relative;
  display: flex;
  gap: 14px;
  padding-bottom: 16px;
}

.tl-dot {
  position: relative;
  z-index: 1;
  width: 10px;
  height: 10px;
  margin-top: 4px;
  flex-shrink: 0;
  border-radius: 50%;
  border: 2px solid var(--wg-panel);
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

.tl-body {
  min-width: 0;
  flex: 1;
}

.tl-title {
  margin: 0;
  font-size: 12.5px;
  color: var(--wg-text);
}

.tl-role {
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 4px;
  background: #f4efe6;
  color: var(--wg-muted);
  font-size: 10.5px;
}

.tl-remark {
  margin: 4px 0 0;
  font-size: 11.5px;
  color: var(--wg-text-soft);
}

.tl-time {
  display: block;
  margin-top: 4px;
  font-size: 10.5px;
  color: var(--wg-muted-light);
}

.chat-box {
  max-height: 560px;
  overflow-y: auto;
  padding: 14px 20px 20px;
}

.chat-line {
  padding: 8px 0;
  border-bottom: 1px dashed #f2ece2;
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
  margin: 5px 0 0;
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--wg-text-soft);
}

.chat-locked {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 50px 20px;
  color: var(--wg-muted);
  font-size: 12px;
}

.chat-locked p {
  margin: 0;
}

.right-col {
  display: flex;
  flex-direction: column;
  gap: 14px;
  position: sticky;
  top: 0;
}

.party {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 16px 8px;
  padding: 9px 10px;
  border: 1px solid #f0e9de;
  border-radius: 9px;
  background: #fdfaf4;
  cursor: pointer;
}

.party:hover {
  border-color: #e2d8ca;
  background: #fbf6ee;
}

.party.disabled {
  cursor: default;
  opacity: 0.75;
}

.party-avatar {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #e9ded0;
  color: #8a6a4d;
  font-size: 12px;
}

.party-avatar.helper {
  background: #e6ede4;
  color: #5a7a57;
}

.party-main {
  flex: 1;
  min-width: 0;
}

.party-main p {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  font-weight: 500;
}

.party-main span {
  font-size: 10.5px;
  color: var(--wg-muted);
}

.party-arrow {
  color: var(--wg-muted-light);
}

.action-card.warn-card {
  border-left: 3px solid var(--wg-amber);
}

.action-card.risk-card {
  border-left: 3px solid var(--wg-danger);
}

.action-body {
  padding: 0 18px 16px;
}

.field {
  margin-bottom: 12px;
}

.field label {
  display: block;
  margin-bottom: 6px;
  font-size: 11.5px;
  color: var(--wg-muted);
}

.field label i {
  color: var(--wg-danger);
  font-style: normal;
}

.action-buttons {
  display: flex;
  justify-content: flex-end;
}

.action-note {
  margin: 8px 0 0;
  font-size: 10.5px;
  line-height: 1.6;
  color: var(--wg-muted);
}

.no-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 30px 18px;
  color: var(--wg-muted);
  text-align: center;
}

.no-action p {
  margin: 0;
  font-size: 12.5px;
  color: var(--wg-text-soft);
}

.no-action span {
  font-size: 10.5px;
}

.loading-state {
  padding-top: 30px;
}

.load-fail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 60px 0;
  color: var(--wg-muted);
}

.load-fail p {
  margin: 0;
  font-size: 13px;
}
</style>
