<template>
  <div class="wg-page">
    <div class="tip">
      <AdminIcon name="alert" :size="14" />
      <span>修改保存后即时生效（含运行中业务），无需重启；数值按「取值范围」校验。</span>
    </div>

    <div class="filter-card">
      <div class="wg-chips config-chips">
        <button class="wg-chip" :class="{ active: !activeGroup }" @click="activeGroup = ''">
          全部
          <span class="chip-count">{{ list.length }}</span>
        </button>
        <button
          v-for="group in groups"
          :key="group.key"
          class="wg-chip"
          :class="{ active: activeGroup === group.key }"
          @click="activeGroup = group.key"
        >
          {{ group.label }}
          <span class="chip-count">{{ group.count }}</span>
        </button>
      </div>
      <div class="filter-row">
        <el-input v-model="keyword" class="kw-input" placeholder="搜索配置项或说明" clearable />
        <span class="result-hint">当前显示 {{ filtered.length }} 项</span>
      </div>
    </div>

    <div class="table-card">
      <el-table :data="filtered" v-loading="loading">
        <el-table-column prop="key" label="配置项" width="250">
          <template #default="{ row }">
            <span class="config-key">{{ row.key }}</span>
          </template>
        </el-table-column>
        <el-table-column label="当前值" width="150">
          <template #default="{ row }">
            <span class="current-value">{{ row.value }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="defaultValue" label="默认值" width="130">
          <template #default="{ row }">
            <span class="muted">{{ row.defaultValue }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="valueRange" label="取值范围" width="170">
          <template #default="{ row }">
            <span class="muted">{{ row.valueRange }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="230" show-overflow-tooltip />
        <el-table-column label="更新时间" width="118">
          <template #default="{ row }">{{ formatShort(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <span class="wg-link" @click="openEdit(row)">编辑</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="`编辑配置 · ${current?.key || ''}`" width="520px">
      <el-form label-width="84px">
        <el-form-item label="说明">
          <span class="muted">{{ current?.description }}</span>
        </el-form-item>
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
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AdminIcon from '../components/AdminIcon.vue'
import { listConfigs, updateConfig } from '../api/admin'
import { formatShort } from '../utils/format'

const GROUP_RULES = [
  { key: 'order', label: '订单与求助', prefixes: ['order', 'urgency', 'help', 'content'] },
  { key: 'user', label: '用户与激励', prefixes: ['credit', 'points', 'light', 'elder'] },
  { key: 'ai', label: 'AI 与内容', prefixes: ['ai', 'community', 'notify'] },
  { key: 'risk', label: '风控与限流', prefixes: ['risk', 'rate', 'cache', 'redis', 'privacy', 'compliance', 'abuse'] },
  { key: 'system', label: '系统与存储', prefixes: ['storage', 'system', 'jwt'] }
]

const PREFIX_TO_GROUP = {}
GROUP_RULES.forEach((group) => {
  group.prefixes.forEach((prefix) => {
    PREFIX_TO_GROUP[prefix] = group.key
  })
})

function groupKeyOf(key) {
  const prefix = (key || '').split('.')[0]
  return PREFIX_TO_GROUP[prefix] || 'system'
}

const list = ref([])
const loading = ref(false)
const activeGroup = ref('')
const keyword = ref('')
const dialogVisible = ref(false)
const saving = ref(false)
const current = ref(null)
const newValue = ref('')

const groups = computed(() => {
  const counts = {}
  list.value.forEach((row) => {
    const key = groupKeyOf(row.key)
    counts[key] = (counts[key] || 0) + 1
  })
  return GROUP_RULES.filter((group) => counts[group.key]).map((group) => ({
    key: group.key,
    label: group.label,
    count: counts[group.key]
  }))
})

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return list.value.filter((row) => {
    if (activeGroup.value && groupKeyOf(row.key) !== activeGroup.value) {
      return false
    }
    if (!kw) return true
    return (row.key || '').toLowerCase().includes(kw) || (row.description || '').toLowerCase().includes(kw)
  })
})

async function load() {
  loading.value = true
  try {
    list.value = await listConfigs()
  } catch (e) {
    // 请求层已提示；保留旧列表
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
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 10px 14px;
  border: 1px solid #ecd3ab;
  border-radius: 9px;
  background: #fbf0dc;
  color: #8a5f1e;
  font-size: 12px;
}

.filter-card {
  padding: 12px 16px;
  margin-bottom: 12px;
  border: 1px solid var(--wg-line);
  border-radius: 12px;
  background: var(--wg-panel);
}

.config-chips {
  flex-wrap: wrap;
  overflow: visible;
  gap: 8px;
}

.chip-count {
  margin-left: 4px;
  font-size: 10px;
  opacity: 0.7;
}

.filter-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

.kw-input {
  width: 300px;
}

.result-hint {
  font-size: 11.5px;
  color: var(--wg-muted);
}

.table-card {
  padding: 0 0 12px;
  border: 1px solid var(--wg-line);
  border-radius: 12px;
  background: var(--wg-panel);
  overflow: hidden;
}

.config-key {
  font-family: Consolas, 'Courier New', monospace;
  font-size: 11.5px;
  color: #6b5a48;
  background: #f4efe6;
  border-radius: 4px;
  padding: 2px 6px;
}

.current-value {
  font-size: 12.5px;
  font-weight: 500;
  color: var(--wg-text);
  font-variant-numeric: tabular-nums;
}

.muted {
  color: var(--wg-muted);
  font-size: 11.5px;
}
</style>
