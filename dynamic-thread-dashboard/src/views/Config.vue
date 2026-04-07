<template>
  <div class="config">
    <div class="page-header">
      <h2>配置管理</h2>
      <p>管理线程池配置和系统设置</p>
    </div>

    <el-row :gutter="20">
      <el-col :span="16">
        <div class="card">
          <h3 class="section-title">线程池配置</h3>
          <el-empty v-if="!loading && configs.length === 0" description="暂无线程池数据" />
          <el-table v-else :data="configs" v-loading="loading" style="width: 100%">
            <el-table-column prop="appId" label="应用" width="120" />
            <el-table-column prop="threadPoolId" label="线程池ID" width="160" />
            <el-table-column prop="corePoolSize" label="核心线程" width="90" />
            <el-table-column prop="maximumPoolSize" label="最大线程" width="90" />
            <el-table-column prop="queueCapacity" label="队列容量" width="90" />
            <el-table-column prop="keepAliveTime" label="存活时间(s)" width="100" />
            <el-table-column prop="rejectedHandler" label="拒绝策略" />
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="editConfig(row)">
                  编辑
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="card" style="margin-top: 20px;">
          <h3 class="section-title">服务状态</h3>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="服务状态">
              <el-tag :type="health.status === 'UP' ? 'success' : 'danger'">
                {{ health.status || 'UNKNOWN' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="应用数量">
              {{ stats.appCount }}
            </el-descriptions-item>
            <el-descriptions-item label="实例数量">
              {{ stats.instanceCount }}
            </el-descriptions-item>
            <el-descriptions-item label="线程池数量">
              {{ configs.length }}
            </el-descriptions-item>
            <el-descriptions-item label="连接客户端">
              {{ health.clientCount || 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="最后刷新">
              {{ lastUpdated || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </el-col>

      <el-col :span="8">
        <div class="card quick-actions">
          <h3 class="section-title">快捷操作</h3>
          <div class="action-buttons">
            <el-button 
              type="primary" 
              :icon="Refresh" 
              @click="refreshAll" 
              :loading="loading"
              style="width: 100%; height: 44px;"
            >
              刷新所有配置
            </el-button>
            <el-button 
              type="success" 
              :icon="Download" 
              @click="exportConfig"
              style="width: 100%; height: 44px;"
            >
              导出配置
            </el-button>
          </div>
        </div>

        <div class="card" style="margin-top: 20px;">
          <h3 class="section-title">说明</h3>
          <el-alert type="info" :closable="false">
            <template #title>独立部署模式</template>
            <p style="margin-top: 8px; font-size: 13px; color: #606266;">
              Dashboard 通过 Netty 长连接与各应用实例通信，配置更新将实时推送到目标实例。
            </p>
          </el-alert>
        </div>
      </el-col>
    </el-row>

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" title="编辑线程池配置" width="500px">
      <el-form :model="editForm" label-width="100px" :rules="rules" ref="formRef">
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
        <el-form-item label="拒绝策略">
          <el-select v-model="editForm.rejectedHandler" style="width: 100%">
            <el-option label="AbortPolicy - 抛出异常" value="AbortPolicy" />
            <el-option label="CallerRunsPolicy - 调用者执行" value="CallerRunsPolicy" />
            <el-option label="DiscardOldestPolicy - 丢弃最旧" value="DiscardOldestPolicy" />
            <el-option label="DiscardPolicy - 静默丢弃" value="DiscardPolicy" />
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
        <el-button type="primary" @click="submitConfig" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Refresh, Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { threadPoolApi } from '@/api'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const configs = ref([])
const health = ref({})
const lastUpdated = ref('')
const stats = reactive({
  appCount: 0,
  instanceCount: 0
})

const editForm = reactive({
  instanceId: '',
  threadPoolId: '',
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

const fetchData = async () => {
  loading.value = true
  try {
    const [statesRes, healthRes] = await Promise.all([
      threadPoolApi.states(),
      threadPoolApi.health()
    ])
    
    // 将分层数据转换为扁平列表
    const pools = []
    const apps = new Set()
    const instances = new Set()
    
    if (statesRes.data) {
      for (const [appId, appInstances] of Object.entries(statesRes.data)) {
        apps.add(appId)
        for (const [instanceId, states] of Object.entries(appInstances)) {
          instances.add(instanceId)
          for (const state of states) {
            pools.push({ appId, instanceId, ...state })
          }
        }
      }
    }
    
    configs.value = pools
    health.value = healthRes
    stats.appCount = apps.size
    stats.instanceCount = instances.size
    lastUpdated.value = new Date().toLocaleTimeString()
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const editConfig = (row) => {
  editForm.instanceId = row.instanceId
  editForm.threadPoolId = row.threadPoolId
  editForm.corePoolSize = row.corePoolSize
  editForm.maximumPoolSize = row.maximumPoolSize
  editForm.queueCapacity = row.queueCapacity
  editForm.rejectedHandler = row.rejectedHandler || 'AbortPolicy'
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

const refreshAll = () => {
  fetchData()
  ElMessage.success('刷新完成')
}

const exportConfig = () => {
  const configData = {
    exportTime: new Date().toISOString(),
    version: '1.0.0',
    threadPools: configs.value.map(pool => ({
      threadPoolId: pool.threadPoolId,
      corePoolSize: pool.corePoolSize,
      maximumPoolSize: pool.maximumPoolSize,
      queueCapacity: pool.queueCapacity,
      keepAliveTime: pool.keepAliveTime,
      rejectedHandler: pool.rejectedHandler,
      allowCoreThreadTimeOut: pool.allowCoreThreadTimeOut
    }))
  }
  
  const blob = new Blob([JSON.stringify(configData, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `thread-pool-config-${new Date().toISOString().slice(0, 10)}.json`
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success('配置导出成功')
}

onMounted(() => {
  fetchData()
})
</script>

<style lang="scss" scoped>
.section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #303133;
}

.quick-actions {
  .action-buttons {
    display: flex;
    flex-direction: column;
    gap: 12px;
    
    :deep(.el-button) {
      margin-left: 0 !important;
      width: 100% !important;
    }
  }
}
</style>
