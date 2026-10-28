<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card><div class="num">{{ data.userCount ?? '-' }}</div><div class="name">用户数</div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card><div class="num">{{ data.orderTotal ?? '-' }}</div><div class="name">求助总数</div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card><div class="num">{{ data.completedCount ?? '-' }}</div><div class="name">已完成数</div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="name">类型分布</div>
          <div class="dist">
            <span>上门 {{ dist.HOME_VISIT ?? 0 }}</span>
            <span>陪同 {{ dist.ACCOMPANY ?? 0 }}</span>
            <span>远程 {{ dist.REMOTE ?? 0 }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row2">
      <el-col :span="14">
        <el-card>
          <template #header>近 7 天趋势</template>
          <div class="chart">
            <div v-for="item in trend" :key="item.date" class="chart-col">
              <div class="bars">
                <div class="bar bar-order" :style="{ height: barHeight(item.orderCount) }" :title="'求助 ' + item.orderCount"></div>
                <div class="bar bar-complete" :style="{ height: barHeight(item.completedCount) }" :title="'完成 ' + item.completedCount"></div>
                <div class="bar bar-user" :style="{ height: barHeight(item.userCount) }" :title="'新增用户 ' + item.userCount"></div>
              </div>
              <div class="chart-date">{{ item.date.slice(5) }}</div>
            </div>
          </div>
          <div class="legend">
            <span><i class="dot dot-order"></i>求助</span>
            <span><i class="dot dot-complete"></i>完成</span>
            <span><i class="dot dot-user"></i>新增用户</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card>
          <template #header>状态分布</template>
          <div v-for="(count, status) in statusDist" :key="status" class="status-row">
            <span class="status-name">{{ statusLabel(status) }}</span>
            <div class="status-track"><div class="status-fill" :style="{ width: statusWidth(count) }"></div></div>
            <span class="status-count">{{ count }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row2">
      <el-col :span="12">
        <el-card>
          <template #header>安全与社区</template>
          <div class="kv"><span>防诈高危上报（AI 命中）</span><b>{{ data.fraudReportCount ?? 0 }}</b></div>
          <div class="kv"><span>社区待人工审核动态</span><b>{{ data.pendingManualPostCount ?? 0 }}</b></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>说明</template>
          <div class="note">「已完成数」= 双确认完成结算的求助；趋势按自然日统计，含 0 值日期。</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getOverview } from '../api/admin'

const data = ref({})
const dist = computed(() => data.value.typeDistribution || {})
const trend = computed(() => data.value.last7Days || [])
const statusDist = computed(() => data.value.statusDistribution || {})

const STATUS_LABELS = {
  PENDING: '待响应',
  ACCEPTED: '已接单',
  IN_SERVICE: '服务中',
  PENDING_CONFIRM: '待确认',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  EXPIRED: '已超时',
  APPEALING: '申诉中'
}

const maxValue = computed(() => {
  let max = 1
  trend.value.forEach((item) => {
    max = Math.max(max, item.orderCount || 0, item.completedCount || 0, item.userCount || 0)
  })
  return max
})

function barHeight(value) {
  const v = value || 0
  return Math.max(2, Math.round((v / maxValue.value) * 96)) + 'px'
}

function statusWidth(count) {
  let total = 0
  Object.values(statusDist.value).forEach((v) => { total += v || 0 })
  if (!total) return '0%'
  return Math.round(((count || 0) / total) * 100) + '%'
}

function statusLabel(status) {
  return STATUS_LABELS[status] || status
}

onMounted(async () => {
  data.value = await getOverview()
})
</script>

<style scoped>
.num {
  font-size: 28px;
  font-weight: 600;
  color: #b06a00;
}

.name {
  color: #666;
  margin-top: 6px;
}

.dist {
  display: flex;
  gap: 12px;
  margin-top: 10px;
  color: #b06a00;
  font-weight: 600;
}

.row2 {
  margin-top: 16px;
}

.chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 130px;
  padding: 0 6px;
}

.chart-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.bars {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 100px;
}

.bar {
  width: 8px;
  border-radius: 3px 3px 0 0;
  background-color: #b9c9b4;
}

.bar-order { background-color: #8fa98a; }
.bar-complete { background-color: #b06a00; }
.bar-user { background-color: #7f9aa8; }

.chart-date {
  margin-top: 6px;
  color: #999;
  font-size: 12px;
}

.legend {
  display: flex;
  gap: 18px;
  margin-top: 10px;
  color: #666;
  font-size: 13px;
}

.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 4px;
  border-radius: 4px;
}

.dot-order { background-color: #8fa98a; }
.dot-complete { background-color: #b06a00; }
.dot-user { background-color: #7f9aa8; }

.status-row {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.status-name {
  width: 70px;
  color: #666;
  font-size: 13px;
}

.status-track {
  flex: 1;
  height: 8px;
  margin: 0 10px;
  border-radius: 4px;
  background-color: #f0f2f5;
  overflow: hidden;
}

.status-fill {
  height: 100%;
  border-radius: 4px;
  background-color: #b06a00;
}

.status-count {
  width: 40px;
  text-align: right;
  color: #333;
}

.kv {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  color: #666;
  border-bottom: 1px dashed #eee;
}

.kv b {
  color: #b06a00;
  font-size: 18px;
}

.note {
  color: #666;
  line-height: 1.7;
}
</style>
