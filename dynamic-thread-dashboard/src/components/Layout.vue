<template>
  <div class="consul-layout">
    <!-- 顶部导航栏 -->
    <header class="consul-header">
      <div class="header-container">
        <!-- Logo -->
        <div class="logo" @click="router.push('/dashboard')">
          <div class="logo-icon">
            <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="16" cy="16" r="14" stroke="currentColor" stroke-width="2"/>
              <circle cx="16" cy="16" r="8" stroke="currentColor" stroke-width="2"/>
              <circle cx="16" cy="16" r="3" fill="currentColor"/>
            </svg>
          </div>
          <span class="logo-text">Dynamic Thread Pool</span>
        </div>
        
        <!-- 主导航 -->
        <nav class="main-nav">
          <router-link 
            v-for="item in navItems" 
            :key="item.path"
            :to="item.path"
            :class="['nav-item', { active: isActive(item.path) }]"
          >
            {{ item.title }}
          </router-link>
        </nav>
        
        <!-- 右侧工具栏 -->
        <div class="header-actions">
          <div class="status-indicator">
            <span class="status-dot"></span>
            <span class="status-text">Running</span>
          </div>
          
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-menu">
              <div class="user-avatar">
                {{ username?.charAt(0)?.toUpperCase() || 'A' }}
              </div>
              <el-icon class="dropdown-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  <span class="dropdown-label">当前用户</span>
                  <span class="dropdown-value">{{ username }}</span>
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  <span>退出登录</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>
    
    <!-- 内容区 -->
    <main class="consul-main">
      <div class="main-container">
        <!-- 页面标题 -->
        <div class="page-header">
          <h1 class="page-title">{{ route.meta.title || '概览' }}</h1>
        </div>
        
        <!-- 页面内容 -->
        <div class="page-content">
          <router-view />
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const username = computed(() => authStore.username)

// 导航项配置
const navItems = [
  { path: '/dashboard', title: '概览' },
  { path: '/thread-pools', title: '线程池' },
  { path: '/monitor', title: '监控' },
  { path: '/config', title: '配置' },
  { path: '/alarm', title: '告警' },
  { path: '/reject-stats', title: '拒绝统计' },
  { path: '/web-container', title: 'Web容器' },
  { path: '/server-monitor', title: '服务器' }
]

// 检查当前路由是否激活
const isActive = (path) => {
  if (path === '/thread-pools') {
    return route.path.startsWith('/thread-pools')
  }
  return route.path === path
}

const handleCommand = async (command) => {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm(
        '确定要退出登录吗？',
        '退出确认',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
      
      await authStore.logout()
      ElMessage.success('已退出登录')
      router.push('/login')
    } catch (error) {
      // 用户取消
    }
  }
}
</script>

<style scoped>
/* Consul 风格变量 */
:root {
  --consul-primary: #8B5CF6;
  --consul-primary-dark: #7C3AED;
  --consul-bg: #F8FAFC;
  --consul-header-bg: #1A1D21;
  --consul-text: #1E293B;
  --consul-text-secondary: #64748B;
  --consul-border: #E2E8F0;
}

.consul-layout {
  min-height: 100vh;
  background: var(--consul-bg);
}

/* 顶部导航栏 */
.consul-header {
  height: 60px;
  background: #1A1D21;
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-container {
  max-width: 1400px;
  margin: 0 auto;
  height: 100%;
  display: flex;
  align-items: center;
  padding: 0 24px;
  gap: 40px;
}

/* Logo */
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  flex-shrink: 0;
}

.logo-icon {
  width: 28px;
  height: 28px;
  color: #8B5CF6;
}

.logo-icon svg {
  width: 100%;
  height: 100%;
}

.logo-text {
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  letter-spacing: -0.3px;
}

/* 主导航 */
.main-nav {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
}

.nav-item {
  padding: 8px 16px;
  color: rgba(255, 255, 255, 0.7);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  border-radius: 6px;
  transition: all 0.15s ease;
}

.nav-item:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
}

.nav-item.active {
  color: #fff;
  background: rgba(139, 92, 246, 0.2);
}

/* 右侧工具栏 */
.header-actions {
  display: flex;
  align-items: center;
  gap: 20px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: rgba(34, 197, 94, 0.1);
  border-radius: 20px;
}

.status-dot {
  width: 8px;
  height: 8px;
  background: #22C55E;
  border-radius: 50%;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.status-text {
  font-size: 13px;
  color: #22C55E;
  font-weight: 500;
}

/* 用户菜单 */
.user-menu {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.user-menu:hover {
  background: rgba(255, 255, 255, 0.08);
}

.user-avatar {
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #8B5CF6 0%, #A78BFA 100%);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

.dropdown-arrow {
  color: rgba(255, 255, 255, 0.5);
  font-size: 14px;
}

/* 内容区 */
.consul-main {
  min-height: calc(100vh - 60px);
  padding: 32px 24px;
}

.main-container {
  max-width: 1400px;
  margin: 0 auto;
}

/* 页面标题 */
.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #1E293B;
  margin: 0;
}

/* 页面内容 */
.page-content {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  padding: 24px;
}

/* 下拉菜单样式 */
:deep(.el-dropdown-menu) {
  padding: 8px;
  border-radius: 10px;
}

:deep(.el-dropdown-menu__item) {
  padding: 10px 12px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 10px;
}

:deep(.el-dropdown-menu__item:not(.is-disabled):hover) {
  background: #F8FAFC;
  color: #8B5CF6;
}

.dropdown-label {
  color: #94A3B8;
  font-size: 12px;
}

.dropdown-value {
  color: #1E293B;
  font-weight: 500;
}
</style>
