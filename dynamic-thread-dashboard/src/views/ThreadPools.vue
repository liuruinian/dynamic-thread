<template>
  <div class="thread-pools">
    <div class="page-header">
      <h2>线程池管理</h2>
      <p>查看和管理所有注册的动态线程池</p>
    </div>

    <!-- 快速筛选 -->
    <div class="card filter-card">
      <el-row :gutter="20" align="middle">
        <el-col :span="6">
          <el-select v-model="filterApp" placeholder="选择应用" clearable style="width: 100%">
            <el-option v-for="app in apps" :key="app" :label="app" :value="app" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-input v-model="searchKeyword" placeholder="搜索线程池ID..." :prefix-icon="Search" clearable />
        </el-col>
        <el-col :span="12" style="text-align: right;">
          <el-switch v-model="autoRefresh" active-text="自动刷新" style="margin-right: 16px;" />
          <el-button type="primary" :icon="Refresh" @click="fetchData" :loading="loading">刷新</el-button>
        </el-col>
      </el-row>
    </div>

    <!-- 线程池列表 -->
    <el-empty v-if="!loading && filteredPools.length === 0" description="暂无线程池数据" style="margin-top: 40px;" />
    
    <el-row :gutter="20">
      <el-col :span="24" v-for="pool in filteredPools" :key="`${pool.instanceId}-${pool.threadPoolId}`">
        <div class="card pool-card">
          <div class="pool-header">
            <div class="pool-title">
              <el-icon :size="24"><Operation /></el-icon>
              <span class="pool-name">{{ pool.threadPoolId }}</span>
              <el-tag :type="pool.activeCount > 0 ? 'success' : 'info'" size="small">
                {{ pool.activeCount > 0 ? '运行中' : '空闲' }}
              </el-tag>
              <el-tag type="info" size="small" class="app-tag">{{ pool.appId }}</el-tag>
            </div>
            <div class="pool-actions">
              <el-button type="primary" :icon="View" @click="viewDetail(pool)">查看详情</el-button>
              <el-button :icon="Edit" @click="editPool(pool)">修改配置</el-button>
            </div>
          </div>

          <el-row :gutter="20" class="pool-stats">
            <el-col :span="4">
              <div class="stat-item">
                <div class="stat-value">{{ pool.corePoolSize }}</div>
                <div class="stat-label">核心线程</div>
              </div>
            </el-col>
            <el-col :span="4">
              <div class="stat-item">
                <div class="stat-value">{{ pool.maximumPoolSize }}</div>
                <div class="stat-label">最大线程</div>
              </div>
            </el-col>
            <el-col :span="4">
              <div class="stat-item">
                <div class="stat-value" :class="{ active: pool.activeCount > 0 }">
                  {{ pool.activeCount }}
                </div>
                <div class="stat-label">活跃线程</div>
              </div>
            </el-col>
            <el-col :span="4">
              <div class="stat-item">
                <div class="stat-value">{{ pool.queueSize }} / {{ pool.queueCapacity }}</div>
                <div class="stat-label">队列使用</div>
              </div>
            </el-col>
            <el-col :span="4">
              <div class="stat-item">
                <div class="stat-value">{{ formatNumber(pool.completedTaskCount) }}</div>
                <div class="stat-label">完成任务</div>
              </div>
            </el-col>
            <el-col :span="4">
              <div class="stat-item">
                <div class="stat-value" :class="{ danger: pool.rejectedCount > 0 }">
                  {{ pool.rejectedCount }}
                </div>
                <div class="stat-label">拒绝任务</div>
              </div>
            </el-col>
          </el-row>

          <div class="pool-progress">
            <div class="progress-item">
              <span class="progress-label">线程使用率</span>
              <el-progress 
                :percentage="Math.round(pool.activePercent || 0)" 
                :color="getProgressColor(pool.activePercent)"
              />
            </div>
            <div class="progress-item">
              <span class="progress-label">队列使用率</span>
              <el-progress 
                :percentage="Math.round(pool.queueUsagePercent || 0)" 
                :color="getProgressColor(pool.queueUsagePercent)"
              />
            </div>
          </div>

          <div class="pool-info">
            <el-descriptions :column="4" size="small">
              <el-descriptions-item label="实例">{{ formatInstanceId(pool.instanceId) }}</el-descriptions-item>
              <el-descriptions-item label="拒绝策略">{{ pool.rejectedHandler || 'AbortPolicy' }}</el-descriptions-item>
              <el-descriptions-item label="存活时间">{{ pool.keepAliveTime }}s</el-descriptions-item>
              <el-descriptions-item label="核心超时">{{ pool.allowCoreThreadTimeOut ? '是' : '否' }}</el-descriptions-item>
            </el-descriptions>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" title="修改线程池配置" width="600px">
      <el-form :model="editForm" label-width="120px" :rules="rules" ref="formRef">
        <el-form-item label="线程池ID">
          <el-input v-model="editForm.threadPoolId" disabled />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="核心线程数" prop="corePoolSize">
              <el-input-number v-model="editForm.corePoolSize" :min="1" :max="500" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大线程数" prop="maximumPoolSize">
              <el-input-number v-model="editForm.maximumPoolSize" :min="1" :max="1000" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
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
        <el-alert type="info" :closable="false" style="margin-top: 8px;">
          <template #title>
            配置将推送到实例: {{ editForm.instanceId }}
          </template>
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitConfig" :loading="submitting">保存配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Edit, View, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { threadPoolApi } from '@/api'

