<template>
  <div>
    <el-card>
      <el-form inline>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="昵称或 openid" clearable style="width: 200px" @keyup.enter="load(1)" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px">
            <el-option v-for="(label, value) in USER_STATUS_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load(1)">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table :data="list" v-loading="loading" @row-click="openDetail">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="nickname" label="昵称" width="130" />
        <el-table-column prop="age" label="年龄" width="70" />
        <el-table-column label="模式" width="90">
          <template #default="{ row }">{{ row.uiMode === 'ELDER' ? '年长' : '普通' }}</template>
        </el-table-column>
        <el-table-column prop="creditScore" label="微光值" width="90" />
        <el-table-column label="光语" width="100">
          <template #default="{ row }">{{ row.lightState }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'NORMAL' ? 'success' : row.status === 'BANNED' ? 'danger' : 'info'">
              {{ USER_STATUS_LABELS[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="openid" label="openid" min-width="160" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="注册时间" width="170" />
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
        @current-change="load"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listUsers } from '../api/admin'
import { USER_STATUS_LABELS } from '../utils/labels'

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
  } finally {
    loading.value = false
  }
}

function openDetail(row) {
  router.push(`/users/${row.id}`)
}

onMounted(() => load(1))
</script>

<style scoped>
.table-card {
  margin-top: 16px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>