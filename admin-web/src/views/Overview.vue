<template>
  <div class="wg-page">
    <div class="page-head">
      <div>
        <p class="greet">
          下午好，{{ auth.username || '管理员' }}。今天有
          <b>{{ todo.counts.reports }}</b> 条举报、<b>{{ todo.counts.appeals }}</b> 条申诉待处理。
        </p>
        <p class="date">{{ todayText }} · 微光社区互助</p>
      </div>
      <div class="head-tools">
        <div class="range">
          <button class="range-btn active">近 7 天</button>
          <button class="range-btn" disabled title="当前接口只提供近 7 天数据">近 30 天</button>
        </div>
      </div>
    </div>

    <div class="kpi-grid">
      <div v-for="item in kpis" :key="item.key" class="kpi-card" :class="'tone-' + item.tone">
        <div class="kpi-top">
          <span class="kpi-label">{{ item.label }}</span>
          <span class="kpi-icon"><AdminIcon :name="item.icon" :size="15" /></span>
        </div>
        <p class="kpi-value">{{ valueOf(item.key) }}<span>{{ item.unit }}</span></p>
        <p class="kpi-note">{{ item.note }}</p>
      </div>
    </div>

    <div class="chart-grid">
      <section class="wg-panel chart-card">
        <div class="card-head">
          <div>
            <h2>求助与完成趋势</h2>
            <p>按天统计新增求助量与已完成互助量</p>
          </div>
          <div class="head-right">
            <div class="legend">
              <span><i class="dot ask"></i>新增求助</span>
              <span><i class="dot done"></i>完成互助</span>
            </div>
            <span class="unit">单位：单</span>
          </div>
        </div>

        <div v-if="trend.length" class="line-wrap">
          <svg class="line-chart" viewBox="0 0 720 240" preserveAspectRatio="none">
            <defs>
              <linearGradient id="askArea" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="rgba(181,102,60,0.22)" />
                <stop offset="100%" stop-color="rgba(181,102,60,0)" />
              </linearGradient>
            </defs>
            <line
              v-for="line in gridLines"
              :key="'g-' + line.y"
              :x1="chart.l"
              :x2="chart.l + chart.w"
              :y1="line.y"
              :y2="line.y"
              stroke="#f0e9de"
              stroke-width="1"
            />
            <text
              v-for="line in gridLines"
              :key="'t-' + line.y"
              :x="chart.l - 8"
              :y="line.y + 4"
              text-anchor="end"
              fill="#9a9084"
              font-size="10.5"
            >
              {{ line.value }}
            </text>
            <path :d="askArea" fill="url(#askArea)" />
            <path :d="askLine" fill="none" stroke="#b5663c" stroke-width="2.4" stroke-linecap="round" />
            <path :d="doneLine" fill="none" stroke="#6e8b6b" stroke-width="2" stroke-linecap="round" />
            <g v-for="(point, index) in askPoints" :key="'ap-' + index">
              <circle :cx="point.x" :cy="point.y" r="3.2" fill="#b5663c" stroke="#fffcf7" stroke-width="1.5">
                <title>{{ trend[index].date }} · 新增求助 {{ trend[index].orderCount }}</title>
              </circle>
            </g>
            <g v-for="(point, index) in donePoints" :key="'dp-' + index">
              <circle :cx="point.x" :cy="point.y" r="3" fill="#6e8b6b" stroke="#fffcf7" stroke-width="1.5">
                <title>{{ trend[index].date }} · 完成互助 {{ trend[index].completedCount }}</title>
              </circle>
            </g>
            <text
              v-for="label in xLabels"
              :key="'x-' + label.x"
              :x="label.x"
              :y="chart.t + chart.h + 20"
              text-anchor="middle"
              fill="#9a9084"
              font-size="10.5"
            >
              {{ label.text }}
            </text>
          </svg>
        </div>
        <p v-else class="empty-tip">暂无趋势数据</p>
      </section>

      <section class="wg-panel type-card">
        <div class="card-head">
          <div>
            <h2>求助类型分布</h2>
            <p>共 {{ typeTotal }} 单求助</p>
          </div>
        </div>
        <div class="donut-wrap">
          <svg class="donut" viewBox="0 0 120 120">
            <circle cx="60" cy="60" r="52" fill="none" stroke="#f0e9de" stroke-width="10" />
            <circle
              v-for="seg in typeSegments"
              :key="seg.key"
              cx="60"
              cy="60"
              r="52"
              fill="none"
              :stroke="seg.color"
              stroke-width="10"
              :stroke-dasharray="seg.dash"
              :stroke-dashoffset="seg.offset"
              transform="rotate(-90 60 60)"
            />
          </svg>
          <div class="donut-center">
            <b>{{ typeTotal }}</b>
            <span>求助总量</span>
          </div>
        </div>
        <div class="type-legend">
          <div v-for="row in typeRows" :key="row.key" class="type-row">
            <i class="type-dot" :style="{ background: row.color }"></i>
            <span class="type-name">{{ row.label }}</span>
            <span class="type-count">{{ row.value }} 单</span>
            <b>{{ row.pct }}%</b>
          </div>
        </div>
      </section>
    </div>

    <div class="bottom-grid">
      <section class="wg-panel todo-card">
        <div class="card-head">
          <div class="todo-title">
            <h2>今日待办</h2>
            <span class="todo-count">{{ todo.total }} 项</span>
          </div>
          <button class="wg-text-btn" @click="todo.load(true)">刷新队列</button>
        </div>
        <div v-if="mixedTodos.length" class="todo-list">
          <div v-for="row in mixedTodos" :key="row.key" class="todo-row">
            <span class="todo-icon" :class="'tone-' + row.tone">
              <AdminIcon :name="row.icon" :size="15" />
            </span>
            <div class="todo-main">
              <p class="todo-text">{{ row.title }}</p>
              <p class="todo-meta">{{ row.meta }}</p>
            </div>
            <span v-if="row.wait" class="todo-wait" :class="{ overdue: row.wait.overdue }">
              {{ row.wait.overdue ? '超时 ' : '' }}{{ row.wait.text }}
            </span>
            <button class="todo-btn" @click="router.push(row.route)">{{ row.action }}</button>
          </div>
        </div>
        <div v-else class="empty-state">
          <AdminIcon name="check" :size="18" />
          <p>暂时没有待办，去看看新的求助吧</p>
          <button class="wg-text-btn" @click="router.push('/orders')">查看求助 →</button>
        </div>
      </section>

      <section class="wg-panel live-card">
        <div class="card-head">
          <h2>实时动态</h2>
          <span class="live-flag"><i></i>实时</span>
        </div>
        <div v-if="recent.length" class="live-list">
          <div v-for="row in recent" :key="row.id" class="live-row" @click="router.push('/orders/' + row.id)">
            <i class="live-dot" :class="'tone-' + orderStatusTone(row.status)"></i>
            <div class="live-main">
              <p>{{ row.title }}</p>
              <span>
                {{ ORDER_STATUS_LABELS[row.status] || row.status }} · #{{ row.id }} ·
                {{ formatShort(row.createdAt) }}
              </span>
            </div>
          </div>
        </div>
        <p v-else class="empty-tip">暂无动态</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AdminIcon from '../components/AdminIcon.vue'
