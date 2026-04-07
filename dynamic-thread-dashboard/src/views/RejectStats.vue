<template>
  <div class="reject-stats">
    <div class="page-header">
      <h2>拒绝策略统计</h2>
      <p>实时监控线程池任务拒绝情况与告警</p>
    </div>

    <!-- 统计概览 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <div class="stat-card danger">
          <div class="stat-icon">
            <el-icon :size="32"><WarningFilled /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ statistics.globalRejectedCount || 0 }}</div>
            <div class="stat-label">总拒绝任务数</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card primary">
          <div class="stat-icon">
            <el-icon :size="32"><DataBoard /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ statistics.poolCount || 0 }}</div>
            <div class="stat-label">监控线程池数</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card warning">
          <div class="stat-icon">
            <el-icon :size="32"><Document /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ totalRecords }}</div>
            <div class="stat-label">最近拒绝记录</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card" :class="statistics.instantAlarmEnabled ? 'success' : 'info'">
          <div class="stat-icon">
            <el-icon :size="32"><Bell /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ statistics.instantAlarmEnabled ? '已启用' : '已禁用' }}</div>
            <div class="stat-label">即时告警</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <!-- 各线程池拒绝统计 -->
      <el-col :span="12">
        <div class="card">
          <div class="card-header">
            <h3 class="section-title">各线程池拒绝统计</h3>
            <el-space>
              <el-button type="danger" text :icon="Delete" @click="resetAll" :disabled="!hasRecords">
                重置全部
              </el-button>
              <el-button :icon="Refresh" @click="fetchData" :loading="loading">刷新</el-button>
            </el-space>
          </div>

          <el-table :data="poolStatsList" v-loading="loading" style="width: 100%" max-height="400">
            <el-table-column prop="threadPoolId" label="线程池" min-width="150" show-overflow-tooltip />
            <el-table-column prop="count" label="拒绝次数" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.count > 0 ? 'danger' : 'success'" size="small">
                  {{ row.count }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="percentage" label="占比" width="120">
              <template #default="{ row }">
                <el-progress 
                  :percentage="row.percentage" 
                  :color="row.percentage > 50 ? '#f56c6c' : row.percentage > 20 ? '#e6a23c' : '#67c23a'"
                  :stroke-width="8"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="viewPoolRecords(row)">
                  查看记录
                </el-button>
                <el-button type="danger" link size="small" @click="resetPool(row.threadPoolId)" :disabled="row.count === 0">
                  重置
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!loading && poolStatsList.length === 0" description="暂无拒绝统计" />
        </div>
      </el-col>

      <!-- 即时告警设置 -->
      <el-col :span="12">
        <div class="card">
          <h3 class="section-title">即时告警设置</h3>
          
          <div class="alarm-setting">
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-label">即时拒绝告警</span>
                <span class="setting-desc">任务被拒绝时立即触发告警通知</span>
              </div>
              <el-switch 
                v-model="instantAlarmEnabled" 
                @change="toggleInstantAlarm"
                active-text="启用"
                inactive-text="禁用"
              />
            </div>
          </div>

          <el-divider />

          <h4 style="margin-bottom: 16px; color: #303133;">拒绝策略说明</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="AbortPolicy">
              <el-tag type="danger" size="small">抛出异常</el-tag>
              抛出 RejectedExecutionException 中断任务
            </el-descriptions-item>
            <el-descriptions-item label="CallerRunsPolicy">
              <el-tag type="warning" size="small">调用者执行</el-tag>
              由提交任务的线程直接执行该任务
            </el-descriptions-item>
            <el-descriptions-item label="DiscardPolicy">
              <el-tag type="info" size="small">静默丢弃</el-tag>
              直接丢弃任务，不抛出异常
            </el-descriptions-item>
            <el-descriptions-item label="DiscardOldestPolicy">
              <el-tag type="warning" size="small">丢弃最旧</el-tag>
              丢弃队列中最旧的任务，尝试重新提交
            </el-descriptions-item>
            <el-descriptions-item label="RetryBufferPolicy">
              <el-tag type="success" size="small">暂存重投递</el-tag>
              暂存被拒绝任务，线程池空闲时自动重投递，最终兜底由主线程执行
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </el-col>
    </el-row>

    <!-- 最近拒绝记录 -->
    <div class="card" style="margin-top: 20px;">
      <div class="card-header">
        <h3 class="section-title">最近拒绝记录</h3>
        <el-space>
          <el-select v-model="filterPool" placeholder="筛选线程池" clearable size="small" style="width: 200px;">
            <el-option v-for="pool in poolStatsList" :key="pool.threadPoolId" 
                       :value="pool.threadPoolId" :label="pool.threadPoolId" />
          </el-select>
          <el-switch v-model="autoRefresh" active-text="自动刷新" size="small" />
        </el-space>
      </div>

      <el-table :data="filteredRecords" v-loading="loading" style="width: 100%" 
                max-height="500" :row-class-name="getRowClassName">
        <el-table-column width="60">
          <template #default="{ row }">
            <el-icon :size="20" color="#f56c6c"><CircleCloseFilled /></el-icon>
          </template>
        </el-table-column>
        <el-table-column prop="threadPoolId" label="线程池" width="150" show-overflow-tooltip />
        <el-table-column prop="appId" label="应用" width="150" show-overflow-tooltip />
        <el-table-column prop="instanceId" label="实例" min-width="180" show-overflow-tooltip />
        <el-table-column prop="rejectedPolicy" label="拒绝策略" width="130">
          <template #default="{ row }">
            <el-tag :type="getPolicyType(row.rejectedPolicy)" size="small">
              {{ row.rejectedPolicy }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="池状态" width="120">
          <template #default="{ row }">
            <span>{{ row.activeCount }}/{{ row.maximumPoolSize }}</span>
          </template>
        </el-table-column>
        <el-table-column label="队列状态" width="130">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.queueSize >= row.queueCapacity }">
              {{ row.queueSize }}/{{ row.queueCapacity }} ({{ row.queueUsagePercent }}%)
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="timestamp" label="时间" width="180">
          <template #default="{ row }">
            {{ formatTimestamp(row.timestamp) }}
          </template>
        </el-table-column>
        <el-table-column prop="rejectedCount" label="拒绝数" width="80" align="center">
          <template #default="{ row }">
            <el-tag type="danger" size="small">{{ row.rejectedCount }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && allRecords.length === 0" description="暂无拒绝记录" />
    </div>

    <!-- 查看线程池记录对话框 -->
    <el-dialog v-model="poolDialogVisible" :title="`${selectedPool} 拒绝详情`" width="900px">
      <el-table :data="poolRecords" v-loading="poolRecordsLoading" max-height="500">
        <el-table-column prop="appId" label="应用" width="150" show-overflow-tooltip />
        <el-table-column prop="instanceId" label="实例" min-width="180" show-overflow-tooltip />
        <el-table-column prop="rejectedPolicy" label="策略" width="120">
          <template #default="{ row }">
            <el-tag :type="getPolicyType(row.rejectedPolicy)" size="small">
              {{ row.rejectedPolicy }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="池状态" width="100">
          <template #default="{ row }">
            {{ row.activeCount }}/{{ row.maximumPoolSize }}
          </template>
        </el-table-column>
        <el-table-column label="队列" width="100">
          <template #default="{ row }">
            {{ row.queueSize }}/{{ row.queueCapacity }}
          </template>
        </el-table-column>
        <el-table-column prop="rejectedCount" label="拒绝数" width="90" align="center">
          <template #default="{ row }">
            <el-tag type="danger" size="small">{{ row.rejectedCount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="timestamp" label="时间" width="180">
          <template #default="{ row }">
            {{ formatTimestamp(row.timestamp) }}
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="poolDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Refresh, Delete, WarningFilled, DataBoard, Document, Bell, CircleCloseFilled 
} from '@element-plus/icons-vue'
import { rejectApi } from '@/api'

// 状态
const loading = ref(false)
const statistics = ref({})
const allRecords = ref([])
const filterPool = ref('')
const autoRefresh = ref(false)
const instantAlarmEnabled = ref(true)

// 线程池记录对话框
const poolDialogVisible = ref(false)
const selectedPool = ref('')
const poolRecords = ref([])
const poolRecordsLoading = ref(false)

let refreshTimer = null

// 计算属性
const poolStatsList = computed(() => {
  const poolStats = statistics.value.poolStatistics || {}
  const total = statistics.value.globalRejectedCount || 1
  
  return Object.entries(poolStats).map(([threadPoolId, count]) => ({
    threadPoolId,
    count,
    percentage: Math.round((count / total) * 100)
  })).sort((a, b) => b.count - a.count)
})

const totalRecords = computed(() => allRecords.value.length)

const hasRecords = computed(() => (statistics.value.globalRejectedCount || 0) > 0)

const filteredRecords = computed(() => {
  if (!filterPool.value) {
    return allRecords.value
  }
  return allRecords.value.filter(r => r.threadPoolId === filterPool.value)
})

// 方法
const fetchData = async () => {
  loading.value = true
  try {
    const [statsRes, recordsRes] = await Promise.all([
      rejectApi.getStatistics(),
      rejectApi.getRecords(100)
    ])
    
    statistics.value = statsRes
    instantAlarmEnabled.value = statsRes.instantAlarmEnabled !== false
    
    // 记录已经是数组格式
    allRecords.value = Array.isArray(recordsRes.records) 
      ? recordsRes.records 
      : []
  } catch (e) {
    console.error('Failed to fetch rejection data:', e)
  } finally {
    loading.value = false
  }
}

const toggleInstantAlarm = async (enabled) => {
  try {
    await rejectApi.toggleInstantAlarm(enabled)
    // 同步更新 statistics 中的状态，保持一致性
    statistics.value.instantAlarmEnabled = enabled
    ElMessage.success(enabled ? '即时告警已启用' : '即时告警已禁用')
  } catch (e) {
    // 恢复开关状态
    instantAlarmEnabled.value = !enabled
    // 全局拦截器已显示错误，此处不再重复显示
  }
}

const resetPool = async (threadPoolId) => {
  try {
    await ElMessageBox.confirm(
      `确定要重置线程池 "${threadPoolId}" 的拒绝统计吗？`,
      '确认重置',
      { type: 'warning' }
    )
    await rejectApi.resetPool(threadPoolId)
    ElMessage.success('统计已重置')
    fetchData()
  } catch (e) {
    if (e !== 'cancel') {
      console.error(e)
    }
  }
}

const resetAll = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要重置所有拒绝统计吗？此操作不可恢复。',
      '确认重置',
      { type: 'warning' }
    )
    await rejectApi.resetAll()
    ElMessage.success('所有统计已重置')
    fetchData()
  } catch (e) {
    if (e !== 'cancel') {
      console.error(e)
    }
  }
}

const viewPoolRecords = async (pool) => {
  selectedPool.value = pool.threadPoolId
  poolDialogVisible.value = true
  poolRecordsLoading.value = true
  
  try {
    const res = await rejectApi.getPoolRecords(pool.threadPoolId, 100)
    poolRecords.value = Array.isArray(res.records) ? res.records : []
  } catch (e) {
    console.error(e)
    poolRecords.value = []
  } finally {
    poolRecordsLoading.value = false
  }
}

const formatClassName = (className) => {
  if (!className) return 'N/A'
  const parts = className.split('.')
  return parts[parts.length - 1]
}

const formatTimestamp = (timestamp) => {
  if (!timestamp) return '-'
  // Handle Java LocalDateTime array format [year, month, day, hour, minute, second, nano]
  if (Array.isArray(timestamp)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = timestamp
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')} ${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:${String(second).padStart(2, '0')}`
  }
  // Handle string format
  if (typeof timestamp === 'string') {
    return timestamp.replace('T', ' ').substring(0, 19)
  }
  return String(timestamp)
}

const getPolicyType = (policy) => {
  if (!policy) return 'info'
  if (policy.includes('Abort')) return 'danger'
  if (policy.includes('Caller')) return 'warning'
  if (policy.includes('RetryBuffer')) return 'success'
  if (policy.includes('Discard')) return 'info'
  return 'info'
}

const getRowClassName = ({ row }) => {
  return row.executorShutdown ? 'row-shutdown' : ''
}

// 自动刷新
watch(autoRefresh, (enabled) => {
  if (enabled) {
    refreshTimer = setInterval(fetchData, 5000)
  } else if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})

onMounted(() => {
  fetchData()
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})
</script>

<style lang="scss" scoped>
.reject-stats {
  .page-header {
    margin-bottom: 24px;
    
    h2 {
      margin: 0 0 8px;
      font-size: 24px;
      font-weight: 600;
      color: #303133;
    }
    
    p {
      margin: 0;
      color: #909399;
    }
  }

  .stats-row {
    margin-bottom: 20px;
  }

  .stat-card {
    background: #fff;
    border-radius: 8px;
    padding: 20px;
    display: flex;
    align-items: center;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
    
    .stat-icon {
      width: 60px;
      height: 60px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-right: 16px;
    }
    
    .stat-content {
      flex: 1;
      
      .stat-value {
        font-size: 28px;
        font-weight: 700;
        line-height: 1.2;
      }
      
      .stat-label {
        font-size: 14px;
        color: #909399;
        margin-top: 4px;
      }
    }
    
    &.danger {
      .stat-icon { background: #fef0f0; color: #f56c6c; }
      .stat-value { color: #f56c6c; }
    }
    
    &.primary {
      .stat-icon { background: #ecf5ff; color: #409eff; }
      .stat-value { color: #409eff; }
    }
    
    &.warning {
      .stat-icon { background: #fdf6ec; color: #e6a23c; }
      .stat-value { color: #e6a23c; }
    }
    
    &.success {
      .stat-icon { background: #f0f9eb; color: #67c23a; }
      .stat-value { color: #67c23a; }
    }
    
    &.info {
      .stat-icon { background: #f4f4f5; color: #909399; }
      .stat-value { color: #909399; }
    }
  }

  .card {
    background: #fff;
    border-radius: 8px;
    padding: 20px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
  }

  .section-title {
    margin: 0 0 16px;
    font-size: 16px;
    font-weight: 600;
    color: #303133;
  }

  .alarm-setting {
    .setting-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 16px 0;
      
      .setting-info {
        .setting-label {
          display: block;
          font-weight: 500;
          color: #303133;
          margin-bottom: 4px;
        }
        
        .setting-desc {
          font-size: 13px;
          color: #909399;
        }
      }
    }
  }

  .task-class {
    font-family: 'Consolas', 'Monaco', monospace;
    font-size: 12px;
    background: #f5f7fa;
    padding: 2px 6px;
    border-radius: 4px;
    color: #606266;
  }

  .text-danger {
    color: #f56c6c;
    font-weight: 600;
  }

  :deep(.row-shutdown) {
    background-color: #fef0f0 !important;
  }

  :deep(.el-table) {
    .el-table__row {
      &:hover {
        background-color: #f5f7fa;
      }
    }
  }
}
</style>
