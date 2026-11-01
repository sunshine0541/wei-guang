<template>
  <div v-if="user" class="wg-page">
    <div class="detail-head">
      <button class="back-btn" @click="goBack">
        <AdminIcon name="arrowLeft" :size="15" />
        返回用户管理
      </button>
      <div class="head-actions">
        <el-button v-if="user.status === 'NORMAL'" type="danger" size="small" plain @click="doBan(true)">封禁</el-button>
        <el-button v-if="user.status === 'BANNED'" type="success" size="small" plain @click="doBan(false)">解封</el-button>
        <el-button type="primary" size="small" @click="openAdjust">微光值调整</el-button>
      </div>
    </div>

    <div class="hero">
      <div class="hero-avatar">{{ (user.nickname || '匿名').slice(0, 1) }}</div>
      <div class="hero-main">
        <div class="hero-name">
          <h1>{{ user.nickname || '未设昵称' }}</h1>
          <span class="wg-pill" :class="'is-' + userStatusTone(user.status)">
            {{ USER_STATUS_LABELS[user.status] || user.status }}
          </span>
          <span class="wg-pill no-dot" :class="user.uiMode === 'ELDER' ? 'is-amber' : 'is-gray'">
            {{ user.uiMode === 'ELDER' ? '年长模式' : '普通模式' }}
          </span>
        </div>
        <p class="hero-meta">uid {{ user.id }} · {{ user.openid }}</p>
      </div>
    </div>

    <div class="kpi-grid">
      <div class="kpi-card">
        <span>微光值</span>
        <b class="clay">{{ user.creditScore }}</b>
      </div>
      <div class="kpi-card">
        <span>违约计数</span>
        <b>{{ user.violationCount }}</b>
      </div>
      <div class="kpi-card">
        <span>发布求助</span>
        <b>{{ user.publishedCount }}</b>
      </div>
      <div class="kpi-card">
        <span>作为邻居完成</span>
        <b>{{ user.helpedCompletedCount }}</b>
      </div>
    </div>

    <div class="info-grid">
      <section class="wg-panel">
        <div class="wg-panel-head"><h2>账号与画像</h2></div>
        <div class="info-list">
          <div class="info-row"><span>年龄</span><b>{{ user.age ?? '未填' }}</b></div>
          <div class="info-row"><span>手机号</span><b>{{ user.phone || '—' }}</b></div>
          <div class="info-row"><span>签名</span><b>{{ user.signature || '—' }}</b></div>
          <div class="info-row"><span>喜欢做的事</span><b>{{ (user.hobbies || []).join('、') || '—' }}</b></div>
          <div class="info-row"><span>需要留意</span><b>{{ user.careTip || '—' }}</b></div>
          <div class="info-row">
            <span>紧急联系人</span>
            <b>{{ user.emergencyContactName || '—' }} {{ user.emergencyContactPhone || '' }}</b>
          </div>
        </div>
      </section>

      <section class="wg-panel">
        <div class="wg-panel-head"><h2>光语与互助</h2></div>
        <div class="info-list">
          <div class="info-row"><span>光语状态</span><b>{{ LIGHT_STATE_LABELS[user.lightState] || user.lightState }}</b></div>
          <div class="info-row"><span>光已传递</span><b>{{ user.lightPassed === 1 ? '是' : '否' }}</b></div>
          <div class="info-row"><span>曾被帮助</span><b>{{ user.helpedBefore === 1 ? '是' : '否' }}</b></div>
          <div class="info-row"><span>发布求助数</span><b>{{ user.publishedCount }}</b></div>
          <div class="info-row"><span>发布且完成</span><b>{{ user.completedCount }}</b></div>
          <div class="info-row"><span>作为邻居完成</span><b>{{ user.helpedCompletedCount }}</b></div>
        </div>
      </section>
    </div>

    <section class="wg-panel ledger">
      <div class="wg-panel-head">
        <div>
          <h2>微光值明细</h2>
          <p>最近 10 条 · 当前余额 {{ user.credit?.balance }}</p>
        </div>
      </div>
      <el-table :data="user.credit?.records || []" size="small">
        <el-table-column label="变化" width="80">
          <template #default="{ row }">
            <span :class="row.delta >= 0 ? 'plus' : 'minus'">{{ row.delta > 0 ? '+' + row.delta : row.delta }}</span>
          </template>
        </el-table-column>
        <el-table-column label="原因" width="160">
          <template #default="{ row }">{{ CREDIT_REASON_LABELS[row.reasonCode] || row.reasonCode }}</template>
        </el-table-column>
        <el-table-column prop="balanceAfter" label="余额" width="80" />
        <el-table-column prop="remark" label="备注" min-width="180" />
        <el-table-column label="时间" width="118">
          <template #default="{ row }">{{ formatShort(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </section>

    <section class="wg-panel ledger">
      <div class="wg-panel-head">
        <div>
          <h2>积分明细</h2>
          <p>最近 10 条 · 当前余额 {{ user.points?.balance }}</p>
        </div>
      </div>
      <el-table :data="user.points?.records || []" size="small">
        <el-table-column label="变化" width="80">
          <template #default="{ row }">
            <span :class="row.delta >= 0 ? 'plus' : 'minus'">{{ row.delta > 0 ? '+' + row.delta : row.delta }}</span>
          </template>
        </el-table-column>
        <el-table-column label="原因" width="160">
          <template #default="{ row }">{{ POINT_REASON_LABELS[row.reasonCode] || row.reasonCode }}</template>
        </el-table-column>
        <el-table-column prop="balanceAfter" label="余额" width="80" />
        <el-table-column label="时间" width="118">
          <template #default="{ row }">{{ formatShort(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="adjustVisible" title="微光值人工处置" width="480px">
      <el-form :model="adjustForm" label-width="82px">
        <el-form-item label="变化值" required>
          <el-input-number v-model="adjustForm.delta" :min="-500" :max="500" :step="5" />
          <span class="muted ml">正数增加 / 负数扣减（余额下限 0）</span>
        </el-form-item>
        <el-form-item label="备注" required>
          <el-input v-model="adjustForm.remark" maxlength="200" placeholder="处置原因（必填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="adjusting" @click="submitAdjust">提交</el-button>
      </template>
    </el-dialog>
  </div>

  <div v-else class="wg-page loading-state">
    <div v-if="loading" class="wg-skeleton" style="height: 180px"></div>
    <div v-else class="load-fail">
      <p>{{ loadError ? '用户信息加载失败' : '用户不存在' }}</p>
      <el-button @click="load">重新加载</el-button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminIcon from '../components/AdminIcon.vue'