import { getOverview, listOrders } from '../api/admin'
import { useAuthStore } from '../store/auth'
import { useTodoStore } from '../store/todo'
import { APPEAL_TYPE_LABELS, ORDER_STATUS_LABELS } from '../utils/labels'
import { formatShort, waitingSince } from '../utils/format'
import { orderStatusTone } from '../utils/status'

const router = useRouter()
const auth = useAuthStore()
const todo = useTodoStore()

const data = ref({})
const recent = ref([])

const todayText = computed(() => {
  const now = new Date()
  const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][now.getDay()]
  return `${now.getFullYear()} 年 ${now.getMonth() + 1} 月 ${now.getDate()} 日 ${week}`
})

const kpis = [
  { key: 'reports', label: '待处置举报', unit: '条', note: '防诈与内容举报', icon: 'shield', tone: 'clay' },
  { key: 'appeals', label: '待处理申诉/反馈', unit: '条', note: '含确认被拒、事后申诉与反馈', icon: 'scale', tone: 'amber' },
  { key: 'posts', label: '待人工审核动态', unit: '条', note: 'AI 转人工队列', icon: 'message', tone: 'green' },
  { key: 'risk', label: '存疑订单', unit: '单', note: '用时低于保底，待核实', icon: 'alert', tone: 'red' }
]

function valueOf(key) {
  return todo.counts[key] || 0
}

/* ---------- 趋势折线图 ---------- */

const chart = { l: 44, t: 16, w: 720 - 44 - 18, h: 240 - 16 - 30 }

const trend = computed(() => data.value.last7Days || [])

const yMax = computed(() => {
  let max = 0
  trend.value.forEach((item) => {
    max = Math.max(max, item.orderCount || 0, item.completedCount || 0)
  })
  return Math.max(4, Math.ceil(max / 4) * 4)
})

