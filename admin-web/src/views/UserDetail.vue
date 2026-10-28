<template>
  <div v-if="user">
    <el-card>
      <template #header>
        <div class="head">
          <div class="left">
            <el-button link type="primary" @click="goBack">← 返回用户管理</el-button>
            <span>用户 #{{ user.id }} · {{ user.nickname || '（未设昵称）' }}</span>
          </div>
          <div>
            <el-tag :type="user.status === 'NORMAL' ? 'success' : user.status === 'BANNED' ? 'danger' : 'info'">
              {{ USER_STATUS_LABELS[user.status] || user.status }}
            </el-tag>
            <el-button
              v-if="user.status === 'NORMAL'"
              type="danger"
              size="small"
              class="ml"
              @click="doBan(true)"
            >
              封禁
            </el-button>
            <el-button v-if="user.status === 'BANNED'" type="success" size="small" class="ml" @click="doBan(false)">
              解封
            </el-button>
            <el-button type="warning" size="small" class="ml" @click="openAdjust">微光值调整</el-button>
          </div>
        </div>
      </template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="微光值">{{ user.creditScore }}</el-descriptions-item>
        <el-descriptions-item label="违约计数">{{ user.violationCount }}</el-descriptions-item>
        <el-descriptions-item label="模式">{{ user.uiMode === 'ELDER' ? '年长' : '普通' }}</el-descriptions-item>
        <el-descriptions-item label="光语状态">{{ user.lightState }}</el-descriptions-item>
        <el-descriptions-item label="光已传递">{{ user.lightPassed === 1 ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="曾被帮助">{{ user.helpedBefore === 1 ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="发布求助数">{{ user.publishedCount }}</el-descriptions-item>
        <el-descriptions-item label="发布且完成数">{{ user.completedCount }}</el-descriptions-item>
        <el-descriptions-item label="作为邻居完成数">{{ user.helpedCompletedCount }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card class="mt">
      <template #header>画像</template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="年龄">{{ user.age ?? '未填' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ user.phone || '—' }}</el-descriptions-item>
        <el-descriptions-item label="签名">{{ user.signature || '—' }}</el-descriptions-item>
        <el-descriptions-item label="喜欢做的事" :span="3">{{ (user.hobbies || []).join('、') || '—' }}</el-descriptions-item>
        <el-descriptions-item label="需要留意" :span="3">{{ user.careTip || '—' }}</el-descriptions-item>
        <el-descriptions-item label="紧急联系人">
          {{ user.emergencyContactName || '—' }} {{ user.emergencyContactPhone || '' }}
        </el-descriptions-item>
        <el-descriptions-item label="openid" :span="2">{{ user.openid }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card class="mt">
      <template #header>微光值明细（最近 10 条，余额 {{ user.credit?.balance }}）</template>
      <el-table :data="user.credit?.records || []" size="small">
        <el-table-column label="变化" width="90">
          <template #default="{ row }">
            <span :class="row.delta >= 0 ? 'plus' : 'minus'">{{ row.delta > 0 ? '+' + row.delta : row.delta }}</span>
          </template>
        </el-table-column>
        <el-table-column label="原因" width="160">
          <template #default="{ row }">{{ CREDIT_REASON_LABELS[row.reasonCode] || row.reasonCode }}</template>
        </el-table-column>
        <el-table-column prop="balanceAfter" label="余额" width="90" />
        <el-table-column prop="remark" label="备注" min-width="160" />
        <el-table-column prop="createdAt" label="时间" width="170" />
      </el-table>
    </el-card>

    <el-card class="mt">
      <template #header>积分明细（最近 10 条，余额 {{ user.points?.balance }}）</template>
      <el-table :data="user.points?.records || []" size="small">
        <el-table-column label="变化" width="90">
          <template #default="{ row }">{{ row.delta > 0 ? '+' + row.delta : row.delta }}</template>
        </el-table-column>
        <el-table-column prop="reasonCode" label="原因" width="160" />
        <el-table-column prop="balanceAfter" label="余额" width="90" />
        <el-table-column prop="createdAt" label="时间" width="170" />
      </el-table>
    </el-card>

    <el-dialog v-model="adjustVisible" title="微光值人工处置" width="460px">
      <el-form :model="adjustForm" label-width="80px">
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
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { banUser, creditAdjust, getUserDetail, unbanUser } from '../api/admin'
import { CREDIT_REASON_LABELS, USER_STATUS_LABELS } from '../utils/labels'

const route = useRoute()
const router = useRouter()
const user = ref(null)
const adjustVisible = ref(false)
const adjusting = ref(false)
const adjustForm = reactive({ delta: 0, remark: '' })

async function load() {
  user.value = await getUserDetail(route.params.id)
}

function goBack() {
  router.push('/users')
}

async function doBan(toBan) {
  const label = toBan ? '封禁' : '解封'
  try {
    await ElMessageBox.confirm(`确认${label}用户「${user.value.nickname || user.value.id}」？`, `${label}确认`, {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
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
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.mt {
  margin-top: 16px;
}

.ml {
  margin-left: 8px;
}

.muted {
  color: #999;
  font-size: 12px;
}

.plus {
  color: #67c23a;
}

.minus {
  color: #f56c6c;
}
</style>