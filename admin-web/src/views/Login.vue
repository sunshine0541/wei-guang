<template>
  <div class="login-page">
    <div class="login-shell">
      <aside class="brand-pane">
        <div class="brand">
          <div class="brand-mark">微</div>
          <div>
            <h1>微光</h1>
            <p>社区互助 · 管理后台</p>
          </div>
        </div>

        <div class="tagline">
          <h2>让每一次求助<br />都能被看见</h2>
          <p>值班台、处置队列、社区审核与申诉仲裁，集中在一个工作面上。</p>
        </div>

        <ul class="feature">
          <li>打开就知道今天要处理什么</li>
          <li>举报、申诉、审核就地处置</li>
          <li>所有处置动作留痕可追溯</li>
        </ul>

        <p class="brand-foot">微光 · 邻里互助演示环境</p>
      </aside>

      <section class="form-pane">
        <div class="form-head">
          <h2>登录后台</h2>
          <p>使用管理员账号继续</p>
        </div>

        <el-form :model="form" label-position="top" @keyup.enter="onSubmit">
          <el-form-item label="账号">
            <el-input v-model="form.username" placeholder="管理员账号" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" placeholder="密码" show-password />
          </el-form-item>
          <el-button type="primary" class="submit" :loading="loading" @click="onSubmit">登录</el-button>
        </el-form>

        <p class="tip">连续输错会被限流（每分钟 5 次），请稍后再试</p>
      </section>
    </div>
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
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 32px 20px;
  background:
    radial-gradient(circle at 18% 12%, rgba(181, 102, 60, 0.1), transparent 42%),
    radial-gradient(circle at 82% 88%, rgba(94, 140, 97, 0.08), transparent 38%),
    var(--wg-canvas);
}

.login-shell {
  display: grid;
  grid-template-columns: 400px 1fr;
  width: 100%;
  max-width: 860px;
  border: 1px solid var(--wg-line);
  border-radius: 16px;
  background: var(--wg-panel);
  box-shadow: 0 20px 60px rgba(46, 42, 38, 0.1);
  overflow: hidden;
}

.brand-pane {
  display: flex;
  flex-direction: column;
  padding: 34px 34px 28px;
  background: var(--wg-ink);
  color: #e8e2d8;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 11px;
  background: linear-gradient(135deg, #c77b2b, #8a4b22);
  color: #fff6ec;
  font-size: 16px;
  font-weight: 700;
}

.brand h1 {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.22em;
  color: #f5ede2;
}

.brand p {
  margin: 3px 0 0;
  font-size: 10.5px;
  letter-spacing: 0.08em;
  color: #8b8175;
}

.tagline {
  margin-top: 54px;
}

.tagline h2 {
  margin: 0;
  font-size: 26px;
  font-weight: 600;
  line-height: 1.45;
  letter-spacing: 0.02em;
  color: #f5ede2;
}

.tagline p {
  margin: 16px 0 0;
  font-size: 12.5px;
  line-height: 1.8;
  color: #a89d90;
}

.feature {
  margin: 28px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.feature li {
  position: relative;
  padding-left: 16px;
  font-size: 12px;
  color: #c6bbae;
}

.feature li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--wg-clay);
  opacity: 0.8;
}

.brand-foot {
  margin: auto 0 0;
  padding-top: 40px;
  font-size: 10.5px;
  color: #6e655b;
}

.form-pane {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 46px 56px;
}

.form-head h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.form-head p {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--wg-muted);
}

.el-form {
  margin-top: 24px;
}

:deep(.el-form-item__label) {
  font-size: 12px;
  color: var(--wg-text-soft);
}

.submit {
  width: 100%;
  height: 40px;
  margin-top: 4px;
  font-size: 13px;
}

.tip {
  margin: 18px 0 0;
  text-align: center;
  font-size: 10.5px;
  color: var(--wg-muted-light);
}

@media (max-width: 820px) {
  .login-shell {
    grid-template-columns: 1fr;
    max-width: 420px;
  }

  .brand-pane {
    display: none;
  }

  .form-pane {
    padding: 36px 30px;
  }
}
</style>
