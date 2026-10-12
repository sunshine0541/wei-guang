<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <div class="title">微光 · 管理后台</div>
      <el-form :model="form" label-width="70px" @keyup.enter="onSubmit">
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="管理员账号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="submit" :loading="loading" @click="onSubmit">登录</el-button>
        </el-form-item>
      </el-form>
      <div class="tip">连续输错会被限流（每分钟 5 次），请稍后再试</div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../store/auth'

const router = useRouter()
const auth = useAuthStore()
const form = reactive({ username: '', password: '' })
const loading = ref(false)

async function onSubmit() {
  if (!form.username || !form.password) {
    return
  }
  loading.value = true
  try {
    await auth.login(form.username.trim(), form.password)
    router.push('/overview')
  } catch (e) {
    // 错误提示已由请求封装统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
}

.login-card {
  width: 380px;
  padding: 8px 12px;
}

.title {
  text-align: center;
  font-size: 20px;
  font-weight: 600;
  color: #b06a00;
  margin-bottom: 20px;
}

.submit {
  width: 100%;
}

.tip {
  text-align: center;
  color: #999;
  font-size: 12px;
}
</style>