<template>
  <div class="cluster-nodes">
    <div class="page-header">
      <h2>集群节点</h2>
      <p>dynamic-thread-server 集群状态与节点信息</p>
    </div>

    <!-- 集群不可用提示 -->
    <div v-if="clusterDisabled" class="cluster-disabled">
      <el-empty description="集群模式未启用">
        <template #image>
          <el-icon :size="64" style="color: #c0c4cc"><OfficeBuilding /></el-icon>
        </template>
        <p style="color: #909399; margin-top: 8px;">
          当前 Server 以单机模式运行，如需集群功能请配置
          <code>dynamic-thread.server.cluster.enabled=true</code>
        </p>
      </el-empty>
    </div>

    <template v-else>
      <!-- 概览卡片 -->
      <el-row :gutter="20" v-loading="loading">
        <el-col :span="6">
          <div class="card stat-card">
            <div class="stat-icon status-icon" :class="healthClass">
              <el-icon :size="28"><CircleCheck v-if="clusterHealthy" /><WarningFilled v-else /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ healthStatus }}</div>
              <div class="stat-label">集群状态</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="card stat-card">
            <div class="stat-icon nodes-icon">
              <el-icon :size="28"><Monitor /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ clusterInfo?.healthyMembers || 0 }} / {{ clusterInfo?.totalMembers || 0 }}</div>
              <div class="stat-label">健康节点 / 总节点</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="card stat-card">
            <div class="stat-icon peer-icon">
              <el-icon :size="28"><Connection /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ clusterInfo?.activePeerConnections || 0 }}</div>
              <div class="stat-label">活跃 Peer 连接</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="card stat-card">
            <div class="stat-icon agent-icon">
              <el-icon :size="28"><User /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ clusterInfo?.totalAgentCount || 0 }}</div>
              <div class="stat-label">Agent 总连接数</div>
            </div>
          </div>
        </el-col>
      </el-row>

      <!-- 当前节点信息 -->
      <div class="card" style="margin-top: 20px;">
        <div class="section-header">
          <h3 class="section-title">当前节点</h3>
          <el-tag v-if="clusterInfo?.isLeader" type="warning" effect="dark" size="small">Leader</el-tag>
        </div>
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="节点 ID">
            <el-tag type="primary" effect="plain" size="small">{{ clusterInfo?.nodeId || '-' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="节点地址">{{ clusterInfo?.nodeAddress || '-' }}</el-descriptions-item>
          <el-descriptions-item label="本地 Agent">{{ clusterInfo?.localAgentCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="全局 Agent">{{ clusterInfo?.totalAgentCount || 0 }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 节点列表 -->
      <div class="card" style="margin-top: 20px;">
        <div class="section-header">
          <h3 class="section-title">节点列表</h3>
          <el-button size="small" :icon="Refresh" @click="fetchData" :loading="loading">刷新</el-button>
        </div>
        <el-table :data="nodes" stripe style="width: 100%" :row-class-name="tableRowClassName">
          <el-table-column label="节点 ID" width="160">
            <template #default="{ row }">
              <div class="node-id-cell">
                <el-tag 
                  :type="row.self ? 'primary' : ''" 
                  effect="plain" 
                  size="small"
                >
                  {{ row.nodeId }}
                </el-tag>
                <el-tag v-if="row.self" type="info" size="small" effect="light" style="margin-left: 4px;">
                  当前
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="address" label="地址" width="200" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <div class="status-cell">
                <span class="status-dot" :class="stateClass(row.state)"></span>
                <span :class="'state-text ' + stateClass(row.state)">{{ stateLabel(row.state) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="健康" width="80" align="center">
            <template #default="{ row }">
              <el-icon v-if="row.healthy" style="color: #67c23a; font-size: 18px;"><CircleCheck /></el-icon>
              <el-icon v-else style="color: #f56c6c; font-size: 18px;"><CircleClose /></el-icon>
            </template>
          </el-table-column>
          <el-table-column label="连接" width="80" align="center">
            <template #default="{ row }">
              <template v-if="row.self">
                <el-tag size="small" type="info">本机</el-tag>
              </template>
              <template v-else>
                <el-icon v-if="row.connected" style="color: #67c23a; font-size: 18px;"><CircleCheck /></el-icon>
                <el-icon v-else style="color: #f56c6c; font-size: 18px;"><CircleClose /></el-icon>
              </template>
            </template>
          </el-table-column>
          <el-table-column label="Agent 数" width="100" align="center">
            <template #default="{ row }">
              <span class="agent-count">{{ row.agentCount || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="加入时间" width="180">
            <template #default="{ row }">
              {{ formatJoinTime(row.joinTime) }}
            </template>
          </el-table-column>
          <el-table-column label="最后心跳">
            <template #default="{ row }">
              <template v-if="row.self">-</template>
              <template v-else>
                <span :class="{ 'heartbeat-stale': isHeartbeatStale(row.lastHeartbeat) }">
                  {{ formatHeartbeat(row.lastHeartbeat) }}
                </span>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 不健康节点告警 -->
      <el-alert
        v-if="unhealthyNodes.length > 0"
        style="margin-top: 20px;"
        title="存在不健康的集群节点"
        type="warning"
        :closable="false"
        show-icon
      >
        <template #default>
          以下节点状态异常：
          <el-tag
            v-for="nodeId in unhealthyNodes"
            :key="nodeId"
            type="danger"
            size="small"
            style="margin: 2px 4px;"
          >{{ nodeId }}</el-tag>
        </template>
      </el-alert>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { 
  CircleCheck, CircleClose, WarningFilled, Monitor, 
  Connection, User, Refresh, OfficeBuilding 
} from '@element-plus/icons-vue'
import { clusterApi } from '@/api'

const loading = ref(false)
const clusterDisabled = ref(false)
const clusterInfo = ref(null)
const clusterHealth = ref(null)
const nodes = ref([])

let refreshTimer = null

const clusterHealthy = computed(() => clusterHealth.value?.status === 'UP')
const healthStatus = computed(() => {
  if (!clusterHealth.value) return '-'
  return clusterHealth.value.status === 'UP' ? '健康' : '降级'
})
const healthClass = computed(() => clusterHealthy.value ? 'healthy' : 'degraded')

const unhealthyNodes = computed(() => clusterHealth.value?.unhealthyNodes || [])

const stateClass = (state) => {
  switch (state) {
    case 'UP': return 'up'
    case 'DOWN': return 'down'
    case 'SUSPECT': return 'suspect'
    default: return 'unknown'
  }
}

const stateLabel = (state) => {
  switch (state) {
    case 'UP': return '在线'
    case 'DOWN': return '离线'
    case 'SUSPECT': return '疑似'
    default: return '未知'
  }
}

const tableRowClassName = ({ row }) => {
  if (row.self) return 'self-row'
  if (!row.healthy) return 'unhealthy-row'
  return ''
}

const formatJoinTime = (joinTime) => {
  if (!joinTime || !Array.isArray(joinTime)) return '-'
  const [year, month, day, hour, minute, second] = joinTime
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')} ${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:${String(second).padStart(2, '0')}`
}

const formatHeartbeat = (timestamp) => {
  if (!timestamp) return '-'
  const elapsed = Date.now() - timestamp
  if (elapsed < 1000) return '刚刚'
  if (elapsed < 60000) return `${Math.floor(elapsed / 1000)}秒前`
  if (elapsed < 3600000) return `${Math.floor(elapsed / 60000)}分钟前`
  return `${Math.floor(elapsed / 3600000)}小时前`
}

const isHeartbeatStale = (timestamp) => {
  if (!timestamp) return true
  return (Date.now() - timestamp) > 30000
}

const fetchData = async () => {
  loading.value = true
  try {
    const [infoRes, nodesRes, healthRes] = await Promise.all([
      clusterApi.info(),
      clusterApi.nodes(),
      clusterApi.health()
    ])
    clusterInfo.value = infoRes
    nodes.value = nodesRes.nodes || []
    clusterHealth.value = healthRes
    clusterDisabled.value = false
  } catch (e) {
    if (e.response?.status === 404) {
      clusterDisabled.value = true
    }
    console.error('Cluster data fetch error:', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchData()
  refreshTimer = setInterval(fetchData, 5000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style lang="scss" scoped>
.page-header {
  margin-bottom: 20px;
  
  h2 {
    margin: 0 0 8px 0;
    font-size: 24px;
    font-weight: 600;
  }
  
  p {
    margin: 0;
    color: #909399;
  }
}

.cluster-disabled {
  padding: 60px 0;
  text-align: center;
  
  code {
    background: #f5f7fa;
    padding: 2px 8px;
    border-radius: 4px;
    font-size: 13px;
    color: #8B5CF6;
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
    
    &.status-icon.healthy { background: linear-gradient(135deg, #67c23a, #85ce61); }
    &.status-icon.degraded { background: linear-gradient(135deg, #e6a23c, #f0c78a); }
    &.nodes-icon { background: linear-gradient(135deg, #409eff, #66b1ff); }
    &.peer-icon { background: linear-gradient(135deg, #8B5CF6, #A78BFA); }
    &.agent-icon { background: linear-gradient(135deg, #f56c6c, #fab6b6); }
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

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: #303133;
}

.node-id-cell {
  display: flex;
  align-items: center;
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  
  &.up {
    background: #67c23a;
    box-shadow: 0 0 6px rgba(103, 194, 58, 0.4);
  }
  &.down {
    background: #f56c6c;
  }
  &.suspect {
    background: #e6a23c;
    animation: blink 1.5s infinite;
  }
  &.unknown {
    background: #c0c4cc;
  }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.state-text {
  font-size: 13px;
  font-weight: 500;
  
  &.up { color: #67c23a; }
  &.down { color: #f56c6c; }
  &.suspect { color: #e6a23c; }
  &.unknown { color: #c0c4cc; }
}

.agent-count {
  font-weight: 600;
  color: #303133;
}

.heartbeat-stale {
  color: #e6a23c;
}

:deep(.self-row) {
  background: rgba(139, 92, 246, 0.04) !important;
}

:deep(.unhealthy-row) {
  background: rgba(245, 108, 108, 0.04) !important;
}
</style>