function pointOf(value, index, total) {
  const ratio = total <= 1 ? 0 : index / (total - 1)
  const v = Number(value) || 0
  return {
    x: chart.l + ratio * chart.w,
    y: chart.t + chart.h - (v / yMax.value) * chart.h
  }
}

const askPoints = computed(() => trend.value.map((item, index) => pointOf(item.orderCount, index, trend.value.length)))
const donePoints = computed(() => trend.value.map((item, index) => pointOf(item.completedCount, index, trend.value.length)))

function smoothPath(points) {
  if (!points.length) return ''
  if (points.length === 1) return `M ${points[0].x} ${points[0].y}`
  let path = `M ${points[0].x} ${points[0].y}`
  for (let i = 1; i < points.length; i += 1) {
    const prev = points[i - 1]
    const curr = points[i]
    const mid = (prev.x + curr.x) / 2
    path += ` C ${mid} ${prev.y}, ${mid} ${curr.y}, ${curr.x} ${curr.y}`
  }
  return path
}

const askLine = computed(() => smoothPath(askPoints.value))
const doneLine = computed(() => smoothPath(donePoints.value))

const askArea = computed(() => {
  if (!askPoints.value.length) return ''
  const baseline = chart.t + chart.h
  const first = askPoints.value[0]
  const last = askPoints.value[askPoints.value.length - 1]
  return `${askLine.value} L ${last.x} ${baseline} L ${first.x} ${baseline} Z`
})

const gridLines = computed(() =>
  [0, 1, 2, 3, 4].map((index) => ({
    y: chart.t + (index / 4) * chart.h,
    value: Math.round(yMax.value * ((4 - index) / 4))
  }))
)

const xLabels = computed(() =>
  trend.value.map((item, index) => ({
    x: pointOf(0, index, trend.value.length).x,
    text: (item.date || '').slice(5)
  }))
)

/* ---------- 类型环形图 ---------- */

const typeItems = computed(() => {
  const dist = data.value.typeDistribution || {}
  return [
    { key: 'HOME_VISIT', label: '上门', color: '#b5663c', value: dist.HOME_VISIT || 0 },
    { key: 'ACCOMPANY', label: '陪同外出', color: '#d69a45', value: dist.ACCOMPANY || 0 },
    { key: 'REMOTE', label: '远程', color: '#6e8b6b', value: dist.REMOTE || 0 }
  ]
})

const typeTotal = computed(() => typeItems.value.reduce((sum, item) => sum + item.value, 0))

const typeRows = computed(() =>
  typeItems.value.map((item) => ({
    ...item,
    pct: typeTotal.value > 0 ? Math.round((item.value / typeTotal.value) * 100) : 0
  }))
)

const DONUT_CIRCUMFERENCE = 2 * Math.PI * 52

const typeSegments = computed(() => {
  let acc = 0
  return typeItems.value.map((item) => {
    const ratio = typeTotal.value > 0 ? item.value / typeTotal.value : 0
    const seg = {
      ...item,
      dash: `${ratio * DONUT_CIRCUMFERENCE} ${DONUT_CIRCUMFERENCE - ratio * DONUT_CIRCUMFERENCE}`,
      offset: -acc * DONUT_CIRCUMFERENCE
    }
    acc += ratio
    return seg
  })
})

/* ---------- 今日待办 / 实时动态 ---------- */