import { banUser, creditAdjust, getUserDetail, unbanUser } from '../api/admin'
import {
  CREDIT_REASON_LABELS,
  LIGHT_STATE_LABELS,
  POINT_REASON_LABELS,
  USER_STATUS_LABELS
} from '../utils/labels'
import { formatShort } from '../utils/format'
import { userStatusTone } from '../utils/status'

const route = useRoute()
const router = useRouter()
const user = ref(null)
const loading = ref(true)
const loadError = ref(false)
const adjustVisible = ref(false)
const adjusting = ref(false)
const adjustForm = reactive({ delta: 0, remark: '' })

async function load() {
  loading.value = true
  loadError.value = false
  try {
    user.value = await getUserDetail(route.params.id)
  } catch (e) {
    user.value = null
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/users')
}

async function doBan(toBan) {
  const label = toBan ? '封禁' : '解封'
  const confirmed = await ElMessageBox.confirm(`确认${label}用户「${user.value.nickname || user.value.id}」？`, `${label}确认`, {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  })
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return

  if (toBan) {
    await banUser(user.value.id)
  } else {
    await unbanUser(user.value.id)
  }
  ElMessage.success(`已${label}`)
  load()
}

function openAdjust() {
  adjustForm.delta = 0
  adjustForm.remark = ''
  adjustVisible.value = true
}

async function submitAdjust() {
  if (!adjustForm.delta) {
    ElMessage.warning('变化值不能为 0')
    return
  }
  if (!adjustForm.remark.trim()) {
    ElMessage.warning('请填写备注')
    return
  }
  adjusting.value = true
  try {
    await creditAdjust(user.value.id, { delta: adjustForm.delta, remark: adjustForm.remark.trim() })
    ElMessage.success('已调整，用户会收到通知')
    adjustVisible.value = false
    load()
  } finally {
    adjusting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
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

.head-actions {
  display: flex;
  gap: 8px;
}

.hero {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border: 1px solid var(--wg-line);
  border-radius: 12px;
  background: var(--wg-panel);
}

.hero-avatar {
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #e9ded0;
  color: #8a6a4d;
  font-size: 19px;
  font-weight: 600;
}

.hero-name {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-wrap: wrap;
}

.hero-name h1 {
  margin: 0;
  font-size: 19px;
  font-weight: 600;
}

.hero-meta {
  margin: 5px 0 0;
  font-size: 11px;
  color: var(--wg-muted);
  word-break: break-all;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-top: 14px;
}

.kpi-card {
  padding: 14px 18px;
  border: 1px solid var(--wg-line);
  border-radius: 12px;
  background: var(--wg-panel);
}

.kpi-card span {
  font-size: 11.5px;
  color: var(--wg-muted);
}

.kpi-card b {
  display: block;
  margin-top: 7px;
  font-size: 24px;
  font-weight: 600;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.kpi-card b.clay {
  color: var(--wg-clay);
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-top: 14px;
  align-items: start;
}

.info-list {
  padding: 0 18px 16px;
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
  width: 82px;
  flex-shrink: 0;
  color: var(--wg-muted);
}

.info-row b {
  flex: 1;
  font-weight: 400;
  color: var(--wg-text-soft);
  word-break: break-all;
}

.ledger {
  margin-top: 14px;
  padding-bottom: 10px;
}

.ledger :deep(.el-table) {
  padding: 0 8px;
}

.plus {
  color: #5e8c61;
  font-variant-numeric: tabular-nums;
}

.minus {
  color: var(--wg-danger);
  font-variant-numeric: tabular-nums;
}

.muted {
  color: var(--wg-muted);
  font-size: 11px;
}

.ml {
  margin-left: 8px;
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
