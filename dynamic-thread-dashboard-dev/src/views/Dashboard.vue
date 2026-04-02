<template>
  <div class="dashboard">
    <div class="page-header">
      <h2>系统概览</h2>
      <p>动态线程池运行状态监控</p>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stat-row">
      <el-col :span="6">
        <div class="card stat-card info">
          <div class="stat-icon">
            <el-icon :size="32"><Grid /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.appCount }}</div>
            <div class="stat-label">应用数</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="card stat-card success">
          <div class="stat-icon">
            <el-icon :size="32"><Monitor /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.instanceCount }}</div>
            <div class="stat-label">实例数</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="card stat-card warning">
          <div class="stat-icon">
            <el-icon :size="32"><Operation /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.threadPoolCount }}</div>
            <div class="stat-label">线程池数</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="card stat-card primary">
          <div class="stat-icon">
            <el-icon :size="32"><User /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.totalActiveThreads }}</div>
            <div class="stat-label">活跃线程</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 线程池状态表格 -->
    <div class="card" style="margin-top: 20px;">
      <div class="card-header">
        <span class="title">线程池状态</span>
        <el-button type="primary" :icon="Refresh" @click="fetchData" :loading="loading">
          刷新
        </el-button>
      </div>
      <el-empty v-if="!loading && threadPools.length === 0" description="暂无线程池数据" />
      <el-table v-else :data="threadPools" v-loading="loading" style="width: 100%; margin-top: 16px;">
        <el-table-column prop="appId" label="应用" width="150" />
        <el-table-column prop="instanceId" label="实例" width="200">
          <template #default="{ row }">
            <el-tooltip :content="row.instanceId" placement="top">
              <span class="instance-id">{{ formatInstanceId(row.instanceId) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="threadPoolId" label="线程池ID" width="180" />
        <el-table-column label="线程数" width="160">
          <template #default="{ row }">
            <div class="thread-stats">
              <span class="active">{{ row.activeCount }}</span>
              <span class="separator">/</span>
              <span class="current">{{ row.poolSize }}</span>
              <span class="separator">/</span>
              <span class="max">{{ row.maximumPoolSize }}</span>
            </div>
            <div class="sub-text">活跃 / 当前 / 最大</div>
          </template>
        </el-table-column>
        <el-table-column label="队列" width="180">
          <template #default="{ row }">
            <el-progress 
              :percentage="Math.round(row.queueUsagePercent || 0)" 
              :color="getQueueColor(row.queueUsagePercent)"
              :stroke-width="10"
            />
            <div class="sub-text">{{ row.queueSize }} / {{ row.queueCapacity }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="completedTaskCount" label="已完成" width="120">
          <template #default="{ row }">
            {{ formatNumber(row.completedTaskCount) }}
          </template>
        </el-table-column>
        <el-table-column prop="rejectedCount" label="拒绝" width="80">
          <template #default="{ row }">
            <el-tag :type="row.rejectedCount > 0 ? 'danger' : 'success'" size="small">
              {{ row.rejectedCount }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewDetail(row)">详情</el-button>
            <el-button type="primary" link @click="editConfig(row)">配置</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 配置对话框 -->
    <el-dialog v-model="dialogVisible" title="修改线程池配置" width="500px">
      <el-form :model="editForm" label-width="120px" :rules="rules" ref="formRef">
        <el-form-item label="线程池ID">
          <el-input v-model="editForm.threadPoolId" disabled />
        </el-form-item>
        <el-form-item label="核心线程数" prop="corePoolSize">
          <el-input-number v-model="editForm.corePoolSize" :min="1" :max="500" style="width: 100%" />
        </el-form-item>
        <el-form-item label="最大线程数" prop="maximumPoolSize">
          <el-input-number v-model="editForm.maximumPoolSize" :min="1" :max="1000" style="width: 100%" />
        </el-form-item>
        <el-form-item label="队列容量" prop="queueCapacity">
          <el-input-number v-model="editForm.queueCapacity" :min="1" :max="100000" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitConfig" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, Grid, Monitor, Operation, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { threadPoolApi } from '@/api'

const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const threadPools = ref([])
const stats = reactive({
  appCount: 0,
  instanceCount: 0,
  threadPoolCount: 0,
  totalActiveThreads: 0
})
const editForm = reactive({
  instanceId: '',
  threadPoolId: '',
  corePoolSize: 10,
  maximumPoolSize: 20,
  queueCapacity: 1000
})

const rules = {
  corePoolSize: [{ required: true, message: '请输入核心线程数', trigger: 'blur' }],
  maximumPoolSize: [{ required: true, message: '请输入最大线程数', trigger: 'blur' }],
  queueCapacity: [{ required: true, message: '请输入队列容量', trigger: 'blur' }]
}

let refreshTimer = null

const fetchData = async () => {
  loading.value = true
  try {
    const res = await threadPoolApi.states()
    
    // 将分层数据转换为扁平列表
    const pools = []
    const apps = new Set()
    const instances = new Set()
    
    if (res.data) {
      for (const [appId, appInstances] of Object.entries(res.data)) {
        apps.add(appId)
        for (const [instanceId, states] of Object.entries(appInstances)) {
          instances.add(instanceId)
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
    
    // 计算统计数据
    stats.appCount = apps.size
    stats.instanceCount = instances.size
    stats.threadPoolCount = pools.length
    stats.totalActiveThreads = pools.reduce((sum, p) => sum + (p.activeCount || 0), 0)
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const getQueueColor = (percent) => {
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
  // 截取显示，如果太长则缩略
  if (instanceId.length > 25) {
    return instanceId.substring(0, 22) + '...'
  }
  return instanceId
}

const viewDetail = (row) => {
  router.push(`/thread-pools/${row.threadPoolId}`)
}

const editConfig = (row) => {
  editForm.instanceId = row.instanceId
  editForm.threadPoolId = row.threadPoolId
  editForm.corePoolSize = row.corePoolSize
  editForm.maximumPoolSize = row.maximumPoolSize
  editForm.queueCapacity = row.queueCapacity
  dialogVisible.value = true
}

const submitConfig = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  
  submitting.value = true
  try {
    await threadPoolApi.updateConfig(editForm.instanceId, {
      threadPoolId: editForm.threadPoolId,
      corePoolSize: editForm.corePoolSize,
      maximumPoolSize: editForm.maximumPoolSize,
      queueCapacity: editForm.queueCapacity
    })
    ElMessage.success('配置更新请求已发送')
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
  // 每 5 秒自动刷新
  refreshTimer = setInterval(fetchData, 5000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})
</script>

<style lang="scss" scoped>
.stat-row {
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  
  .stat-icon {
    width: 60px;
    height: 60px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(64, 158, 255, 0.1);
    color: #409eff;
  }
  
  .stat-content {
    flex: 1;
    
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
  
  &.success {
    .stat-icon {
      background: rgba(103, 194, 58, 0.1);
      color: #67c23a;
    }
    .stat-value { color: #67c23a; }
  }
  
  &.warning {
    .stat-icon {
      background: rgba(230, 162, 60, 0.1);
      color: #e6a23c;
    }
    .stat-value { color: #e6a23c; }
  }
  
  &.primary {
    .stat-icon {
      background: rgba(64, 158, 255, 0.1);
      color: #409eff;
    }
    .stat-value { color: #409eff; }
  }
  
  &.info {
    .stat-icon {
      background: rgba(144, 147, 153, 0.1);
      color: #909399;
    }
    .stat-value { color: #409eff; }
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  
  .title {
    font-size: 16px;
    font-weight: 600;
  }
}

.instance-id {
  font-family: monospace;
  font-size: 12px;
  color: #606266;
}

.thread-stats {
  font-family: monospace;
  
  .active { color: #67c23a; font-weight: 600; }
  .current { color: #409eff; }
  .max { color: #909399; }
  .separator { color: #dcdfe6; margin: 0 2px; }
}

.sub-text {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
