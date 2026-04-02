<template>
  <div class="login-page">
    <!-- 左侧品牌区域 -->
    <div class="brand-section">
      <div class="brand-content">
        <div class="brand-icon">
          <svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="24" cy="24" r="20" stroke="currentColor" stroke-width="2" opacity="0.3"/>
            <circle cx="24" cy="24" r="12" stroke="currentColor" stroke-width="2"/>
            <circle cx="24" cy="24" r="4" fill="currentColor"/>
          </svg>
        </div>
        <h1 class="brand-title">Dynamic Thread Pool</h1>
        <p class="brand-subtitle">高性能动态线程池管理平台</p>
        <div class="feature-list">
          <div class="feature-item">
            <div class="feature-icon">
              <el-icon><TrendCharts /></el-icon>
            </div>
            <div class="feature-text">
              <span class="feature-title">实时监控</span>
              <span class="feature-desc">线程池运行状态一目了然</span>
            </div>
          </div>
          <div class="feature-item">
            <div class="feature-icon">
              <el-icon><Setting /></el-icon>
            </div>
            <div class="feature-text">
              <span class="feature-title">动态配置</span>
              <span class="feature-desc">在线调整核心参数无需重启</span>
            </div>
          </div>
          <div class="feature-item">
            <div class="feature-icon">
              <el-icon><Bell /></el-icon>
            </div>
            <div class="feature-text">
              <span class="feature-title">智能告警</span>
              <span class="feature-desc">异常检测多渠道及时通知</span>
            </div>
          </div>
        </div>
      </div>
      <div class="brand-footer">
        <span>v1.0.0</span>
      </div>
    </div>
    
    <!-- 右侧登录区域 -->
    <div class="login-section">
      <div class="login-wrapper">
        <div class="login-header">
          <div class="login-logo">
            <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="16" cy="16" r="14" stroke="currentColor" stroke-width="2"/>
              <circle cx="16" cy="16" r="8" stroke="currentColor" stroke-width="2"/>
              <circle cx="16" cy="16" r="3" fill="currentColor"/>
            </svg>
          </div>
          <h2>Sign in</h2>
          <p>登录您的账户以继续</p>
        </div>
        
        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          class="login-form"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="username">
            <label class="form-label">用户名</label>
            <el-input
              v-model="loginForm.username"
              placeholder="请输入用户名"
              size="large"
              clearable
            />
          </el-form-item>
          
          <el-form-item prop="password">
            <label class="form-label">密码</label>
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              size="large"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          
          <el-form-item>
            <el-button
              type="primary"
              size="large"
              :loading="loading"
              class="login-btn"
              @click="handleLogin"
            >
              {{ loading ? '登录中...' : '登录' }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { TrendCharts, Setting, Bell } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const loginFormRef = ref(null)
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return
  
  loading.value = true
  try {
    const result = await authStore.login(loginForm)
    if (result.success) {
      ElMessage.success('登录成功')
      const redirect = route.query.redirect || '/'
      router.push(redirect)
    } else {
      ElMessage.error(result.message || '登录失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
}

/* 左侧品牌区域 - Consul 风格 */
.brand-section {
  flex: 1;
  background: #1A1D21;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 60px;
  position: relative;
}

.brand-content {
  text-align: center;
  color: #fff;
  z-index: 1;
  max-width: 400px;
}

.brand-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto 28px;
  color: #8B5CF6;
}

.brand-icon svg {
  width: 100%;
  height: 100%;
}

.brand-title {
  font-size: 28px;
  font-weight: 600;
  margin: 0 0 8px;
  letter-spacing: -0.5px;
  color: #fff;
}

.brand-subtitle {
  font-size: 15px;
  color: #94A3B8;
  margin: 0 0 48px;
}

.feature-list {
  text-align: left;
}

.feature-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px 0;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.feature-item:first-child {
  border-top: none;
}

.feature-icon {
  width: 40px;
  height: 40px;
  background: rgba(139, 92, 246, 0.15);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8B5CF6;
  flex-shrink: 0;
}

.feature-icon .el-icon {
  font-size: 20px;
}

.feature-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.feature-title {
  font-size: 14px;
  font-weight: 500;
  color: #fff;
}

.feature-desc {
  font-size: 13px;
  color: #64748B;
}

.brand-footer {
  position: absolute;
  bottom: 30px;
  color: #475569;
  font-size: 12px;
}

/* 右侧登录区域 */
.login-section {
  width: 480px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  padding: 60px;
}

.login-wrapper {
  width: 100%;
  max-width: 340px;
}

.login-header {
  text-align: center;
  margin-bottom: 36px;
}

.login-logo {
  width: 48px;
  height: 48px;
  margin: 0 auto 20px;
  color: #8B5CF6;
}

.login-logo svg {
  width: 100%;
  height: 100%;
}

.login-header h2 {
  font-size: 24px;
  font-weight: 600;
  color: #1E293B;
  margin: 0 0 8px;
}

.login-header p {
  font-size: 14px;
  color: #94A3B8;
  margin: 0;
}

.form-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #475569;
  margin-bottom: 8px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.login-form :deep(.el-form-item__label) {
  display: none;
}

.login-form :deep(.el-input__wrapper) {
  padding: 4px 14px;
  border-radius: 8px;
  box-shadow: 0 0 0 1px #E2E8F0;
  transition: all 0.15s;
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #CBD5E1;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px rgba(139, 92, 246, 0.25);
}

.login-form :deep(.el-input--large .el-input__inner) {
  height: 42px;
}

.login-btn {
  width: 100%;
  height: 46px;
  font-size: 15px;
  font-weight: 500;
  border-radius: 8px;
  background: #8B5CF6;
  border: none;
  transition: all 0.2s;
  margin-top: 8px;
}

.login-btn:hover {
  background: #7C3AED;
}

.login-btn:active {
  background: #6D28D9;
}

/* 响应式 */
@media (max-width: 900px) {
  .brand-section {
    display: none;
  }
  
  .login-section {
    width: 100%;
  }
}
</style>
