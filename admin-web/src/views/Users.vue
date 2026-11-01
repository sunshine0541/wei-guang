<template>
  <div class="wg-page">
    <div class="wg-toolbar">
      <p>共 <b>{{ total }}</b> 位用户<span v-if="query.status"> · 当前筛选「{{ USER_STATUS_LABELS[query.status] }}」</span></p>
      <button class="wg-ghost-btn" :disabled="loading" @click="load(query.page)">
        <AdminIcon name="refresh" :size="14" />刷新
      </button>
    </div>

    <div class="filter-card">
      <div class="wg-chips">
        <button class="wg-chip" :class="{ active: !query.status }" @click="pickStatus('')">全部</button>
        <button
          v-for="(label, value) in USER_STATUS_LABELS"
          :key="value"
          class="wg-chip"
          :class="{ active: query.status === value }"
          @click="pickStatus(value)"
        >
          {{ label }}
        </button>
      </div>
      <div class="filter-row">
        <el-input
          v-model="query.keyword"
          class="kw-input"
          placeholder="搜索昵称或 openid"
          clearable
          @keyup.enter="load(1)"
          @clear="load(1)"
        />
        <el-button type="primary" @click="load(1)">查询</el-button>
        <button class="wg-text-btn" @click="reset">重置</button>
      </div>
    </div>

    <div class="table-card">
      <el-table :data="list" v-loading="loading" row-key="id" @row-click="onRowClick">
        <el-table-column prop="id" label="ID" width="64">
          <template #default="{ row }">
            <span class="record-id">#{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="用户" width="180">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-avatar">{{ (row.nickname || '匿名').slice(0, 1) }}</div>
              <div class="user-main">
                <p>{{ row.nickname || '未设昵称' }}</p>
                <span>{{ row.age ?? '年龄未填' }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="模式" width="80">
          <template #default="{ row }">
            <span class="wg-pill no-dot" :class="row.uiMode === 'ELDER' ? 'is-amber' : 'is-gray'">
              {{ row.uiMode === 'ELDER' ? '年长' : '普通' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="creditScore" label="微光值" width="90">
          <template #default="{ row }">
            <span class="credit-value">{{ row.creditScore }}</span>
          </template>
        </el-table-column>
        <el-table-column label="光语" width="92">
          <template #default="{ row }">{{ LIGHT_STATE_LABELS[row.lightState] || row.lightState }}</template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <span class="wg-pill" :class="'is-' + userStatusTone(row.status)">
              {{ USER_STATUS_LABELS[row.status] || row.status }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="openid" label="openid" min-width="170" show-overflow-tooltip />
        <el-table-column label="注册时间" width="118">
          <template #default="{ row }">{{ formatShort(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <span class="wg-link" @click.stop="openDetail(row)">详情</span>
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AdminIcon from '../components/AdminIcon.vue'
import { listUsers } from '../api/admin'
import { LIGHT_STATE_LABELS, USER_STATUS_LABELS } from '../utils/labels'
import { formatShort } from '../utils/format'
import { userStatusTone } from '../utils/status'

const router = useRouter()
const query = reactive({ keyword: '', status: '', page: 1, pageSize: 20 })
const list = ref([])
const total = ref(0)
const loading = ref(false)

async function load(page) {
  query.page = page || query.page
  loading.value = true
  try {
    const data = await listUsers({
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      page: query.page,
      pageSize: query.pageSize
    })
    list.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    // 请求层已提示
  } finally {
    loading.value = false
  }
}

function pickStatus(status) {
  query.status = status
  load(1)
}

function reset() {
  query.keyword = ''
  query.status = ''
  load(1)
}

function onSizeChange(size) {
  query.pageSize = size
  load(1)
}

function onRowClick(row, column) {
  if (column && column.type === 'selection') return
  openDetail(row)
}

function openDetail(row) {
  router.push(`/users/${row.id}`)
}

onMounted(() => load(1))
</script>

<style scoped>
.filter-card {
  padding: 12px 16px;
  margin-bottom: 12px;
  border: 1px solid var(--wg-line);
  border-radius: 12px;
  background: var(--wg-panel);
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}

.kw-input {
  width: 280px;
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

.user-cell {
  display: flex;
  align-items: center;
  gap: 9px;
}

.user-avatar {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #e9ded0;
  color: #8a6a4d;
  font-size: 12px;
}

.user-main p {
  margin: 0;
  font-size: 12.5px;
  color: var(--wg-text);
}

.user-main span {
  font-size: 10.5px;
  color: var(--wg-muted);
}

.credit-value {
  font-variant-numeric: tabular-nums;
  font-weight: 500;
}

.pager {
  margin: 12px 16px 0;
  justify-content: flex-end;
}
</style>
