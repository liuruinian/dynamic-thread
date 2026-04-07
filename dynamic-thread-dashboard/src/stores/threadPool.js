import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { threadPoolApi, testApi } from '@/api'

export const useThreadPoolStore = defineStore('threadPool', () => {
  // 状态
  const rawData = ref({}) // 原始分层数据: { appId: { instanceId: [states] } }
  const threadPools = ref({}) // 扁平化数据: { threadPoolId: state }
  const health = ref({})
  const webState = ref(null)
  const loading = ref(false)
  const lastUpdated = ref(null)
  const history = ref({
    timestamps: [],
    data: {}
  })

  // 计算属性 - 线程池列表
  const threadPoolList = computed(() => {
    const pools = []
    if (rawData.value && rawData.value.data) {
      for (const [appId, appInstances] of Object.entries(rawData.value.data)) {
        for (const [instanceId, states] of Object.entries(appInstances)) {
          for (const state of states) {
            pools.push({
              appId,
              instanceId,
              ...state
            })
          }
        }
      }
    } else {
      // 兼容旧的扁平数据结构
      pools.push(...Object.values(threadPools.value))
    }
    return pools
  })

  // 统计数据
  const totalStats = computed(() => {
    const pools = threadPoolList.value
    const apps = new Set()
    const instances = new Set()
    pools.forEach(p => {
      if (p.appId) apps.add(p.appId)
      if (p.instanceId) instances.add(p.instanceId)
    })
    return {
      appCount: apps.size,
      instanceCount: instances.size,
      threadPoolCount: pools.length,
      totalActiveThreads: pools.reduce((sum, p) => sum + (p.activeCount || 0), 0),
      totalQueuedTasks: pools.reduce((sum, p) => sum + (p.queueSize || 0), 0),
      totalCompletedTasks: pools.reduce((sum, p) => sum + (p.completedTaskCount || 0), 0),
      totalRejectedTasks: pools.reduce((sum, p) => sum + (p.rejectedCount || 0), 0)
    }
  })

  const isHealthy = computed(() => health.value.status === 'UP')

  // Actions
  async function fetchAll() {
    loading.value = true
    try {
      const [statesRes, healthRes, webRes] = await Promise.all([
        threadPoolApi.states(),
        threadPoolApi.health(),
        threadPoolApi.webState()
      ])
      rawData.value = statesRes
      health.value = healthRes
      webState.value = webRes
      lastUpdated.value = new Date()
      
      // 更新历史数据
      updateHistory()
    } finally {
      loading.value = false
    }
  }

  async function fetchStates() {
    try {
      const res = await threadPoolApi.states()
      rawData.value = res
      lastUpdated.value = new Date()
      updateHistory()
    } catch (e) {
      console.error('Failed to fetch states:', e)
      throw e
    }
  }

  function updateHistory() {
    const now = new Date().toLocaleTimeString()
    history.value.timestamps.push(now)
    if (history.value.timestamps.length > 30) {
      history.value.timestamps.shift()
    }

    threadPoolList.value.forEach(pool => {
      const id = pool.threadPoolId
      if (!history.value.data[id]) {
        history.value.data[id] = {
          activeCount: [],
          queueSize: [],
          completedTaskCount: []
        }
      }
      history.value.data[id].activeCount.push(pool.activeCount)
      history.value.data[id].queueSize.push(pool.queueSize)
      history.value.data[id].completedTaskCount.push(pool.completedTaskCount)

      // 保持最近 30 条数据
      if (history.value.data[id].activeCount.length > 30) {
        history.value.data[id].activeCount.shift()
        history.value.data[id].queueSize.shift()
        history.value.data[id].completedTaskCount.shift()
      }
    })
  }

  async function updateConfig(instanceIdOrThreadPoolId, config) {
    try {
      // 如果配置中没有 threadPoolId，可能是旧的调用方式
      if (config.threadPoolId) {
        // 新方式：使用 instanceId + threadPoolId
        return await threadPoolApi.updateConfig(instanceIdOrThreadPoolId, config)
      } else {
        // 旧方式：使用 threadPoolId
        return await threadPoolApi.update(instanceIdOrThreadPoolId, config)
      }
    } catch (e) {
      console.error('Failed to update config:', e)
      throw e
    }
  }

  async function getPoolState(id) {
    try {
      return await threadPoolApi.getState(id)
    } catch (e) {
      console.error('Failed to get pool state:', e)
      throw e
    }
  }

  async function submitTestTasks(count, sleepMs) {
    return await testApi.batch(count, sleepMs)
  }

  function getPoolById(id) {
    return threadPoolList.value.find(p => p.threadPoolId === id)
  }

  function getPoolHistory(id) {
    return history.value.data[id] || { activeCount: [], queueSize: [], completedTaskCount: [] }
  }

  // 导出配置
  function exportConfig() {
    const config = {
      exportTime: new Date().toISOString(),
      version: '1.0.0',
      threadPools: threadPoolList.value.map(pool => ({
        threadPoolId: pool.threadPoolId,
        corePoolSize: pool.corePoolSize,
        maximumPoolSize: pool.maximumPoolSize,
        queueCapacity: pool.queueCapacity,
        keepAliveTime: pool.keepAliveTime,
        rejectedHandler: pool.rejectedHandler,
        allowCoreThreadTimeOut: pool.allowCoreThreadTimeOut
      }))
    }
    return JSON.stringify(config, null, 2)
  }

  return {
    // State
    rawData,
    threadPools,
    health,
    webState,
    loading,
    lastUpdated,
    history,
    
    // Computed
    threadPoolList,
    totalStats,
    isHealthy,
    
    // Actions
    fetchAll,
    fetchStates,
    updateConfig,
    getPoolState,
    submitTestTasks,
    getPoolById,
    getPoolHistory,
    exportConfig
  }
})
