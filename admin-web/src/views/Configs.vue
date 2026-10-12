<template>
  <div>
    <el-alert
      type="info"
      :closable="false"
      title="修改保存后即时生效（含运行中业务），无需重启；数值按“取值范围”校验"
      class="tip"
    />
    <el-card class="table-card">
      <el-table :data="list" v-loading="loading">
        <el-table-column prop="key" label="配置项" width="260" />
        <el-table-column prop="value" label="当前值" width="160" />
        <el-table-column prop="defaultValue" label="默认值" width="140" />
        <el-table-column prop="valueRange" label="取值范围" width="180" />
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="`编辑配置 · ${current?.key || ''}`" width="480px">
      <el-form label-width="80px">
        <el-form-item label="当前值">
          <span class="muted">{{ current?.value }}</span>
        </el-form-item>
        <el-form-item label="取值范围">
          <span class="muted">{{ current?.valueRange }}</span>
        </el-form-item>
        <el-form-item label="新值" required>
          <el-input v-model="newValue" placeholder="输入新值，保存后即时生效" @keyup.enter="submit" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存并生效</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listConfigs, updateConfig } from '../api/admin'

const list = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const current = ref(null)
const newValue = ref('')

async function load() {
  loading.value = true
  try {
    list.value = await listConfigs()
  } finally {
    loading.value = false
  }
}

function openEdit(row) {
  current.value = row
  newValue.value = row.value
  dialogVisible.value = true
}

async function submit() {
  const value = newValue.value.trim()
  if (!value) {
    ElMessage.warning('请输入新值')
    return
  }
  saving.value = true
  try {
    await updateConfig(current.value.key, value)
    ElMessage.success('已保存并即时生效')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.tip {
  margin-bottom: 12px;
}

.muted {
  color: #999;
}
</style>