const mixedTodos = computed(() => {
  const rows = []
  const reports = todo.queues.reports || []
  reports.slice(0, 2).forEach((row) => {
    const isFraud = row.type === 'FRAUD_RISK' || (!row.targetType && row.messageId)
    const target = row.targetTitle ? `「${row.targetTitle}」` : `#${row.targetId || row.orderId || row.id}`
    rows.push({
      key: 'report-' + row.id,
      icon: 'shield',
      tone: 'red',
      title: isFraud
        ? `防诈上报：聊天消息${row.orderId ? '（订单 #' + row.orderId + '）' : ''}`
        : `举报：${row.targetType === 'ORDER' ? '订单' : row.targetType === 'COMMENT' ? '评论' : '动态'}${target}`,
      meta: row.evidence || '待核实举报理由',
      wait: waitingSince(row.createdAt),
      action: '去处置',
      route: { path: '/reports', query: { reportId: row.id } },
      createdAt: row.createdAt || ''
    })
  })
  const appeal = (todo.queues.appeals || [])[0]
  if (appeal) {
    rows.push({
      key: 'appeal-' + appeal.id,
      icon: 'scale',
      tone: 'amber',
      title: `${APPEAL_TYPE_LABELS[appeal.type] || '申诉'}：${appeal.orderTitle || '未关联求助'}`,
      meta: appeal.reason || '未填写理由',
      wait: waitingSince(appeal.createdAt),
      action: '去仲裁',
      route: { path: '/appeals', query: { appealId: appeal.id } },
      createdAt: appeal.createdAt || ''
    })
  }
  const post = (todo.queues.posts || [])[0]
  if (post) {
    rows.push({
      key: 'post-' + post.id,
      icon: 'message',
      tone: 'green',
      title: `待审核动态：${post.title || (post.content || '').slice(0, 16)}`,
      meta: `${post.authorNickname || '匿名邻居'} · ${post.content || ''}`,
      wait: waitingSince(post.createdAt),
      action: '去审核',
      route: { path: '/community', query: { postId: post.id } },
      createdAt: post.createdAt || ''
    })
  }
  const risk = (todo.queues.risk || [])[0]
  if (risk) {
    rows.push({
      key: 'risk-' + risk.id,
      icon: 'alert',
      tone: 'red',
      title: `风险订单：${risk.title}`,
      meta: `用时 ${risk.durationMinutes ?? '—'} 分 / 保底 ${risk.minMinutes ?? '—'} 分`,
      wait: waitingSince(risk.createdAt),
      action: '去核实',
      route: { path: '/orders/' + risk.id },
      createdAt: risk.createdAt || ''
    })
  }
  return rows.sort((a, b) => String(a.createdAt).localeCompare(String(b.createdAt))).slice(0, 4)
})

async function loadRecent() {
  try {
    const result = await listOrders({ page: 1, pageSize: 5 })
    recent.value = result.records || []
  } catch (e) {
    recent.value = []
  }
}

onMounted(async () => {
  // 工作台每次进入都强制刷新待办，避免处置后 30 秒内返回仍看到旧队列
  const [overviewResult] = await Promise.allSettled([getOverview(), todo.load(true), loadRecent()])
  if (overviewResult.status === 'fulfilled') {
    data.value = overviewResult.value || {}
  }
})
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 16px;
}

.greet {
  margin: 0;
  font-size: 13px;
  color: var(--wg-text-soft);
}

.greet b {
  color: var(--wg-clay);
  font-weight: 600;
}

.date {
  margin: 5px 0 0;
  font-size: 11.5px;
  color: var(--wg-muted);
}

.head-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.range {
  display: flex;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--wg-line);
  border-radius: 9px;
  background: #f9f5ee;
}

.range-btn {
  height: 28px;
  padding: 0 12px;
  border: none;
  border-radius: 6px;
  background: none;
  color: var(--wg-muted);
  font-size: 12px;
  cursor: pointer;
}

.range-btn.active {
  background: var(--wg-panel);
  color: var(--wg-text);
  font-weight: 500;
  box-shadow: 0 1px 2px rgba(46, 42, 38, 0.06);
}

.range-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

.kpi-card {
  padding: 16px 18px;
  border: 1px solid var(--wg-line);
  border-radius: 12px;
  background: var(--wg-panel);
  box-shadow: 0 1px 2px rgba(46, 42, 38, 0.04);
}

.kpi-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.kpi-label {
  font-size: 12.5px;
  color: var(--wg-muted);
}

.kpi-icon {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 8px;
}

.kpi-card.tone-clay .kpi-icon {
  background: #f6e8dd;
  color: #b5663c;
}

.kpi-card.tone-amber .kpi-icon {
  background: #f8efdc;
  color: #a9772f;
}

.kpi-card.tone-green .kpi-icon {
  background: #e8f0e6;
  color: #5a7a57;
}

.kpi-card.tone-red .kpi-icon {
  background: #f2e7e4;
  color: #a0524a;
}

.kpi-value {
  margin: 12px 0 0;
  font-size: 30px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.kpi-value span {
  margin-left: 4px;
  font-size: 12px;
  font-weight: 400;
  color: var(--wg-muted);
}

.kpi-note {
  margin: 10px 0 0;
  font-size: 11.5px;
  color: var(--wg-muted);
}

.chart-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.85fr) minmax(0, 1fr);
  gap: 14px;
  margin-top: 14px;
  align-items: stretch;
}

.chart-card,
.type-card {
  padding-bottom: 10px;
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 15px 18px 6px;
}

.card-head h2 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.card-head p {
  margin: 4px 0 0;
  font-size: 11.5px;
  color: var(--wg-muted);
}

