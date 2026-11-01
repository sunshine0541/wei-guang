<template>
  <div class="wg-page">
    <div class="toolbar">
      <p class="summary">
        共 <b>{{ total }}</b> 条求助，当前第 {{ query.page }} 页
        <span v-if="query.keyword" class="filter-hint">· 关键词「{{ query.keyword }}」</span>
      </p>
      <div class="toolbar-actions">
        <button class="ghost-btn" :disabled="loading" @click="load(query.page)">
          <AdminIcon name="refresh" :size="14" />刷新
        </button>
        <button class="ghost-btn" :disabled="!list.length" @click="exportRows(list, '当前页')">
          <AdminIcon name="download" :size="14" />导出当前页
        </button>
      </div>
    </div>

    <div class="filter-card">
      <div class="chips">
        <button class="chip" :class="{ active: !query.status }" @click="pickStatus('')">
          全部
        </button>
        <button
          v-for="(label, value) in ORDER_STATUS_LABELS"
          :key="value"
          class="chip"
          :class="{ active: query.status === value }"
          @click="pickStatus(value)"
        >
          {{ label }}
        </button>
      </div>
      <div class="filter-row">
        <el-input
          v-model="query.keyword"
          class="search-input"
          placeholder="搜索求助标题关键词"
          clearable
          @keyup.enter="load(1)"
          @clear="load(1)"
        />
        <el-select v-model="query.orderType" class="type-select" placeholder="全部类型" clearable @change="load(1)">
          <el-option v-for="(label, value) in ORDER_TYPE_LABELS" :key="value" :label="label" :value="value" />
        </el-select>
        <el-select v-model="riskFilter" class="risk-select" placeholder="全部风险" clearable @change="load(1)">
          <el-option label="只看存疑" value="SUSPICIOUS" />
          <el-option label="已核实" value="REVIEWED" />
        </el-select>
        <el-button type="primary" @click="load(1)">查询</el-button>
        <button class="text-btn" @click="reset">重置</button>
      </div>
    </div>

    <div class="table-card">
      <el-table
        ref="tableRef"
        :data="list"
        v-loading="loading"
        row-key="id"
        class="order-table"
        @row-click="onRowClick"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="44" />
        <el-table-column prop="id" label="ID" width="66">
          <template #default="{ row }">
            <span class="order-id">#{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="求助标题" min-width="220">
          <template #default="{ row }">
            <div class="title-cell">
              <span class="title-text">{{ row.title }}</span>
              <span v-if="row.urgentFlag === 1" class="urgent">急</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="92">
          <template #default="{ row }">{{ ORDER_TYPE_LABELS[row.orderType] || row.orderType }}</template>
        </el-table-column>
        <el-table-column label="状态" width="128">
          <template #default="{ row }">
            <div class="status-cell">
              <span class="wg-pill" :class="'is-' + orderStatusTone(row.status)">
                {{ ORDER_STATUS_LABELS[row.status] || row.status }}
              </span>
              <span v-if="row.cancelPendingBy" class="wg-pill is-gray no-dot">协商挂起</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="双方" width="140">
          <template #default="{ row }">
            <div class="people-cell">
              <span>{{ row.requesterNickname || '—' }}</span>
              <span class="muted">→ {{ row.helperNickname || '待接单' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="用时 / 保底" width="120">
          <template #default="{ row }">
            <div v-if="row.durationMinutes !== null && row.durationMinutes !== undefined" class="duration-cell">
              <span>{{ row.durationMinutes }} / {{ row.minMinutes }} 分</span>
              <div class="duration-track">
                <i
                  :style="{
                    width: durationWidth(row),
                    background: row.suspicious ? 'var(--wg-danger)' : 'var(--wg-clay)'
                  }"
                ></i>
              </div>
            </div>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="风险" width="94">
          <template #default="{ row }">
            <span v-if="riskLabel(row)" class="wg-pill" :class="'is-' + riskTone(row)">{{ riskLabel(row) }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="118">
          <template #default="{ row }">{{ formatShort(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="148" fixed="right">
          <template #default="{ row }">
            <div class="row-actions" @click.stop>
              <span class="wg-link" @click="openDrawer(row)">详情</span>
              <span v-if="row.suspicious && !row.riskReviewedAt" class="wg-link danger" @click="quickVerify(row)">
                核实
              </span>
              <span v-if="row.cancelPendingBy" class="wg-link" @click="router.push('/orders/' + row.id)">
                裁决
              </span>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-size="query.pageSize"
        :page-sizes="[10, 20, 50]"
        :current-page="query.page"
        @current-change="onPage"
        @size-change="onSizeChange"
      />
    </div>

    <div v-if="selected.length" class="batch-bar">
      <span>已选 <b>{{ selected.length }}</b> 项</span>
      <button class="batch-btn" :disabled="!canBatchVerify || batchLoading" @click="batchVerify">
        {{ batchLoading ? '核实中…' : '批量标记已核实' }}
      </button>
      <button class="batch-btn plain" @click="exportRows(selected, '选中项')">导出选中</button>
      <button class="batch-btn plain" @click="clearSelection">取消选择</button>
    </div>

    <el-drawer v-model="drawerVisible" size="480px" :with-header="false">
      <div v-loading="drawerLoading" class="drawer-body">
        <template v-if="detail">
          <div class="drawer-head">
            <div>
              <p class="drawer-id">#{{ detail.id }}</p>
              <h3>{{ detail.title }}</h3>
            </div>
            <span class="wg-pill" :class="'is-' + orderStatusTone(detail.status)">
              {{ ORDER_STATUS_LABELS[detail.status] || detail.status }}
            </span>
          </div>

          <div class="drawer-meta">
            <span>{{ ORDER_TYPE_LABELS[detail.orderType] || detail.orderType }}</span>
            <span>·</span>
            <span>{{ formatShort(detail.createdAt) }} 发布</span>
            <span>·</span>
            <span>{{ detail.addressArea || '无需地址' }}</span>
          </div>

          <div v-if="detail.suspicious || detail.riskReviewedAt" class="risk-banner">
            <AdminIcon name="alert" :size="15" />
            <div>
              <p>用时 {{ detail.durationMinutes }} 分钟 / 保底 {{ detail.minMinutes }} 分钟</p>
              <span v-if="detail.riskReviewedAt">
                已核实：{{ detail.riskReviewResult === 'DEDUCT' ? '确认有问题（已扣分）' : '正常' }}
              </span>
              <span v-else>用时低于保底，建议人工核实</span>
            </div>
          </div>

          <section class="drawer-section">
            <h4>求助信息</h4>
            <div class="kv"><span>描述</span><b>{{ detail.description || '—' }}</b></div>
            <div class="kv"><span>时间期望</span><b>{{ detail.timeExpect || '—' }}</b></div>
            <div class="kv"><span>地址</span><b>{{ detail.addressArea || '—' }} {{ detail.addressDetail || '' }}</b></div>
            <div class="kv"><span>标签</span><b>{{ (detail.tags || []).join('、') || '—' }}</b></div>
            <div class="kv"><span>轮次 / 释放</span><b>{{ detail.currentRound }} 轮 / {{ detail.releaseCount }} 次</b></div>
            <div v-if="detail.cancelReason" class="kv"><span>取消原因</span><b>{{ detail.cancelReason }}</b></div>
          </section>

          <section class="drawer-section">
            <h4>参与双方</h4>
            <div class="party-card">
              <div class="party-avatar">{{ (detail.requester?.nickname || '—').slice(0, 1) }}</div>
              <div>
                <p>{{ detail.requester?.nickname || '—' }}</p>
                <span>发布者 · uid {{ detail.requester?.id }}</span>
              </div>
            </div>
            <div class="party-card">
              <div class="party-avatar helper">{{ (detail.helper?.nickname || '待').slice(0, 1) }}</div>
              <div>
                <p>{{ detail.helper?.nickname || '待接单' }}</p>
                <span v-if="detail.helper">接单者 · uid {{ detail.helper.id }}</span>
                <span v-else>尚未有人接单</span>
              </div>
            </div>
          </section>

          <section class="drawer-section">
            <h4>状态时间线</h4>
            <div v-if="(detail.timeline || []).length" class="mini-timeline">
              <div v-for="(item, index) in detail.timeline.slice(0, 4)" :key="index" class="mini-line">
                <i></i>
                <div>
                  <p>{{ TIMELINE_LABELS[item.eventType] || item.eventType }}</p>
                  <span>{{ formatShort(item.createdAt) }} · {{ OPERATOR_ROLE_LABELS[item.operatorRole] || item.operatorRole }}</span>
                </div>
              </div>
            </div>
            <p v-else class="muted">暂无时间线</p>
          </section>

          <div class="drawer-actions">
            <el-button @click="drawerVisible = false">关闭</el-button>
            <el-button type="primary" @click="router.push('/orders/' + detail.id)">打开完整详情</el-button>
          </div>
        </template>
        <p v-else-if="!drawerLoading" class="muted">加载失败，请稍后重试</p>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminIcon from '../components/AdminIcon.vue'
import { getOrderDetail, listOrders, riskReview } from '../api/admin'
import {
  OPERATOR_ROLE_LABELS,
  ORDER_STATUS_LABELS,
  ORDER_TYPE_LABELS,
  TIMELINE_LABELS
} from '../utils/labels'
import { formatShort } from '../utils/format'
import { orderStatusTone, riskTone } from '../utils/status'

const route = useRoute()
const router = useRouter()

const query = reactive({ status: '', orderType: '', keyword: '', page: 1, pageSize: 20 })
const riskFilter = ref('')
const list = ref([])
const total = ref(0)
const loading = ref(false)
const selected = ref([])
const batchLoading = ref(false)
const tableRef = ref(null)

const drawerVisible = ref(false)
const drawerLoading = ref(false)
const detail = ref(null)
let listSeq = 0
let drawerSeq = 0

const canBatchVerify = computed(() =>
  selected.value.length > 0 && selected.value.every((row) => row.suspicious && !row.riskReviewedAt)
)

function riskLabel(row) {
  if (row.riskReviewedAt) {
    return row.riskReviewResult === 'DEDUCT' ? '已扣分' : '已核实'
  }
  return row.suspicious ? '存疑' : ''
}

function durationWidth(row) {
  const min = Number(row.minMinutes) || 0
  const duration = Number(row.durationMinutes) || 0
  if (min <= 0) return '100%'
  return Math.min(100, Math.round((duration / min) * 100)) + '%'
}

function pickStatus(status) {
  query.status = status
  load(1)
}

async function load(page) {
  query.page = page || query.page
  const seq = ++listSeq
  loading.value = true
  try {
    const data = await listOrders({
      status: query.status || undefined,
      orderType: query.orderType || undefined,
      keyword: query.keyword || undefined,
      riskStatus: riskFilter.value || undefined,
      page: query.page,
      pageSize: query.pageSize
    })
    if (seq !== listSeq) return
    list.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    // 请求层已提示，保留旧列表
  } finally {
    if (seq === listSeq) {
      loading.value = false
    }
  }
}

function onPage(page) {
  load(page)
}

function onSizeChange(size) {
  query.pageSize = size
  load(1)
}

function reset() {
  query.status = ''
  query.orderType = ''
  query.keyword = ''
  riskFilter.value = ''
  load(1)
}

function onSelectionChange(rows) {
  selected.value = rows
}

function clearSelection() {
  selected.value = []
  if (tableRef.value) {
    tableRef.value.clearSelection()
  }
}

function onRowClick(row, column) {
  if (column && column.type === 'selection') return
  openDrawer(row)
}

async function openDrawer(row) {
  const seq = ++drawerSeq
  drawerVisible.value = true
  drawerLoading.value = true
  detail.value = null
  try {
    const data = await getOrderDetail(row.id)
    if (seq === drawerSeq) {
      detail.value = data
    }
  } catch (e) {
    if (seq === drawerSeq) {
      detail.value = null
    }
  } finally {
    if (seq === drawerSeq) {
      drawerLoading.value = false
    }
  }
}

async function quickVerify(row) {
  const confirmed = await ElMessageBox.confirm(
    `确认将求助 #${row.id} 的用时风险标记为「正常」？本操作只记录核实结果，扣分请到用户详情调整微光值。`,
    '风险核实',
    { type: 'warning', confirmButtonText: '标记正常', cancelButtonText: '再想想' }
  ).then(() => true).catch(() => false)
  if (!confirmed) return
  try {
    await riskReview(row.id, { result: 'NORMAL', note: '列表快捷核实' })
    ElMessage.success('已标记为正常')
    load(query.page)
  } catch (e) {
    // 请求层已提示
  }
}

async function batchVerify() {
  if (!canBatchVerify.value) return
  const confirmed = await ElMessageBox.confirm(
    `确认将选中的 ${selected.value.length} 单风险标记为「正常」？只记录核实结果，不会扣分。`,
    '批量核实',
    { type: 'warning', confirmButtonText: '确认核实', cancelButtonText: '取消' }
  ).then(() => true).catch(() => false)
  if (!confirmed) return

  batchLoading.value = true
  let success = 0
  let failed = 0
  for (const row of selected.value) {
    try {
      await riskReview(row.id, { result: 'NORMAL', note: '批量核实' })
      success += 1
    } catch (e) {
      failed += 1
    }
  }
  batchLoading.value = false
  if (success) ElMessage.success(`已核实 ${success} 单`)
  if (failed) ElMessage.warning(`${failed} 单核实失败，请稍后重试`)
  clearSelection()
  load(query.page)
}

function exportRows(rows, label) {
  if (!rows.length) return
  const header = ['ID', '标题', '类型', '状态', '发布者', '接单者', '用时(分)', '保底(分)', '风险', '发布时间']
  const body = rows.map((row) => [
    row.id,
    row.title,
    ORDER_TYPE_LABELS[row.orderType] || row.orderType,
    ORDER_STATUS_LABELS[row.status] || row.status,
    row.requesterNickname || '',
    row.helperNickname || '',
    row.durationMinutes ?? '',
    row.minMinutes ?? '',
    riskLabel(row) || '—',
    formatShort(row.createdAt)
  ])
  const csv = '\ufeff' + [header, ...body]
    .map((line) => line.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(','))
    .join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `微光求助_${label}_${new Date().toISOString().slice(0, 10)}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

watch(
  () => route.query.keyword,
  (value) => {
    if (value !== undefined && value !== query.keyword) {
      query.keyword = String(value)
      load(1)
    }
  }
)

onMounted(() => {
  if (route.query.keyword) {
    query.keyword = String(route.query.keyword)
  }
  load(1)
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.summary {
  margin: 0;
  font-size: 12.5px;
  color: var(--wg-muted);
}

.summary b {
  color: var(--wg-text);
  font-weight: 600;
}

.filter-hint {
  color: var(--wg-clay);
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.ghost-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 13px;
  border: 1px solid #e2d8ca;
  border-radius: 8px;
  background: var(--wg-panel);
  color: #6b6259;
  font-size: 12.5px;
  cursor: pointer;
}

.ghost-btn:hover {
  background: #f6f0e6;
}

.ghost-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.filter-card {
  padding: 14px 16px;
  margin-bottom: 12px;
  border: 1px solid var(--wg-line);
  border-radius: 12px;
  background: var(--wg-panel);
}

.chips {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding-bottom: 3px;
  scrollbar-width: none;
}

.chips::-webkit-scrollbar {
  display: none;
}

.chip {
  flex-shrink: 0;
  height: 30px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: #f8f4ed;
  color: var(--wg-muted);
  font-size: 12px;
  cursor: pointer;
}

.chip:hover {
  color: var(--wg-text-soft);
}

.chip.active {
  background: #fbf0dc;
  border-color: #ecd3ab;
  color: #9a6a22;
  font-weight: 500;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}

.search-input {
  width: 280px;
}

.type-select {
  width: 140px;
}

.risk-select {
  width: 140px;
}

.text-btn {
  border: none;
  background: none;
  color: var(--wg-muted);
  font-size: 12px;
  cursor: pointer;
  padding: 0 4px;
}

.text-btn:hover {
  color: var(--wg-clay);
}

.table-card {
  padding: 0 0 12px;
  border: 1px solid var(--wg-line);
  border-radius: 12px;
  background: var(--wg-panel);
  overflow: hidden;
}

.order-id {
  color: var(--wg-muted);
  font-variant-numeric: tabular-nums;
}

.title-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--wg-text);
}

.urgent {
  flex-shrink: 0;
  height: 19px;
  padding: 0 6px;
  border-radius: 5px;
  background: #f2e7e4;
  color: #a0524a;
  font-size: 10.5px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
}

.people-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  line-height: 1.4;
}

.people-cell .muted {
  font-size: 11px;
}

.duration-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.duration-cell span {
  font-size: 11.5px;
  color: var(--wg-text-soft);
  font-variant-numeric: tabular-nums;
}

.duration-track {
  width: 76px;
  height: 4px;
  border-radius: 2px;
  background: #f0e9de;
  overflow: hidden;
}

.duration-track i {
  display: block;
  height: 100%;
  border-radius: 2px;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
}

.wg-link.danger {
  color: var(--wg-danger);
}

.pager {
  margin: 12px 16px 0;
  justify-content: flex-end;
}

.batch-bar {
  position: sticky;
  bottom: 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  width: fit-content;
  margin: 14px auto 0;
  padding: 9px 12px 9px 16px;
  border: 1px solid #d9c9b6;
  border-radius: 10px;
  background: #fffaf1;
  box-shadow: 0 8px 24px rgba(46, 42, 38, 0.12);
  font-size: 12.5px;
}

.batch-bar b {
  color: var(--wg-clay);
}

.batch-btn {
  height: 30px;
  padding: 0 12px;
  border: none;
  border-radius: 7px;
  background: var(--wg-clay);
  color: #fff6ec;
  font-size: 12px;
  cursor: pointer;
}

.batch-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.batch-btn.plain {
  background: transparent;
  border: 1px solid #e2d8ca;
  color: #6b6259;
}

.drawer-body {
  min-height: 240px;
}

.drawer-head {
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

.drawer-head h3 {
  margin: 4px 0 0;
  font-size: 17px;
  font-weight: 600;
  line-height: 1.35;
}

.drawer-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
  font-size: 11.5px;
  color: var(--wg-muted);
}

.risk-banner {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin-top: 14px;
  padding: 11px 12px;
  border: 1px solid #ecd3ab;
  border-radius: 9px;
  background: #fbf0dc;
  color: #8a5f1e;
  font-size: 11.5px;
}

.risk-banner p {
  margin: 0 0 3px;
  font-weight: 500;
}

.risk-banner span {
  color: #a9772f;
}

.drawer-section {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid #f0e9de;
}

.drawer-section h4 {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
}

.kv {
  display: flex;
  gap: 12px;
  padding: 5px 0;
  font-size: 12px;
  line-height: 1.6;
}

.kv span {
  width: 72px;
  flex-shrink: 0;
  color: var(--wg-muted);
}

.kv b {
  flex: 1;
  font-weight: 400;
  color: var(--wg-text-soft);
}

.party-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  margin-bottom: 8px;
  border: 1px solid #f0e9de;
  border-radius: 9px;
  background: #fdfaf4;
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

.party-card p {
  margin: 0;
  font-size: 12.5px;
  font-weight: 500;
}

.party-card span {
  font-size: 10.5px;
  color: var(--wg-muted);
}

.mini-timeline {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.mini-line {
  display: flex;
  gap: 9px;
}

.mini-line i {
  width: 6px;
  height: 6px;
  margin-top: 5px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--wg-clay);
}

.mini-line p {
  margin: 0;
  font-size: 12px;
  color: var(--wg-text-soft);
}

.mini-line span {
  font-size: 10.5px;
  color: var(--wg-muted);
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
