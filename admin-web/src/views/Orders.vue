<template>
  <div>
    <el-card>
      <el-form inline>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px">
            <el-option v-for="(label, value) in ORDER_STATUS_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.orderType" clearable placeholder="全部" style="width: 140px">
            <el-option v-for="(label, value) in ORDER_TYPE_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="标题关键词" clearable style="width: 180px" @keyup.enter="load(1)" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load(1)">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table :data="list" v-loading="loading" @row-click="openDetail">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ ORDER_TYPE_LABELS[row.orderType] || row.orderType }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ ORDER_STATUS_LABELS[row.status] || row.status }}</el-tag>
            <el-tag v-if="row.cancelPendingBy" type="warning" class="ml">协商挂起</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="急" width="60">
          <template #default="{ row }">
            <span v-if="row.urgentFlag === 1" class="urgent">急</span>
          </template>
        </el-table-column>
        <el-table-column prop="requesterNickname" label="发布者" width="110" />
        <el-table-column prop="helperNickname" label="接单者" width="110" />
        <el-table-column prop="createdAt" label="发布时间" width="170" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="query.pageSize"
        :current-page="query.page"
        @current-change="onPage"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listOrders } from '../api/admin'
import { ORDER_STATUS_LABELS, ORDER_TYPE_LABELS } from '../utils/labels'

const router = useRouter()
const query = reactive({ status: '', orderType: '', keyword: '', page: 1, pageSize: 20 })
const list = ref([])
const total = ref(0)
const loading = ref(false)

function statusTag(status) {
  if (status === 'COMPLETED') return 'success'
  if (status === 'CANCELLED' || status === 'EXPIRED') return 'info'
  if (status === 'APPEALING' || status === 'PENDING_CONFIRM') return 'warning'
  return 'primary'
}

async function load(page) {
  query.page = page || query.page
  loading.value = true
  try {
    const data = await listOrders({
      status: query.status || undefined,
      orderType: query.orderType || undefined,
      keyword: query.keyword || undefined,
      page: query.page,
      pageSize: query.pageSize
    })
    list.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function onPage(page) {
  load(page)
}

function openDetail(row) {
  router.push(`/orders/${row.id}`)
}

onMounted(() => load(1))
</script>

<style scoped>
.table-card {
  margin-top: 16px;
}

.ml {
  margin-left: 6px;
}

.urgent {
  color: #d9534f;
  font-weight: 600;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>