.head-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.legend {
  display: flex;
  gap: 12px;
  font-size: 10.5px;
  color: var(--wg-muted);
}

.legend span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
}

.dot.ask {
  background: #b5663c;
}

.dot.done {
  background: #6e8b6b;
}

.unit {
  padding: 3px 8px;
  border-radius: 5px;
  background: #f4efe6;
  color: var(--wg-muted);
  font-size: 10px;
}

.line-wrap {
  padding: 2px 10px 6px;
}

.line-chart {
  width: 100%;
  height: 240px;
}

.empty-tip {
  padding: 40px 18px;
  text-align: center;
  font-size: 12px;
  color: var(--wg-muted);
}

.donut-wrap {
  position: relative;
  width: 168px;
  height: 168px;
  margin: 4px auto 2px;
}

.donut {
  width: 100%;
  height: 100%;
}

.donut-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.donut-center b {
  font-size: 24px;
  font-weight: 600;
  line-height: 1;
  color: var(--wg-text);
  font-variant-numeric: tabular-nums;
}

.donut-center span {
  margin-top: 4px;
  font-size: 10.5px;
  color: var(--wg-muted);
}

.type-legend {
  padding: 0 18px 6px;
}

.type-row {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 4px 0;
  font-size: 11.5px;
  color: var(--wg-text-soft);
}

.type-dot {
  width: 7px;
  height: 7px;
  border-radius: 2px;
  flex-shrink: 0;
}

.type-name {
  flex: 1;
}

.type-count {
  color: var(--wg-muted);
  font-size: 10.5px;
}

.type-row b {
  width: 34px;
  text-align: right;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.bottom-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.85fr) minmax(0, 1fr);
  gap: 14px;
  margin-top: 14px;
  align-items: stretch;
}

.todo-card,
.live-card {
  padding-bottom: 8px;
}

.todo-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.todo-count {
  padding: 1px 7px;
  border-radius: 999px;
  background: #f6e8dd;
  color: #a2542c;
  font-size: 10.5px;
  font-weight: 500;
}

.todo-list {
  padding: 2px 0 0;
}

.todo-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 18px;
  border-top: 1px solid #f5efe5;
}

.todo-row:hover {
  background: #fbf7f0;
}

.todo-icon {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 8px;
}

.todo-icon.tone-red {
  background: #f2e7e4;
  color: #a0524a;
}

.todo-icon.tone-amber {
  background: #fbf0dc;
  color: #9a6a22;
}

.todo-icon.tone-green {
  background: #e8f0e6;
  color: #4f6b4c;
}

.todo-main {
  flex: 1;
  min-width: 0;
}

.todo-text {
  margin: 0;
  font-size: 12.5px;
  color: var(--wg-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-meta {
  margin: 3px 0 0;
  font-size: 11px;
  color: var(--wg-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-wait {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--wg-muted);
}

.todo-wait.overdue {
  color: var(--wg-danger);
  font-weight: 500;
}

.todo-btn {
  flex-shrink: 0;
  height: 28px;
  padding: 0 11px;
  border: 1px solid #e2d8ca;
  border-radius: 7px;
  background: var(--wg-panel);
  color: var(--wg-text-soft);
  font-size: 11.5px;
  cursor: pointer;
}

.todo-btn:hover {
  border-color: var(--wg-clay);
  color: var(--wg-clay);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 30px 18px;
  color: var(--wg-muted);
}

.empty-state p {
  margin: 0;
  font-size: 12px;
}

.live-flag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 10.5px;
  color: #6e8b6b;
}

.live-flag i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #7fb07a;
}

.live-list {
  padding: 2px 18px 6px;
}

.live-row {
  display: flex;
  gap: 9px;
  padding: 8px 0;
  border-bottom: 1px dashed #f2ece2;
  cursor: pointer;
}

.live-row:last-child {
  border-bottom: none;
}

.live-row:hover p {
  color: var(--wg-clay);
}

.live-dot {
  width: 7px;
  height: 7px;
  margin-top: 6px;
  flex-shrink: 0;
  border-radius: 50%;
}

.live-dot.tone-amber {
  background: var(--wg-amber);
}

.live-dot.tone-green {
  background: var(--wg-success);
}

.live-dot.tone-red {
  background: var(--wg-danger);
}

.live-dot.tone-blue {
  background: var(--wg-info);
}

.live-dot.tone-gray {
  background: #b7aa98;
}

.live-main {
  min-width: 0;
}

.live-main p {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--wg-text-soft);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.live-main span {
  font-size: 10px;
  color: var(--wg-muted-light);
}
</style>
