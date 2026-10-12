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
    <el-card class="note">
      <template #header>说明</template>
      「已完成数」= 双确认完成结算的求助；类型分布按全状态统计。
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getOverview } from '../api/admin'

const data = ref({})
const dist = computed(() => data.value.typeDistribution || {})

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

.note {
  margin-top: 16px;
  color: #666;
}
</style>