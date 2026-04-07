<template>
  <div class="monitor">
    <div class="page-header">
      <h2>实时监控</h2>
      <p>线程池实时状态和性能图表</p>
    </div>

    <!-- 筛选区域 -->
    <div class="card" style="margin-bottom: 20px;">
      <el-row :gutter="20" align="middle">
        <el-col :span="6">
          <el-select v-model="selectedApp" placeholder="选择应用" clearable style="width: 100%"
                     @change="onAppChange">
            <el-option v-for="app in apps" :key="app" :label="app" :value="app" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-select v-model="selectedInstance" placeholder="选择实例" clearable style="width: 100%"
                     :disabled="!selectedApp">
            <el-option v-for="inst in instances" :key="inst" :label="inst" :value="inst" />
          </el-select>
        </el-col>
        <el-col :span="12" style="text-align: right;">
          <el-space>
            <el-switch v-model="autoRefresh" active-text="自动刷新" />
            <el-button :icon="Refresh" @click="fetchData" :loading="loading">刷新</el-button>
          </el-space>
        </el-col>
      </el-row>
    </div>

    <!-- 无数据提示 -->
    <div v-if="!loading && threadPools.length === 0" class="card">
      <el-empty description="暂无线程池数据">
        <template #image>
          <el-icon :size="80" color="#909399"><Monitor /></el-icon>
        </template>
        <p style="color: #909399; margin-top: 16px;">请确保有客户端应用连接到 Dashboard Server</p>
      </el-empty>
    </div>

    <!-- 统计卡片 -->
    <el-row v-if="threadPools.length > 0" :gutter="20" style="margin-bottom: 20px;">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ threadPools.length }}</div>
          <div class="stat-label">线程池数量</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ totalActive }}</div>
          <div class="stat-label">活跃线程</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ totalQueued }}</div>
          <div class="stat-label">排队任务</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ totalCompleted }}</div>
          <div class="stat-label">完成任务</div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <template v-if="threadPools.length > 0">
      <el-row :gutter="20">
        <el-col :span="12">
          <div class="card">
            <h3 class="chart-title">线程使用情况</h3>
            <v-chart class="chart" :option="threadChartOption" autoresize />
          </div>
        </el-col>
        <el-col :span="12">
          <div class="card">
            <h3 class="chart-title">队列使用情况</h3>
            <v-chart class="chart" :option="queueChartOption" autoresize />
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="20" style="margin-top: 20px;">
        <el-col :span="12">
          <div class="card">
            <h3 class="chart-title">任务完成趋势</h3>
            <v-chart class="chart" :option="taskChartOption" autoresize />
          </div>
        </el-col>
        <el-col :span="12">
          <div class="card">
            <h3 class="chart-title">线程池负载</h3>
            <v-chart class="chart" :option="loadChartOption" autoresize />
          </div>
        </el-col>
      </el-row>

      <!-- 线程池详情表格 -->
      <div class="card" style="margin-top: 20px;">
        <h3 class="chart-title">线程池详情</h3>
        <el-table :data="filteredPools" style="width: 100%" stripe>
          <el-table-column prop="threadPoolId" label="线程池" width="180" />
          <el-table-column prop="appId" label="应用" width="160" />
          <el-table-column label="线程" width="140">
            <template #default="{ row }">
              <span>{{ row.activeCount }} / {{ row.poolSize }} / {{ row.maximumPoolSize }}</span>
            </template>
          </el-table-column>
          <el-table-column label="队列" width="140">
            <template #default="{ row }">
              <span>{{ row.queueSize }} / {{ row.queueCapacity }}</span>
            </template>
          </el-table-column>
          <el-table-column label="线程使用率" width="150">
            <template #default="{ row }">
              <el-progress :percentage="Math.round(row.activePercent || 0)" 
                           :color="getProgressColor(row.activePercent)" />
            </template>
          </el-table-column>
          <el-table-column label="队列使用率" width="150">
            <template #default="{ row }">
              <el-progress :percentage="Math.round(row.queueUsagePercent || 0)" 
                           :color="getProgressColor(row.queueUsagePercent)" />
            </template>
          </el-table-column>
          <el-table-column prop="completedTaskCount" label="完成任务" width="120" />
          <el-table-column prop="rejectedCount" label="拒绝任务" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.rejectedCount > 0" type="danger" size="small">{{ row.rejectedCount }}</el-tag>
              <span v-else>0</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, GaugeChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { Refresh, Monitor } from '@element-plus/icons-vue'
