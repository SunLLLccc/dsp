<template>
  <div class="login-container">
    <!-- 动态背景 -->
    <div class="login-bg">
      <div class="bg-shape shape-1"></div>
      <div class="bg-shape shape-2"></div>
      <div class="bg-shape shape-3"></div>
    </div>

    <!-- 左侧品牌区 -->
    <div class="login-brand">
      <div class="brand-content">
        <div class="brand-logo-wrap">
          <svg class="brand-logo" viewBox="0 0 56 56" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="2" y="2" width="52" height="52" rx="12" fill="rgba(255,255,255,0.15)" stroke="rgba(255,255,255,0.25)" stroke-width="1"/>
            <path d="M16 18h10v20H16V18zm14 0h10v20H30V18z" fill="white"/>
          </svg>
        </div>
        <h1 class="brand-title">DSP</h1>
        <div class="brand-divider"></div>
        <h2 class="brand-subtitle">数据服务平台</h2>
        <p class="brand-desc">统一数据查询 · 接口管理 · 审批发布</p>
        <div class="brand-features">
          <div class="feature-item">
            <div class="feature-dot"></div>
            <span>多数据源引擎</span>
          </div>
          <div class="feature-item">
            <div class="feature-dot"></div>
            <span>可视化调试</span>
          </div>
          <div class="feature-item">
            <div class="feature-dot"></div>
            <span>审批工作流</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧登录表单 -->
    <div class="login-form-wrap">
      <div class="login-card">
        <div class="login-welcome">
          <h2 class="login-title">欢迎回来</h2>
          <p class="login-subtitle">登录以继续访问管理后台</p>
        </div>
        <el-form :model="form" :rules="rules" ref="formRef" @submit.prevent="handleLogin" class="login-form">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" prefix-icon="User" size="large" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" prefix-icon="Lock" size="large"
              show-password @keyup.enter="handleLogin" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
              {{ loading ? '登录中...' : '登 录' }}
            </el-button>
          </el-form-item>
        </el-form>
        <div class="login-footer">
          <span>DSP Data Service Platform</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { authApi } from '../../api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await authApi.login(form)
    authStore.setLogin(res.data)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e) {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  position: relative;
  overflow: hidden;
}

/* 动态背景装饰 */
.login-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}

.bg-shape {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.5;
  animation: float 20s ease-in-out infinite;
}

.shape-1 {
  width: 600px;
  height: 600px;
  background: var(--el-color-primary-light-5);
  top: -200px;
  right: -100px;
  animation-delay: 0s;
}

.shape-2 {
  width: 400px;
  height: 400px;
  background: var(--el-color-primary-light-3);
  bottom: -100px;
  left: 20%;
  animation-delay: -7s;
}

.shape-3 {
  width: 300px;
  height: 300px;
  background: #A78BFA;
  top: 40%;
  right: 30%;
  animation-delay: -14s;
}

@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  25% { transform: translate(30px, -30px) scale(1.05); }
  50% { transform: translate(-20px, 20px) scale(0.95); }
  75% { transform: translate(15px, 15px) scale(1.02); }
}

/* 左侧品牌区 */
.login-brand {
  flex: 1;
  background: linear-gradient(160deg, #3B5BDB 0%, #4C6EF5 40%, #5C7CFA 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px;
  position: relative;
  z-index: 1;
}

.login-brand::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 20% 80%, rgba(255,255,255,0.08) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 20%, rgba(255,255,255,0.06) 0%, transparent 40%);
}

.login-brand::after {
  content: '';
  position: absolute;
  inset: 40px;
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 20px;
  pointer-events: none;
}

.brand-content {
  text-align: center;
  color: white;
  max-width: 420px;
  position: relative;
  z-index: 2;
}

.brand-logo-wrap {
  margin-bottom: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.brand-logo {
  width: 72px;
  height: 72px;
  filter: drop-shadow(0 4px 16px rgba(0,0,0,0.15));
}

.brand-title {
  font-size: 42px;
  font-weight: 800;
  letter-spacing: 6px;
  margin-bottom: 12px;
  background: linear-gradient(180deg, #FFFFFF 0%, rgba(255,255,255,0.85) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.brand-divider {
  width: 48px;
  height: 2px;
  background: rgba(255,255,255,0.4);
  margin: 0 auto 16px;
  border-radius: 1px;
}

.brand-subtitle {
  font-size: 18px;
  font-weight: 500;
  letter-spacing: 3px;
  margin-bottom: 12px;
  opacity: 0.95;
}

.brand-desc {
  font-size: 13px;
  letter-spacing: 2px;
  opacity: 0.6;
  margin-bottom: 36px;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: flex-start;
  margin: 0 auto;
  width: fit-content;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  opacity: 0.7;
  letter-spacing: 1px;
}

.feature-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(255,255,255,0.6);
  flex-shrink: 0;
}

/* 右侧表单区 */
.login-form-wrap {
  width: 520px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px);
  padding: 60px;
  position: relative;
  z-index: 1;
}

.login-card {
  width: 100%;
  max-width: 360px;
}

.login-welcome {
  margin-bottom: 36px;
}

.login-title {
  font-size: 26px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
  letter-spacing: 1px;
}

.login-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.login-form .el-form-item {
  margin-bottom: 22px;
}

.login-btn {
  width: 100%;
  height: 46px;
  font-size: 15px;
  font-weight: 600;
  border-radius: var(--radius-md);
  letter-spacing: 2px;
}

.login-footer {
  text-align: center;
  margin-top: 32px;
  font-size: 11px;
  color: var(--text-placeholder);
  letter-spacing: 1px;
}

/* 响应式 */
@media (max-width: 767px) {
  .login-container {
    flex-direction: column;
  }

  .login-brand {
    padding: 40px 24px 32px;
    min-height: auto;
  }

  .brand-logo {
    width: 48px;
    height: 48px;
  }

  .brand-title {
    font-size: 28px;
    letter-spacing: 4px;
  }

  .brand-subtitle {
    font-size: 14px;
  }

  .brand-features {
    display: none;
  }

  .login-form-wrap {
    width: 100%;
    padding: 32px 24px;
  }
}
</style>
