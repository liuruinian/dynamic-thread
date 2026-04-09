import axios from 'axios'
import { ElMessage, ElNotification } from 'element-plus'

const api = axios.create({
  // 统一使用空 baseURL，API路径使用完整路径
  baseURL: '',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
api.interceptors.request.use(
  config => {
    // 添加 token
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['satoken'] = token
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  response => response.data,
  error => {
    let message = '请求失败'
    
    if (error.response) {
      const status = error.response.status
      const data = error.response.data
      
      switch (status) {
        case 400:
          message = data?.message || '请求参数错误'
          break
        case 401:
          message = '未授权，请登录'
          // 清除 token 并跳转登录页
          localStorage.removeItem('token')
          localStorage.removeItem('username')
          if (window.location.pathname !== '/login') {
            window.location.href = '/login'
          }
          break
        case 403:
          message = '拒绝访问'
          break
        case 404:
          message = '请求的资源不存在'
          break
        case 500:
          message = data?.message || '服务器内部错误'
          break
        case 502:
          message = '网关错误'
          break
        case 503:
          message = '服务不可用'
          break
        default:
          message = data?.message || `请求错误: ${status}`
      }
    } else if (error.code === 'ECONNABORTED') {
      message = '请求超时，请检查网络连接'
    } else if (!window.navigator.onLine) {
      message = '网络已断开，请检查网络连接'
    }
    
    // 显示错误消息
    ElNotification({
      title: '请求失败',
      message,
      type: 'error',
      duration: 3000
    })
    
    return Promise.reject(error)
  }
)

// 线程池相关 API (独立 Server 模式)
export const threadPoolApi = {
  // 获取所有应用
  listApps() {
    return api.get('/api/thread-pools/apps')
  },
  
  // 获取应用的所有实例
  listInstances(appId) {
    return api.get(`/api/thread-pools/apps/${appId}/instances`)
  },
  
  // 获取所有客户端
  listClients() {
    return api.get('/api/thread-pools/clients')
  },
  
  // 获取所有线程池状态（分组）
  states() {
    return api.get('/api/thread-pools/states')
  },
  
  // 获取实例的线程池状态
  getInstanceStates(instanceId) {
    return api.get(`/api/thread-pools/instances/${instanceId}/states`)
  },
  
  // 更新线程池配置
  updateConfig(instanceId, config) {
    return api.post(`/api/thread-pools/instances/${instanceId}/config`, config)
  },
  
  // 更新线程池配置 (兼容旧API调用)
  update(threadPoolId, config) {
    // 对于旧版本调用，尝试找到对应实例并更新
    return api.post('/api/thread-pools/config', { threadPoolId, ...config })
  },
  
  // 获取单个线程池状态
  getState(threadPoolId) {
    return api.get(`/api/thread-pools/${threadPoolId}/state`)
  },
  
  // 健康检查
  health() {
    return api.get('/api/thread-pools/health')
  },
  
  // 获取 Web 容器线程池状态
  webState() {
    return api.get('/api/thread-pools/web-state').catch(() => ({
      available: false,
      message: '独立部署模式不支持Web容器管理'
    }))
  },
  
  // 获取通知平台配置
  getNotifyPlatform(platform) {
    return api.get(`/api/notify/platforms/${platform}`).catch(() => ({
      found: false,
      platform: null
    }))
  }
}

// 告警相关 API
export const alarmApi = {
  // ==================== 规则管理 ====================
  
  // 获取所有告警规则
  listRules() {
    return api.get('/api/alarm/rules')
  },
  
  // 获取单个告警规则
  getRule(id) {
    return api.get(`/api/alarm/rules/${id}`)
  },
  
  // 添加告警规则
  addRule(rule) {
    return api.post('/api/alarm/rules', rule)
  },
  
  // 更新告警规则
  updateRule(id, rule) {
    return api.put(`/api/alarm/rules/${id}`, rule)
  },
  
  // 删除告警规则
  deleteRule(id) {
    return api.delete(`/api/alarm/rules/${id}`)
  },
  
  // 启用/禁用规则
  toggleRule(id, enabled) {
    return api.post(`/api/alarm/rules/${id}/toggle?enabled=${enabled}`)
  },
  
  // ==================== 历史记录 ====================
  
  // 获取告警历史
  getHistory(page = 0, size = 50) {
    return api.get(`/api/alarm/history?page=${page}&size=${size}`)
  },
  
  // 获取指定线程池的告警历史
  getHistoryByThreadPool(threadPoolId) {
    return api.get(`/api/alarm/history/${threadPoolId}`)
  },
  
  // 获取告警统计
  getStatistics() {
    return api.get('/api/alarm/statistics')
  },
  
  // 解决告警
  resolveAlarm(recordId) {
    return api.post(`/api/alarm/history/${recordId}/resolve`)
  },
  
  // 清空告警历史
  clearHistory() {
    return api.delete('/api/alarm/history')
  },
  
  // ==================== 平台管理 ====================
  
  // 获取已启用的通知平台
  getEnabledPlatforms() {
    return api.get('/api/alarm/platforms')
  },
  
  // 获取通知平台配置
  getPlatformConfig(platform) {
    return api.get(`/api/alarm/platforms/${platform}/config`)
  },
  
  // 启用通知平台
  enablePlatform(platform) {
    return api.post(`/api/alarm/platforms/${platform}/enable`)
  },
  
  // 禁用通知平台
  disablePlatform(platform) {
    return api.post(`/api/alarm/platforms/${platform}/disable`)
  },
  
  // ==================== 告警测试 ====================
  
  // 模拟告警
  simulate(type = 'queue', value = 90, threadPoolId = 'simulate-pool') {
    const params = new URLSearchParams()
    params.append('type', type)
    if (type === 'reject') {
      params.append('count', value)
    } else {
      params.append('usage', value)
    }
    params.append('threadPoolId', threadPoolId)
    return api.post(`/api/alarm/test/simulate?${params.toString()}`)
  },
  
  // 配置通知平台
  configurePlatform(platform, config) {
    return api.post(`/api/alarm/platforms/${platform}/configure`, config)
  },
  
  // 测试通知平台
  testPlatform(platform, config) {
    return api.post(`/api/alarm/platforms/${platform}/test`, config)
  },

  // ==================== 限流管理 ====================
  
  // 获取限流统计
  getRateLimitStatistics() {
    return api.get('/api/alarm/rate-limit/statistics')
  },
  
  // 获取所有限流状态
  getRateLimitStates() {
    return api.get('/api/alarm/rate-limit/states')
  },
  
  // 启用/禁用限流
  toggleRateLimit(enabled) {
    return api.post(`/api/alarm/rate-limit/toggle?enabled=${enabled}`)
  },
  
  // 设置限流间隔
  setRateLimitInterval(seconds) {
    return api.post(`/api/alarm/rate-limit/interval?seconds=${seconds}`)
  },
  
  // 重置所有限流状态
  resetAllRateLimits() {
    return api.post('/api/alarm/rate-limit/reset')
  },
  
  // 重置指定限流状态
  resetRateLimit(threadPoolId, metric) {
    return api.post(`/api/alarm/rate-limit/reset/${threadPoolId}/${metric}`)
  }
}

// 测试相关 API
export const testApi = {
  // 获取状态
  status() {
    return api.get('/api/test/status')
  },
  
  // 提交单个任务
  submit(instanceId, threadPoolId, sleepMs = 100) {
    return api.post(`/api/thread-pools/instances/${instanceId}/test/submit`, {
      threadPoolId,
      sleepMs
    })
  },
  
  // 提交批量任务
  batch(instanceId, threadPoolId, count = 10, sleepMs = 100) {
    return api.post(`/api/thread-pools/instances/${instanceId}/test/batch`, {
      threadPoolId,
      count,
      sleepMs
    })
  }
}

// 拒绝策略统计 API
export const rejectApi = {
  // 获取全局拒绝统计
  getStatistics() {
    return api.get('/api/reject/statistics')
  },
  
  // 获取指定线程池的拒绝统计
  getPoolStatistics(threadPoolId) {
    return api.get(`/api/reject/statistics/${threadPoolId}`)
  },
  
  // 获取所有拒绝记录
  getRecords(limit = 20) {
    return api.get(`/api/reject/records?limit=${limit}`)
  },
  
  // 获取指定线程池的拒绝记录
  getPoolRecords(threadPoolId, limit = 50) {
    return api.get(`/api/reject/records/${threadPoolId}?limit=${limit}`)
  },
  
  // 获取综合摘要
  getSummary() {
    return api.get('/api/reject/summary')
  },
  
  // 切换即时告警
  toggleInstantAlarm(enabled) {
    return api.post(`/api/reject/instant-alarm?enabled=${enabled}`)
  },
  
  // 重置指定线程池统计
  resetPool(threadPoolId) {
    return api.post(`/api/reject/reset/${threadPoolId}`)
  },
  
  // 重置所有统计
  resetAll() {
    return api.post('/api/reject/reset')
  }
}

// Web 容器线程池 API (Server 多应用模式)
export const webContainerApi = {
  // 获取所有应用的 Web 容器状态
  getAllStates() {
    return api.get('/api/web-containers/states')
  },
  
  // 获取按应用分组的 Web 容器状态
  getStatesGrouped() {
    return api.get('/api/web-containers/states/grouped')
  },
  
  // 获取指定应用的 Web 容器状态
  getStatesByApp(appId) {
    return api.get(`/api/web-containers/apps/${appId}/states`)
  },
  
  // 获取指定实例的 Web 容器状态
  getStateByInstance(instanceId) {
    return api.get(`/api/web-containers/instances/${instanceId}/state`)
  },
  
  // 获取 Web 容器汇总统计
  getSummary() {
    return api.get('/api/web-containers/summary')
  },
  
  // 获取有 Web 容器的应用列表
  getApps() {
    return api.get('/api/web-containers/apps')
  },
  
  // 更新指定实例的 Web 容器配置
  updateConfig(instanceId, config) {
    return api.post(`/api/web-containers/instances/${instanceId}/config`, config)
  }
}

// 服务器监控 API
export const serverApi = {
  // 获取完整服务器指标
  metrics() {
    return api.get('/api/server/metrics')
  },
  
  // 获取 CPU 信息
  cpu() {
    return api.get('/api/server/cpu')
  },
  
  // 获取内存信息
  memory() {
    return api.get('/api/server/memory')
  },
  
  // 获取磁盘信息
  disk() {
    return api.get('/api/server/disk')
  },
  
  // 获取网络信息
  network() {
    return api.get('/api/server/network')
  },
  
  // 获取 JVM 信息
  jvm() {
    return api.get('/api/server/jvm')
  }
}

// 集群管理 API
export const clusterApi = {
  // 获取集群概览
  info() {
    return api.get('/api/cluster/info')
  },
  
  // 获取所有集群节点
  nodes() {
    return api.get('/api/cluster/nodes')
  },
  
  // 获取单个节点详情
  node(nodeId) {
    return api.get(`/api/cluster/nodes/${nodeId}`)
  },
  
  // 集群健康检查
  health() {
    return api.get('/api/cluster/health')
  }
}

export default api