import { threadPoolApi } from '@/api'

use([CanvasRenderer, BarChart, LineChart, GaugeChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent])

const loading = ref(false)
const autoRefresh = ref(true)
const threadPools = ref([])
const apps = ref([])
const instances = ref([])
const selectedApp = ref('')
const selectedInstance = ref('')

const history = reactive({
  timestamps: [],
  completed: {}
})

let refreshTimer = null

// 计算属性
const filteredPools = computed(() => {
  let result = threadPools.value
  if (selectedApp.value) {
    result = result.filter(p => p.appId === selectedApp.value)
  }
  if (selectedInstance.value) {
    result = result.filter(p => p.instanceId === selectedInstance.value)
  }
  return result
})

const totalActive = computed(() => filteredPools.value.reduce((sum, p) => sum + (p.activeCount || 0), 0))
const totalQueued = computed(() => filteredPools.value.reduce((sum, p) => sum + (p.queueSize || 0), 0))
const totalCompleted = computed(() => filteredPools.value.reduce((sum, p) => sum + (p.completedTaskCount || 0), 0))

const threadChartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['核心线程', '当前线程', '活跃线程', '最大线程'] },
  xAxis: { type: 'category', data: filteredPools.value.map(p => p.threadPoolId), axisLabel: { rotate: 30 } },
  yAxis: { type: 'value' },
  series: [
    { name: '核心线程', type: 'bar', data: filteredPools.value.map(p => p.corePoolSize), barGap: '0%' },
    { name: '当前线程', type: 'bar', data: filteredPools.value.map(p => p.poolSize) },
    { name: '活跃线程', type: 'bar', data: filteredPools.value.map(p => p.activeCount), itemStyle: { color: '#67c23a' } },
    { name: '最大线程', type: 'bar', data: filteredPools.value.map(p => p.maximumPoolSize), itemStyle: { color: '#e6e8eb' } }
  ]
}))

const queueChartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['队列大小', '队列容量'] },
  xAxis: { type: 'category', data: filteredPools.value.map(p => p.threadPoolId), axisLabel: { rotate: 30 } },
  yAxis: { type: 'value' },
  series: [
    { name: '队列大小', type: 'bar', data: filteredPools.value.map(p => p.queueSize), itemStyle: { color: '#409eff' } },
    { name: '队列容量', type: 'bar', data: filteredPools.value.map(p => p.queueCapacity), itemStyle: { color: '#e6e8eb' } }
  ]
}))

const taskChartOption = computed(() => {
  const pools = filteredPools.value
  const series = pools.map(pool => ({
    name: pool.threadPoolId,
    type: 'line',
    smooth: true,
    data: history.completed[pool.threadPoolId] || []
  }))
  
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: pools.map(p => p.threadPoolId) },
    xAxis: { type: 'category', data: history.timestamps },
    yAxis: { type: 'value', name: '完成任务数' },
    series
  }
})

