<template>
  <div class="web-container-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h1>Web 容器线程池</h1>
      </div>
      <div class="header-right">
        <el-switch v-model="autoRefresh" active-text="自动刷新" style="margin-right: 16px;" />
        <el-button @click="refreshData" :loading="loading" :icon="Refresh">
          刷新数据
        </el-button>
      </div>
    </div>

    <!-- 汇总统计卡片 -->
    <el-row :gutter="20" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon icon-purple">
              <el-icon :size="28"><Monitor /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ summary.totalInstances || 0 }}</div>
              <div class="stat-label">总实例数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon icon-green">
              <el-icon :size="28"><CircleCheck /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ summary.onlineInstances || 0 }}</div>
              <div class="stat-label">在线实例</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon icon-blue">
              <el-icon :size="28"><TrendCharts /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value" :class="getPercentClass(summary.avgActivePercent)">
                {{ formatPercent(summary.avgActivePercent) }}
              </div>
              <div class="stat-label">平均活跃率</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon icon-pink">
              <el-icon :size="28"><List /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value" :class="getPercentClass(summary.avgQueueUsagePercent)">
                {{ formatPercent(summary.avgQueueUsagePercent) }}
              </div>
              <div class="stat-label">平均队列使用率</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 应用列表 -->
    <el-card shadow="hover" class="app-list-card">
      <template #header>
        <div class="card-header">
          <div class="header-title">
            <el-icon><Grid /></el-icon>
            <span>应用 Web 容器列表</span>
          </div>
          <div class="header-actions">
            <el-input
              v-model="searchText"
              placeholder="搜索应用/实例"
              :prefix-icon="Search"
              clearable
              size="default"
              style="width: 200px; margin-right: 12px;"
            />
            <el-select 
              v-model="filterApp" 
              placeholder="筛选应用" 
              clearable 
              size="default"
              style="width: 160px; margin-right: 12px;"
            >
              <el-option 
                v-for="app in appList" 
                :key="app" 
                :label="app" 
                :value="app" 
              />
            </el-select>
            <el-tag type="info" size="large">{{ filteredStates.length }} 个实例</el-tag>
          </div>
        </div>
      </template>

      <!-- 空状态 -->
      <el-empty v-if="filteredStates.length === 0 && !loading" description="暂无 Web 容器实例" />

      <!-- 实例表格 -->
      <el-table 
        v-else
        :data="filteredStates" 
        style="width: 100%" 
        stripe 
        v-loading="loading"
        :header-cell-style="{ background: '#fafafa', color: '#606266', fontWeight: 600 }"
      >
        <el-table-column prop="appId" label="应用" min-width="160">
          <template #default="{ row }">
            <div class="app-cell">
              <el-tag type="primary" effect="plain" size="default">{{ row.appId }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="instanceId" label="实例" min-width="200">
          <template #default="{ row }">
            <el-tooltip :content="row.instanceId" placement="top" :show-after="300">
              <span class="instance-id">{{ formatInstanceId(row.instanceId) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="containerType" label="容器" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getContainerTagType(row.containerType)" size="default" effect="dark">
              {{ row.containerType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <div class="status-cell">
              <span class="status-dot" :class="row.online ? 'online' : 'offline'"></span>
              <span :class="row.online ? 'status-online' : 'status-offline'">
                {{ row.online ? '在线' : '离线' }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="线程池" width="130" align="center">
          <template #default="{ row }">
            <div class="pool-info">
              <span class="pool-current">{{ row.state?.poolSize || 0 }}</span>
              <span class="pool-divider">/</span>
              <span class="pool-max">{{ row.state?.maximumPoolSize || 0 }}</span>
            </div>
            <div class="pool-label">当前 / 最大</div>
          </template>
        </el-table-column>
        <el-table-column label="活跃率" width="160" align="center">
          <template #default="{ row }">
            <div class="metric-cell">
              <el-progress 
                :percentage="Math.min(row.state?.activePercent || 0, 100)" 
                :color="getProgressColor(row.state?.activePercent)"
                :stroke-width="10"
                :show-text="false"
              />
              <span class="metric-value" :class="getPercentClass(row.state?.activePercent)">
                {{ formatPercent(row.state?.activePercent) }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="队列" width="100" align="center">
          <template #default="{ row }">
            <div class="queue-cell">
              <span class="queue-value">{{ row.state?.queueSize || 0 }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180" align="center">
          <template #default="{ row }">
            <div class="time-cell">
              <el-icon><Clock /></el-icon>
              <span>{{ formatTime(row.state?.timestamp) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button 
                type="primary" 
                size="small"
                :disabled="!row.online"
                @click="openMonitorDialog(row)"
              >
                监控
              </el-button>
              <el-button 
                type="warning" 
                size="small"
                :disabled="!row.online"
                @click="openConfigDialog(row)"
              >
                配置
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 配置对话框 -->
    <el-dialog 
      v-model="configDialogVisible" 
      title="Web 容器线程池配置" 
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form :model="configForm" label-width="120px" :rules="configRules" ref="configFormRef">
        <el-descriptions :column="2" border size="default" style="margin-bottom: 24px;">
          <el-descriptions-item label="应用">
            <el-tag type="primary">{{ currentInstance?.appId }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="容器">
            <el-tag :type="getContainerTagType(currentInstance?.containerType)">
              {{ currentInstance?.containerType }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="实例" :span="2">
            <span class="instance-id">{{ currentInstance?.instanceId }}</span>
          </el-descriptions-item>
        </el-descriptions>
        
        <el-form-item label="核心线程数" prop="corePoolSize">
          <el-input-number 
            v-model="configForm.corePoolSize" 
            :min="1" 
            :max="configForm.maximumPoolSize || 500"
            style="width: 200px"
          />
          <span class="form-hint">当前: {{ currentInstance?.state?.corePoolSize }}</span>
        </el-form-item>
        <el-form-item label="最大线程数" prop="maximumPoolSize">
          <el-input-number 
            v-model="configForm.maximumPoolSize" 
            :min="configForm.corePoolSize || 1" 
            :max="1000"
            style="width: 200px"
          />
          <span class="form-hint">当前: {{ currentInstance?.state?.maximumPoolSize }}</span>
        </el-form-item>
        <el-form-item label="空闲超时(秒)" prop="keepAliveTime">
          <el-input-number 
            v-model="configForm.keepAliveTime" 
            :min="0" 
            :max="3600"
            style="width: 200px"
          />
          <span class="form-hint">当前: {{ currentInstance?.state?.keepAliveTime || 60 }}s</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitConfig" :loading="configSubmitting">
          确认更新
        </el-button>
      </template>
    </el-dialog>

    <!-- 监控对话框 -->
    <el-dialog 
      v-model="monitorDialogVisible" 
      :title="`实例监控 - ${monitorInstance?.appId || ''}`" 
      width="800px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="monitor-content">
        <!-- 实时状态 -->
        <el-row :gutter="16" class="monitor-stats">
          <el-col :span="6">
            <div class="monitor-stat-item">
              <div class="stat-icon-sm" :class="getContainerIconClass(monitorInstance?.containerType)">
                <el-icon><Platform /></el-icon>
              </div>
              <div class="stat-text">
                <div class="stat-value-sm">{{ monitorInstance?.containerType }}</div>
                <div class="stat-label-sm">容器类型</div>
              </div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="monitor-stat-item">
              <div class="stat-icon-sm icon-blue">
                <el-icon><User /></el-icon>
              </div>
              <div class="stat-text">
                <div class="stat-value-sm">
                  {{ monitorInstance?.state?.poolSize || 0 }} / {{ monitorInstance?.state?.maximumPoolSize || 0 }}
                </div>
                <div class="stat-label-sm">当前/最大线程</div>
              </div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="monitor-stat-item">
              <div class="stat-icon-sm icon-green">
                <el-icon><Promotion /></el-icon>
              </div>
              <div class="stat-text">
                <div class="stat-value-sm" :class="getPercentClass(monitorInstance?.state?.activePercent)">
                  {{ monitorInstance?.state?.activeCount || 0 }} ({{ formatPercent(monitorInstance?.state?.activePercent) }})
                </div>
                <div class="stat-label-sm">活跃线程</div>
              </div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="monitor-stat-item">
              <div class="stat-icon-sm icon-orange">
                <el-icon><List /></el-icon>
              </div>
              <div class="stat-text">
                <div class="stat-value-sm">{{ monitorInstance?.state?.queueSize || 0 }}</div>
                <div class="stat-label-sm">队列大小</div>
              </div>
            </div>
          </el-col>
        </el-row>

        <el-divider />

        <!-- 详细信息 -->
        <el-descriptions :column="3" border>
          <el-descriptions-item label="核心线程数">{{ monitorInstance?.state?.corePoolSize }}</el-descriptions-item>
          <el-descriptions-item label="最大线程数">{{ monitorInstance?.state?.maximumPoolSize }}</el-descriptions-item>
          <el-descriptions-item label="当前线程数">{{ monitorInstance?.state?.poolSize }}</el-descriptions-item>
          <el-descriptions-item label="活跃线程数">{{ monitorInstance?.state?.activeCount }}</el-descriptions-item>
          <el-descriptions-item label="队列大小">{{ monitorInstance?.state?.queueSize }}</el-descriptions-item>
          <el-descriptions-item label="队列容量">{{ monitorInstance?.state?.queueCapacity || '无限制' }}</el-descriptions-item>
          <el-descriptions-item label="活跃率">
            <span :class="getPercentClass(monitorInstance?.state?.activePercent)">
              {{ formatPercent(monitorInstance?.state?.activePercent) }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="队列使用率">
            <span :class="getPercentClass(monitorInstance?.state?.queueUsagePercent)">
              {{ formatPercent(monitorInstance?.state?.queueUsagePercent) }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatTime(monitorInstance?.state?.timestamp) }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="monitorDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="openConfigDialog(monitorInstance)">配置线程池</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { webContainerApi } from '@/api/index'
import { 
  Refresh, Monitor, TrendCharts, List, Grid, CircleCheck,
  Clock, User, Platform, Promotion, Search
} from '@element-plus/icons-vue'

const summary = ref({})
const allStates = ref([])
const loading = ref(false)
const autoRefresh = ref(true)
const filterApp = ref('')
const searchText = ref('')
let refreshTimer = null

// 计算属性
const appList = computed(() => {
  const apps = new Set(allStates.value.map(s => s.appId))
  return Array.from(apps).sort()
})

const filteredStates = computed(() => {
  let result = allStates.value
  
  if (filterApp.value) {
    result = result.filter(s => s.appId === filterApp.value)
  }
  
  if (searchText.value) {
    const keyword = searchText.value.toLowerCase()
    result = result.filter(s => 
      s.appId?.toLowerCase().includes(keyword) || 
      s.instanceId?.toLowerCase().includes(keyword)
    )
  }
  
  return result
})

// 配置对话框
const configDialogVisible = ref(false)
const configSubmitting = ref(false)
const currentInstance = ref(null)
const configFormRef = ref(null)
const configForm = ref({
  corePoolSize: null,
  maximumPoolSize: null,
  keepAliveTime: null
})

// 监控对话框
const monitorDialogVisible = ref(false)
const monitorInstance = ref(null)

const configRules = {
  corePoolSize: [{ required: true, message: '请输入核心线程数', trigger: 'blur' }],
  maximumPoolSize: [{ required: true, message: '请输入最大线程数', trigger: 'blur' }]
}

// 格式化函数
const formatPercent = (val) => {
  if (val === undefined || val === null) return '0%'
  return `${Math.round(val)}%`
}

const formatInstanceId = (id) => {
  if (!id) return '-'
  if (id.length > 28) {
    return id.substring(0, 12) + '...' + id.substring(id.length - 12)
  }
  return id
}

const formatTime = (timestamp) => {
  if (!timestamp) return '-'
  try {
    let date
    if (Array.isArray(timestamp)) {
      const [year, month, day, hour = 0, minute = 0, second = 0] = timestamp
      date = new Date(year, month - 1, day, hour, minute, second)
    } else if (typeof timestamp === 'string') {
      date = new Date(timestamp)
    } else if (typeof timestamp === 'number' && timestamp > 0) {
      date = new Date(timestamp)
    } else {
      return '-'
    }
    if (isNaN(date.getTime())) return '-'
    return date.toLocaleString('zh-CN', { 
      month: '2-digit', day: '2-digit', 
      hour: '2-digit', minute: '2-digit', second: '2-digit' 
    })
  } catch {
    return '-'
  }
}

const getProgressColor = (percentage) => {
  if (!percentage || percentage < 50) return '#67C23A'
  if (percentage < 80) return '#E6A23C'
  return '#F56C6C'
}

const getPercentClass = (val) => {
  if (!val || val < 50) return 'percent-normal'
  if (val < 80) return 'percent-warning'
  return 'percent-danger'
}

const getContainerTagType = (type) => {
  switch (type) {
    case 'Tomcat': return 'danger'
    case 'Jetty': return 'warning'
    case 'Undertow': return 'success'
    default: return 'info'
  }
}

const getContainerIconClass = (type) => {
  switch (type) {
    case 'Tomcat': return 'icon-red'
    case 'Jetty': return 'icon-yellow'
    case 'Undertow': return 'icon-green'
    default: return 'icon-gray'
  }
}

// 获取数据
const fetchData = async () => {
  try {
    const [summaryData, statesData] = await Promise.all([
      webContainerApi.getSummary(),
      webContainerApi.getAllStates()
    ])
    summary.value = summaryData || {}
    allStates.value = statesData || []
    
    // 更新监控对话框数据
    if (monitorDialogVisible.value && monitorInstance.value) {
      const updated = statesData.find(s => s.instanceId === monitorInstance.value.instanceId)
      if (updated) {
        monitorInstance.value = updated
      }
    }
  } catch (error) {
    console.error('Failed to fetch data:', error)
  }
}

const refreshData = async () => {
  loading.value = true
  try {
    await fetchData()
    ElMessage.success('数据已刷新')
  } finally {
    loading.value = false
  }
}

// 监控对话框
const openMonitorDialog = (row) => {
  monitorInstance.value = row
  monitorDialogVisible.value = true
}

// 配置对话框
const openConfigDialog = (row) => {
  currentInstance.value = row
  configForm.value = {
    corePoolSize: row.state?.corePoolSize || 10,
    maximumPoolSize: row.state?.maximumPoolSize || 200,
    keepAliveTime: row.state?.keepAliveTime || 60
  }
  configDialogVisible.value = true
  monitorDialogVisible.value = false
}

const submitConfig = async () => {
  try { await configFormRef.value?.validate() } catch { return }

  if (configForm.value.corePoolSize > configForm.value.maximumPoolSize) {
    ElMessage.error('核心线程数不能大于最大线程数')
    return
  }

  configSubmitting.value = true
  try {
    const result = await webContainerApi.updateConfig(currentInstance.value.instanceId, {
      corePoolSize: configForm.value.corePoolSize,
      maximumPoolSize: configForm.value.maximumPoolSize,
      keepAliveTime: configForm.value.keepAliveTime
    })

    if (result.success) {
      ElMessage.success('配置更新成功')
      configDialogVisible.value = false
      setTimeout(() => fetchData(), 1000)
    } else {
      ElMessage.error(result.message || '配置更新失败')
    }
  } catch (error) {
    ElMessage.error('配置更新失败: ' + (error.message || '未知错误'))
  } finally {
    configSubmitting.value = false
  }
}

// 自动刷新控制
watch(autoRefresh, (val) => {
  if (val) {
    refreshTimer = setInterval(fetchData, 5000)
  } else {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})

onMounted(() => {
  fetchData()
  if (autoRefresh.value) {
    refreshTimer = setInterval(fetchData, 5000)
  }
})

onUnmounted(() => {
  clearInterval(refreshTimer)
})
</script>

<style scoped>
.web-container-page {
  padding: 24px;
  background: #f5f7fa;
  min-height: calc(100vh - 60px);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-left h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.header-right {
  display: flex;
  align-items: center;
}

.stat-cards {
  margin-bottom: 20px;
}

.stat-card {
  border-radius: 12px;
  border: none;
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}

.icon-purple { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.icon-green { background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%); }
.icon-blue { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); }
.icon-pink { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); }
.icon-orange { background: linear-gradient(135deg, #fa709a 0%, #fee140 100%); }
.icon-red { background: linear-gradient(135deg, #ff6b6b 0%, #ee5a24 100%); }
.icon-yellow { background: linear-gradient(135deg, #f9d423 0%, #ff4e50 100%); }
.icon-gray { background: linear-gradient(135deg, #bdc3c7 0%, #2c3e50 100%); }

.stat-info { flex: 1; }
.stat-value { font-size: 28px; font-weight: 700; color: #303133; }
.stat-label { font-size: 14px; color: #909399; margin-top: 4px; }

.percent-normal { color: #67C23A; }
.percent-warning { color: #E6A23C; }
.percent-danger { color: #F56C6C; }

.app-list-card {
  border-radius: 12px;
  border: none;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 16px;
  color: #303133;
}

.header-actions {
  display: flex;
  align-items: center;
}

.app-cell {
  display: flex;
  align-items: center;
}

.instance-id {
  font-family: 'JetBrains Mono', 'Consolas', monospace;
  font-size: 13px;
  color: #606266;
}

.status-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-dot.online {
  background: #67C23A;
  box-shadow: 0 0 8px rgba(103, 194, 58, 0.6);
}

.status-dot.offline {
  background: #F56C6C;
}

.status-online { color: #67C23A; font-weight: 500; }
.status-offline { color: #F56C6C; font-weight: 500; }

.pool-info {
  font-size: 15px;
  font-weight: 600;
}

.pool-current { color: #409EFF; }
.pool-divider { color: #C0C4CC; margin: 0 3px; }
.pool-max { color: #909399; }
.pool-label { font-size: 12px; color: #C0C4CC; margin-top: 2px; }

.metric-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 0 8px;
}

.metric-value {
  font-size: 14px;
  font-weight: 600;
  text-align: center;
}

.queue-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.queue-value {
  font-size: 18px;
  font-weight: 600;
  color: #606266;
}

.time-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  color: #909399;
}

.action-buttons {
  display: flex;
  justify-content: center;
  gap: 8px;
}

.form-hint {
  margin-left: 16px;
  color: #909399;
  font-size: 13px;
}

.monitor-content {
  padding: 8px;
}

.monitor-stats {
  margin-bottom: 16px;
}

.monitor-stat-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 10px;
  height: 70px;
}

.stat-icon-sm {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}

.stat-text { flex: 1; min-width: 0; }
.stat-value-sm { 
  font-size: 15px; 
  font-weight: 600; 
  color: #303133; 
  white-space: nowrap; 
  overflow: hidden; 
  text-overflow: ellipsis; 
}
.stat-label-sm { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