const router = useRouter()
const threadPools = ref([])
const apps = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const loading = ref(false)
const autoRefresh = ref(true)
const formRef = ref(null)
const filterApp = ref('')
const searchKeyword = ref('')

const editForm = reactive({
  instanceId: '',
  threadPoolId: '',
  corePoolSize: 10,
  maximumPoolSize: 20,
  queueCapacity: 1000,
  rejectedHandler: 'AbortPolicy'
})

const rules = {
  corePoolSize: [
    { required: true, message: '请输入核心线程数', trigger: 'blur' },
    { type: 'number', min: 1, message: '核心线程数最小为1', trigger: 'blur' }
  ],
  maximumPoolSize: [
    { required: true, message: '请输入最大线程数', trigger: 'blur' },
    { type: 'number', min: 1, message: '最大线程数最小为1', trigger: 'blur' }
  ],
  queueCapacity: [
    { required: true, message: '请输入队列容量', trigger: 'blur' },
    { type: 'number', min: 1, message: '队列容量最小为1', trigger: 'blur' }
  ]
}

// 筛选后的线程池列表
const filteredPools = computed(() => {
  return threadPools.value.filter(pool => {
    const appMatch = !filterApp.value || pool.appId === filterApp.value
    const keywordMatch = !searchKeyword.value || 
      pool.threadPoolId.toLowerCase().includes(searchKeyword.value.toLowerCase())
    return appMatch && keywordMatch
  })
})

let refreshTimer = null

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
            pools.push({
              appId,
              instanceId,
              ...state
            })
          }
        }
      }
    }
    
    threadPools.value = pools
    apps.value = Array.from(appSet)
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const getProgressColor = (percent) => {
  if (percent >= 80) return '#f56c6c'
  if (percent >= 50) return '#e6a23c'
  return '#67c23a'
}

const formatNumber = (num) => {
  if (!num) return '0'
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toString()
}

const formatInstanceId = (instanceId) => {
  if (!instanceId) return ''
  if (instanceId.length > 30) {
    return instanceId.substring(0, 27) + '...'
  }
  return instanceId
}

const viewDetail = (pool) => {
  router.push(`/thread-pools/${pool.threadPoolId}`)
}

const editPool = (pool) => {
  editForm.instanceId = pool.instanceId
  editForm.threadPoolId = pool.threadPoolId
  editForm.corePoolSize = pool.corePoolSize
  editForm.maximumPoolSize = pool.maximumPoolSize
  editForm.queueCapacity = pool.queueCapacity
  editForm.rejectedHandler = pool.rejectedHandler || 'AbortPolicy'
  dialogVisible.value = true
}

const submitConfig = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  
  // 验证核心线程数不能大于最大线程数
  if (editForm.corePoolSize > editForm.maximumPoolSize) {
    ElMessage.warning('核心线程数不能大于最大线程数')
    return
  }
  
  submitting.value = true
  try {
    await threadPoolApi.updateConfig(editForm.instanceId, {
      threadPoolId: editForm.threadPoolId,
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

// 监听自动刷新开关
watch(autoRefresh, (val) => {
  if (val) {
    refreshTimer = setInterval(fetchData, 5000)
  } else if (refreshTimer) {
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
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style lang="scss" scoped>
.filter-card {
  margin-bottom: 20px;
}

.pool-card {
  margin-bottom: 20px;
  transition: all 0.3s;
  
  &:hover {
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  }
}

.pool-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  
  .pool-title {
    display: flex;
    align-items: center;
    gap: 12px;
    
    .pool-name {
      font-size: 18px;
      font-weight: 600;
    }
    
    .app-tag {
      margin-left: 8px;
    }
  }
}

.pool-stats {
  margin-bottom: 20px;
  
  .stat-item {
    text-align: center;
    padding: 16px;
    background: linear-gradient(135deg, #f5f7fa 0%, #e8eaed 100%);
    border-radius: 8px;
    
    .stat-value {
      font-size: 24px;
      font-weight: 600;
      color: #303133;
      
      &.active {
        color: #67c23a;
      }
      
      &.danger {
        color: #f56c6c;
      }
    }
    
    .stat-label {
      font-size: 12px;
      color: #909399;
      margin-top: 8px;
    }
  }
}

.pool-progress {
  display: flex;
  gap: 40px;
  margin-bottom: 20px;
  
  .progress-item {
    flex: 1;
    
    .progress-label {
      font-size: 14px;
      color: #606266;
      margin-bottom: 8px;
      display: block;
    }
  }
}

.pool-info {
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}
</style>
