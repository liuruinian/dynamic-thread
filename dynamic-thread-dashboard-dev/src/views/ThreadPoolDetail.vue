<template>
  <div class="thread-pool-detail">
    <div class="page-header">
      <el-page-header @back="goBack">
        <template #content>
          <div class="header-content">
            <el-icon :size="24"><Operation /></el-icon>
            <span class="title">{{ pool?.threadPoolId || '加载中...' }}</span>
            <el-tag :type="pool?.activeCount > 0 ? 'success' : 'info'" size="large">
              {{ pool?.activeCount > 0 ? '运行中' : '空闲' }}
            </el-tag>
          </div>
        </template>
        <template #extra>
          <el-space>
            <el-button :icon="Refresh" @click="fetchData">刷新</el-button>
            <el-button type="primary" :icon="Edit" @click="showEditDialog">修改配置</el-button>
          </el-space>
        </template>
      </el-page-header>
    </div>

    <el-row :gutter="20" v-loading="loading">
      <!-- 核心指标卡片 -->
      <el-col :span="6">
        <div class="card stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #409eff, #66b1ff);">
            <el-icon :size="28"><User /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ pool?.activeCount || 0 }}</div>
            <div class="stat-label">活跃线程</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="card stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #67c23a, #85ce61);">
            <el-icon :size="28"><List /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ pool?.queueSize || 0 }}</div>
            <div class="stat-label">队列任务</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="card stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #e6a23c, #f0c78a);">
            <el-icon :size="28"><Finished /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ formatNumber(pool?.completedTaskCount) }}</div>
            <div class="stat-label">完成任务</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="card stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #f56c6c, #fab6b6);">
            <el-icon :size="28"><CloseBold /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ pool?.rejectedCount || 0 }}</div>
            <div class="stat-label">拒绝任务</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- 配置信息 -->
      <el-col :span="12">
        <div class="card">
          <h3 class="section-title">线程池配置</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="核心线程数">
              <el-tag>{{ pool?.corePoolSize }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="最大线程数">
              <el-tag>{{ pool?.maximumPoolSize }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="当前线程数">
              {{ pool?.poolSize }}
            </el-descriptions-item>
            <el-descriptions-item label="最大历史线程">
              {{ pool?.largestPoolSize }}
            </el-descriptions-item>
            <el-descriptions-item label="队列容量">
              {{ pool?.queueCapacity }}
            </el-descriptions-item>
            <el-descriptions-item label="队列类型">
              {{ pool?.queueType || 'LinkedBlockingQueue' }}
            </el-descriptions-item>
            <el-descriptions-item label="存活时间">
              {{ pool?.keepAliveTime }}s
            </el-descriptions-item>
            <el-descriptions-item label="核心线程超时">
              <el-tag :type="pool?.allowCoreThreadTimeOut ? 'success' : 'info'" size="small">
                {{ pool?.allowCoreThreadTimeOut ? '是' : '否' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="拒绝策略" :span="2">
              {{ pool?.rejectedHandler }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </el-col>

      <!-- 使用率仪表盘 -->
      <el-col :span="12">
        <div class="card">
          <h3 class="section-title">资源使用率</h3>
          <el-row :gutter="20">
            <el-col :span="12">
              <v-chart class="gauge-chart" :option="threadGaugeOption" autoresize />
              <div class="gauge-label">线程使用率</div>
            </el-col>
            <el-col :span="12">
              <v-chart class="gauge-chart" :option="queueGaugeOption" autoresize />
              <div class="gauge-label">队列使用率</div>
            </el-col>
          </el-row>
        </div>
      </el-col>
    </el-row>

    <!-- RetryBuffer 缓冲区状态 -->
    <el-row :gutter="20" style="margin-top: 20px;" v-if="pool?.retryBufferEnabled">
      <el-col :span="24">
        <div class="card retry-buffer-card">
          <div class="card-header">
            <h3 class="section-title">
              <el-icon><Warning /></el-icon>
              暂存缓冲区状态 (RetryBufferPolicy)
            </h3>
            <el-tag type="warning" effect="plain">实时监控</el-tag>
          </div>
          
          <el-row :gutter="20">
            <!-- 缓冲区使用率 -->
            <el-col :span="8">
              <div class="buffer-gauge-container">
                <v-chart class="buffer-gauge" :option="bufferGaugeOption" autoresize />
                <div class="gauge-label">缓冲区使用率</div>
              </div>
            </el-col>
            
            <!-- 缓冲区统计 -->
            <el-col :span="16">
              <el-row :gutter="16" class="buffer-stats">
                <el-col :span="8">
                  <div class="buffer-stat-item">
                    <div class="buffer-stat-value primary">{{ pool?.retryBufferSize || 0 }}</div>
                    <div class="buffer-stat-label">当前暂存任务</div>
                    <div class="buffer-stat-detail">/ {{ pool?.retryBufferCapacity || 0 }} 容量</div>
                  </div>
                </el-col>
                <el-col :span="8">
                  <div class="buffer-stat-item">
                    <div class="buffer-stat-value warning">{{ formatNumber(pool?.retryBufferTotalBuffered) }}</div>
                    <div class="buffer-stat-label">累计暂存</div>
                  </div>
                </el-col>
                <el-col :span="8">
                  <div class="buffer-stat-item">
                    <div class="buffer-stat-value success">{{ formatNumber(pool?.retryBufferTotalRetried) }}</div>
                    <div class="buffer-stat-label">重投递成功</div>
                  </div>
                </el-col>
                <el-col :span="8">
                  <div class="buffer-stat-item">
                    <div class="buffer-stat-value danger">{{ formatNumber(pool?.retryBufferTotalFallback) }}</div>
                    <div class="buffer-stat-label">兆底执行</div>
                  </div>
                </el-col>
                <el-col :span="8">
                  <div class="buffer-stat-item">
                    <div class="buffer-stat-value info">{{ formatNumber(pool?.retryBufferTotalExpired) }}</div>
                    <div class="buffer-stat-label">过期任务</div>
                  </div>
                </el-col>
                <el-col :span="8">
                  <div class="buffer-stat-item">
                    <div class="buffer-stat-value" :class="retrySuccessRateClass">{{ retrySuccessRate }}%</div>
                    <div class="buffer-stat-label">重投递成功率</div>
                  </div>
                </el-col>
              </el-row>
            </el-col>
          </el-row>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- 历史趋势图 -->
      <el-col :span="24">
        <div class="card">
          <div class="card-header">
            <h3 class="section-title">历史趋势</h3>
            <el-radio-group v-model="chartType" size="small">
              <el-radio-button value="active">活跃线程</el-radio-button>
              <el-radio-button value="queue">队列大小</el-radio-button>
              <el-radio-button value="completed">完成任务</el-radio-button>
            </el-radio-group>
          </div>
          <v-chart class="trend-chart" :option="trendChartOption" autoresize />
        </div>
      </el-col>
    </el-row>

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" title="修改线程池配置" width="500px">
      <el-form :model="editForm" label-width="120px" :rules="rules" ref="formRef">
        <el-form-item label="核心线程数" prop="corePoolSize">
          <el-slider v-model="editForm.corePoolSize" :min="1" :max="200" show-input />
        </el-form-item>
        <el-form-item label="最大线程数" prop="maximumPoolSize">
          <el-slider v-model="editForm.maximumPoolSize" :min="1" :max="500" show-input />
        </el-form-item>
        <el-form-item label="队列容量" prop="queueCapacity">
          <el-input-number v-model="editForm.queueCapacity" :min="1" :max="100000" style="width: 100%" />
        </el-form-item>
        <el-form-item label="拒绝策略" prop="rejectedHandler">
          <el-select v-model="editForm.rejectedHandler" style="width: 100%">
            <el-option label="AbortPolicy - 抛出异常" value="AbortPolicy" />
            <el-option label="CallerRunsPolicy - 调用者执行" value="CallerRunsPolicy" />
            <el-option label="DiscardPolicy - 静默丢弃" value="DiscardPolicy" />
            <el-option label="DiscardOldestPolicy - 丢弃最旧" value="DiscardOldestPolicy" />
            <el-option label="RetryBufferPolicy - 暂存重投递" value="RetryBufferPolicy" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitConfig" :loading="submitting">保存配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Refresh, Edit, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { GaugeChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { threadPoolApi } from '@/api'

use([CanvasRenderer, GaugeChart, LineChart, GridComponent, TooltipComponent])

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const chartType = ref('active')
const pool = ref(null)
const formRef = ref(null)

// 历史数据
const history = reactive({
  timestamps: [],
  activeCount: [],
  queueSize: [],
  completedTaskCount: []
})

const editForm = reactive({
  instanceId: '',
  corePoolSize: 10,
  maximumPoolSize: 20,
  queueCapacity: 1000,
  rejectedHandler: 'AbortPolicy'
})

const rules = {
  corePoolSize: [{ required: true, message: '请输入核心线程数', trigger: 'blur' }],
  maximumPoolSize: [{ required: true, message: '请输入最大线程数', trigger: 'blur' }],
  queueCapacity: [{ required: true, message: '请输入队列容量', trigger: 'blur' }]
}

let refreshTimer = null

const threadGaugeOption = computed(() => ({
  series: [{
    type: 'gauge',
    progress: { show: true, width: 15 },
    axisLine: { lineStyle: { width: 15 } },
    axisTick: { show: false },
    splitLine: { show: false },
    axisLabel: { show: false },
    pointer: { show: false },
    anchor: { show: false },
    title: { show: false },
    detail: {
      valueAnimation: true,
      fontSize: 28,
      offsetCenter: [0, 0],
      formatter: '{value}%'
    },
    data: [{ value: Math.round(pool.value?.activePercent || 0) }]
  }]
}))

const queueGaugeOption = computed(() => ({
  series: [{
    type: 'gauge',
    progress: { show: true, width: 15, itemStyle: { color: '#e6a23c' } },
    axisLine: { lineStyle: { width: 15, color: [[1, '#e4e7ed']] } },
    axisTick: { show: false },
    splitLine: { show: false },
    axisLabel: { show: false },
    pointer: { show: false },
    anchor: { show: false },
    title: { show: false },
    detail: {
      valueAnimation: true,
      fontSize: 28,
      offsetCenter: [0, 0],
      formatter: '{value}%'
    },
    data: [{ value: Math.round(pool.value?.queueUsagePercent || 0) }]
  }]
}))

// RetryBuffer 缓冲区仪表盘
const bufferGaugeOption = computed(() => ({
  series: [{
    type: 'gauge',
    progress: { show: true, width: 15, itemStyle: { color: '#f56c6c' } },
    axisLine: { lineStyle: { width: 15, color: [[1, '#e4e7ed']] } },
    axisTick: { show: false },
    splitLine: { show: false },
    axisLabel: { show: false },
    pointer: { show: false },
    anchor: { show: false },
    title: { show: false },
    detail: {
      valueAnimation: true,
      fontSize: 28,
      offsetCenter: [0, 0],
      formatter: '{value}%'
    },
    data: [{ value: Math.round(pool.value?.retryBufferUsagePercent || 0) }]
  }]
}))

// 重投递成功率计算
const retrySuccessRate = computed(() => {
  const total = pool.value?.retryBufferTotalBuffered || 0
  const retried = pool.value?.retryBufferTotalRetried || 0
  if (total === 0) return 0
  return Math.round((retried / total) * 100)
})

// 成功率颜色样式
const retrySuccessRateClass = computed(() => {
  const rate = retrySuccessRate.value
  if (rate >= 80) return 'success'
  if (rate >= 50) return 'warning'
  return 'danger'
})

const trendChartOption = computed(() => {
  const dataKey = chartType.value === 'active' ? 'activeCount' 
    : chartType.value === 'queue' ? 'queueSize' : 'completedTaskCount'
  
  return {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: history.timestamps },
    yAxis: { type: 'value' },
    series: [{
      type: 'line',
      smooth: true,
      areaStyle: { opacity: 0.3 },
      data: history[dataKey] || []
    }]
  }
})

const formatNumber = (num) => {
  if (!num) return '0'
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toString()
}

const goBack = () => {
  router.push('/thread-pools')
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await threadPoolApi.states()
    
    // 从分层数据中查找匹配的线程池
    if (res.data) {
      for (const [appId, appInstances] of Object.entries(res.data)) {
        for (const [instanceId, states] of Object.entries(appInstances)) {
          for (const state of states) {
            if (state.threadPoolId === route.params.id) {
              pool.value = { appId, instanceId, ...state }
              editForm.instanceId = instanceId
              
              // 更新历史数据
              const now = new Date().toLocaleTimeString()
              history.timestamps.push(now)
              history.activeCount.push(state.activeCount)
              history.queueSize.push(state.queueSize)
              history.completedTaskCount.push(state.completedTaskCount)
              
              // 保持最近30条
              if (history.timestamps.length > 30) {
                history.timestamps.shift()
                history.activeCount.shift()
                history.queueSize.shift()
                history.completedTaskCount.shift()
              }
              return
            }
          }
        }
      }
    }
  } finally {
    loading.value = false
  }
}

const showEditDialog = () => {
  if (pool.value) {
    editForm.instanceId = pool.value.instanceId
    editForm.corePoolSize = pool.value.corePoolSize
    editForm.maximumPoolSize = pool.value.maximumPoolSize
    editForm.queueCapacity = pool.value.queueCapacity
    editForm.rejectedHandler = pool.value.rejectedHandler || 'AbortPolicy'
  }
  dialogVisible.value = true
}

const submitConfig = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  
  if (editForm.corePoolSize > editForm.maximumPoolSize) {
    ElMessage.warning('核心线程数不能大于最大线程数')
    return
  }
  
  submitting.value = true
  try {
    await threadPoolApi.updateConfig(editForm.instanceId, {
      threadPoolId: route.params.id,
      corePoolSize: editForm.corePoolSize,
      maximumPoolSize: editForm.maximumPoolSize,
      queueCapacity: editForm.queueCapacity,
      rejectedHandler: editForm.rejectedHandler
    })
    ElMessage.success('配置更新成功')
    dialogVisible.value = false
    fetchData()
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  fetchData()
  refreshTimer = setInterval(fetchData, 3000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style lang="scss" scoped>
.page-header {
  margin-bottom: 20px;
  
  .header-content {
    display: flex;
    align-items: center;
    gap: 12px;
    
    .title {
      font-size: 20px;
      font-weight: 600;
    }
  }
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  
  .stat-icon {
    width: 56px;
    height: 56px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
  }
  
  .stat-info {
    .stat-value {
      font-size: 28px;
      font-weight: 600;
      color: #303133;
    }
    
    .stat-label {
      font-size: 14px;
      color: #909399;
      margin-top: 4px;
    }
  }
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #303133;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  
  .section-title {
    margin-bottom: 0;
  }
}

.gauge-chart {
  height: 180px;
}

.gauge-label {
  text-align: center;
  color: #909399;
  font-size: 14px;
  margin-top: -20px;
}

.trend-chart {
  height: 300px;
}

// RetryBuffer 缓冲区状态样式
.retry-buffer-card {
  background: linear-gradient(135deg, #fff9f0 0%, #fff 100%);
  border-left: 4px solid #e6a23c;
  
  .section-title {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #e6a23c;
  }
}

.buffer-gauge-container {
  text-align: center;
}

.buffer-gauge {
  height: 200px;
}

.buffer-stats {
  .buffer-stat-item {
    text-align: center;
    padding: 16px 8px;
    background: #f5f7fa;
    border-radius: 8px;
    margin-bottom: 16px;
    
    .buffer-stat-value {
      font-size: 24px;
      font-weight: 600;
      
      &.primary { color: #409eff; }
      &.success { color: #67c23a; }
      &.warning { color: #e6a23c; }
      &.danger { color: #f56c6c; }
      &.info { color: #909399; }
    }
    
    .buffer-stat-label {
      font-size: 13px;
      color: #606266;
      margin-top: 4px;
    }
    
    .buffer-stat-detail {
      font-size: 12px;
      color: #909399;
      margin-top: 2px;
    }
  }
}
</style>