const loadChartOption = computed(() => {
  const pools = filteredPools.value.slice(0, 4) // 最多显示4个仪表盘
  const centers = pools.length === 1 ? [['50%', '60%']] :
                  pools.length === 2 ? [['30%', '60%'], ['70%', '60%']] :
                  pools.length === 3 ? [['20%', '60%'], ['50%', '60%'], ['80%', '60%']] :
                  [['20%', '60%'], ['40%', '60%'], ['60%', '60%'], ['80%', '60%']]
  
  return {
    series: pools.map((pool, index) => ({
      type: 'gauge',
      center: centers[index],
      radius: pools.length <= 2 ? '70%' : '55%',
      min: 0,
      max: 100,
      progress: { show: true, width: 12 },
      axisLine: { lineStyle: { width: 12 } },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { show: false },
      pointer: { show: false },
      anchor: { show: false },
      title: { show: true, offsetCenter: [0, '85%'], fontSize: 12 },
      detail: { 
        valueAnimation: true, 
        offsetCenter: [0, '0%'],
        fontSize: 20,
        formatter: '{value}%'
      },
      data: [{ value: Math.round(pool.activePercent || 0), name: pool.threadPoolId }]
    }))
  }
})

// 方法
const getProgressColor = (percent) => {
  if (percent >= 80) return '#f56c6c'
  if (percent >= 60) return '#e6a23c'
  return '#67c23a'
}

const onAppChange = () => {
  selectedInstance.value = ''
  // 更新实例列表
  if (selectedApp.value) {
    const appPools = threadPools.value.filter(p => p.appId === selectedApp.value)
    instances.value = [...new Set(appPools.map(p => p.instanceId))]
  } else {
    instances.value = []
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await threadPoolApi.states()
    
    // 将分层数据转换为扁平列表
    const pools = []
    const appSet = new Set()
    
    if (res.data) {
      for (const [appId, appInstances] of Object.entries(res.data)) {
        appSet.add(appId)
        for (const [instanceId, states] of Object.entries(appInstances)) {
          for (const state of states) {
            pools.push({ appId, instanceId, ...state })
          }
        }
      }
    }
    
    threadPools.value = pools
    apps.value = [...appSet]
    
    // 如果当前选择的应用不在列表中，清除选择
    if (selectedApp.value && !appSet.has(selectedApp.value)) {
      selectedApp.value = ''
      selectedInstance.value = ''
    }
    
    // 更新历史数据
    const now = new Date().toLocaleTimeString()
    history.timestamps.push(now)
    if (history.timestamps.length > 20) history.timestamps.shift()
    
    pools.forEach(pool => {
      if (!history.completed[pool.threadPoolId]) {
        history.completed[pool.threadPoolId] = []
      }
      history.completed[pool.threadPoolId].push(pool.completedTaskCount)
      if (history.completed[pool.threadPoolId].length > 20) {
        history.completed[pool.threadPoolId].shift()
      }
    })
  } catch (e) {
    console.error('Failed to fetch thread pool states:', e)
  } finally {
    loading.value = false
  }
}

watch(autoRefresh, (val) => {
  if (val) {
    refreshTimer = setInterval(fetchData, 2000)
  } else {
    if (refreshTimer) clearInterval(refreshTimer)
  }
})

onMounted(() => {
  fetchData()
  if (autoRefresh.value) {
    refreshTimer = setInterval(fetchData, 2000)
  }
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style lang="scss" scoped>
.chart {
  height: 300px;
}

.chart-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #303133;
}

.stat-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  padding: 20px;
  color: white;
  text-align: center;
  
  .stat-value {
    font-size: 32px;
    font-weight: bold;
    margin-bottom: 8px;
  }
  
  .stat-label {
    font-size: 14px;
    opacity: 0.9;
  }
}

.stat-card:nth-child(2) {
  background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
}

.stat-card:nth-child(3) {
  background: linear-gradient(135deg, #fc4a1a 0%, #f7b733 100%);
}

.stat-card:nth-child(4) {
  background: linear-gradient(135deg, #6a11cb 0%, #2575fc 100%);
}

:deep(.el-col:nth-child(1) .stat-card) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

:deep(.el-col:nth-child(2) .stat-card) {
  background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
}

:deep(.el-col:nth-child(3) .stat-card) {
  background: linear-gradient(135deg, #fc4a1a 0%, #f7b733 100%);
}

:deep(.el-col:nth-child(4) .stat-card) {
  background: linear-gradient(135deg, #6a11cb 0%, #2575fc 100%);
}
</style>
