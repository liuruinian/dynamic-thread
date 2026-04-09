import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/components/Layout.vue'

const routes = [
  // 登录页面 - 独立路由
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true }
  },
  // 主布局路由
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '概览' }
      },
      {
        path: 'thread-pools',
        name: 'ThreadPools',
        component: () => import('@/views/ThreadPools.vue'),
        meta: { title: '线程池管理' }
      },
      {
        path: 'thread-pools/:id',
        name: 'ThreadPoolDetail',
        component: () => import('@/views/ThreadPoolDetail.vue'),
        meta: { title: '线程池详情' }
      },
      {
        path: 'monitor',
        name: 'Monitor',
        component: () => import('@/views/Monitor.vue'),
        meta: { title: '实时监控' }
      },
      {
        path: 'config',
        name: 'Config',
        component: () => import('@/views/Config.vue'),
        meta: { title: '配置管理' }
      },
      {
        path: 'alarm',
        name: 'Alarm',
        component: () => import('@/views/Alarm.vue'),
        meta: { title: '告警配置' }
      },
      {
        path: 'reject-stats',
        name: 'RejectStats',
        component: () => import('@/views/RejectStats.vue'),
        meta: { title: '拒绝统计' }
      },
      {
        path: 'web-container',
        name: 'WebContainer',
        component: () => import('@/views/WebContainer.vue'),
        meta: { title: 'Web容器线程池' }
      },
      {
        path: 'server-monitor',
        name: 'ServerMonitor',
        component: () => import('@/views/ServerMonitor.vue'),
        meta: { title: '服务器监控' }
      },
      {
        path: 'cluster',
        name: 'ClusterNodes',
        component: () => import('@/views/ClusterNodes.vue'),
        meta: { title: '集群节点' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  // 公开页面无需鉴权
  if (to.meta.public) {
    next()
    return
  }
  
  // 检查是否登录
  const token = localStorage.getItem('token')
  if (!token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }
  
  next()
})

export default router
