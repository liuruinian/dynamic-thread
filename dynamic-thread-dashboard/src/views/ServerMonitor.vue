<template>
  <div class="server-monitor">
    <div class="page-header">
      <h2>服务器监控</h2>
      <p>dynamic-thread-server 运行环境实时监控</p>
    </div>

    <!-- 概览卡片 -->
    <el-row :gutter="20" v-loading="loading">
      <el-col :span="6">
        <div class="card stat-card">
          <div class="stat-icon cpu-icon">
            <el-icon :size="28"><Cpu /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ metrics?.cpu?.usage?.toFixed(1) || 0 }}%</div>
            <div class="stat-label">CPU 使用率</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="card stat-card">
          <div class="stat-icon memory-icon">
            <el-icon :size="28"><Coin /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ metrics?.memory?.usagePercent?.toFixed(1) || 0 }}%</div>
            <div class="stat-label">内存使用率</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="card stat-card">
          <div class="stat-icon disk-icon">
            <el-icon :size="28"><Files /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ avgDiskUsage.toFixed(1) }}%</div>
            <div class="stat-label">磁盘使用率</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="card stat-card">
          <div class="stat-icon network-icon">
            <el-icon :size="28"><Connection /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ metrics?.network?.tcpConnections || 0 }}</div>
            <div class="stat-label">TCP 连接数</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- CPU & 内存 -->
      <el-col :span="12">
        <div class="card">
          <h3 class="section-title">CPU 信息</h3>
          <el-row :gutter="20">
            <el-col :span="12">
              <v-chart class="gauge-chart" :option="cpuGaugeOption" autoresize />
            </el-col>
            <el-col :span="12">
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="型号">{{ metrics?.cpu?.model || '-' }}</el-descriptions-item>
                <el-descriptions-item label="核心数">{{ metrics?.cpu?.physicalCores }} 物理 / {{ metrics?.cpu?.logicalCores }} 逻辑</el-descriptions-item>
                <el-descriptions-item label="频率">{{ metrics?.cpu?.frequencyMhz || 0 }} MHz</el-descriptions-item>
                <el-descriptions-item label="负载 (1/5/15分钟)">
                  {{ metrics?.cpu?.loadAverage1?.toFixed(2) || 0 }} / 
                  {{ metrics?.cpu?.loadAverage5?.toFixed(2) || 0 }} / 
                  {{ metrics?.cpu?.loadAverage15?.toFixed(2) || 0 }}
                </el-descriptions-item>
              </el-descriptions>
            </el-col>
          </el-row>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="card">
          <h3 class="section-title">内存信息</h3>
          <el-row :gutter="20">
            <el-col :span="12">
              <v-chart class="gauge-chart" :option="memoryGaugeOption" autoresize />
            </el-col>
            <el-col :span="12">
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="总内存">{{ formatBytes(metrics?.memory?.total) }}</el-descriptions-item>
                <el-descriptions-item label="已使用">{{ formatBytes(metrics?.memory?.used) }}</el-descriptions-item>
                <el-descriptions-item label="剩余">{{ formatBytes(metrics?.memory?.free) }}</el-descriptions-item>
                <el-descriptions-item label="交换区">
                  {{ formatBytes(metrics?.memory?.swapUsed) }} / {{ formatBytes(metrics?.memory?.swapTotal) }}
                </el-descriptions-item>
              </el-descriptions>
            </el-col>
          </el-row>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- JVM 信息 -->
      <el-col :span="12">
        <div class="card">
          <h3 class="section-title">JVM 信息</h3>
          <el-row :gutter="20">
            <el-col :span="12">
              <v-chart class="gauge-chart" :option="heapGaugeOption" autoresize />
              <div class="gauge-label">堆内存使用率</div>
            </el-col>
            <el-col :span="12">
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="JVM">{{ metrics?.jvm?.name || '-' }}</el-descriptions-item>
                <el-descriptions-item label="版本">{{ metrics?.jvm?.version || '-' }}</el-descriptions-item>
                <el-descriptions-item label="运行时间">{{ formatUptime(metrics?.jvm?.uptime) }}</el-descriptions-item>
                <el-descriptions-item label="堆内存">
                  {{ formatBytes(metrics?.jvm?.heapUsed) }} / {{ formatBytes(metrics?.jvm?.heapMax) }}
                </el-descriptions-item>
                <el-descriptions-item label="非堆内存">{{ formatBytes(metrics?.jvm?.nonHeapUsed) }}</el-descriptions-item>
              </el-descriptions>
            </el-col>
          </el-row>
          <el-row :gutter="16" style="margin-top: 16px;">
            <el-col :span="6">
              <div class="mini-stat">
                <div class="mini-value">{{ metrics?.jvm?.threadCount || 0 }}</div>
                <div class="mini-label">活动线程</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="mini-stat">
                <div class="mini-value">{{ metrics?.jvm?.peakThreadCount || 0 }}</div>
                <div class="mini-label">峰值线程</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="mini-stat">
                <div class="mini-value">{{ metrics?.jvm?.gcCount || 0 }}</div>
                <div class="mini-label">GC 次数</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="mini-stat">
                <div class="mini-value">{{ formatMs(metrics?.jvm?.gcTime) }}</div>
                <div class="mini-label">GC 耗时</div>
              </div>
            </el-col>
          </el-row>
        </div>
      </el-col>

      <!-- 网络流量 -->
      <el-col :span="12">
        <div class="card">
          <h3 class="section-title">网络流量</h3>
          <v-chart class="trend-chart" :option="networkChartOption" autoresize />
          <el-row :gutter="16" style="margin-top: 16px;">
            <el-col :span="6">
              <div class="mini-stat">
                <div class="mini-value primary">{{ formatSpeed(metrics?.network?.receiveRate) }}</div>
                <div class="mini-label">下载速度</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="mini-stat">
                <div class="mini-value success">{{ formatSpeed(metrics?.network?.sendRate) }}</div>
                <div class="mini-label">上传速度</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="mini-stat">
                <div class="mini-value">{{ formatBytes(metrics?.network?.totalBytesReceived) }}</div>
                <div class="mini-label">累计接收</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="mini-stat">
                <div class="mini-value">{{ formatBytes(metrics?.network?.totalBytesSent) }}</div>
                <div class="mini-label">累计发送</div>
              </div>
            </el-col>
          </el-row>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- 磁盘信息 -->
      <el-col :span="24">
        <div class="card">
          <h3 class="section-title">磁盘信息</h3>
          <el-row :gutter="16" style="margin-bottom: 16px;">
            <el-col :span="6">
              <div class="mini-stat">
                <div class="mini-value primary">{{ formatSpeed(metrics?.disk?.readSpeed) }}</div>
                <div class="mini-label">读取速度</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="mini-stat">
                <div class="mini-value success">{{ formatSpeed(metrics?.disk?.writeSpeed) }}</div>
                <div class="mini-label">写入速度</div>
              </div>
            </el-col>
          </el-row>
          <el-table :data="metrics?.disk?.partitions || []" stripe style="width: 100%">
            <el-table-column prop="mountPoint" label="挂载点" width="120" />
            <el-table-column prop="fileSystem" label="文件系统" width="100" />
            <el-table-column label="总容量" width="120">
              <template #default="{ row }">{{ formatBytes(row.totalSpace) }}</template>
            </el-table-column>
            <el-table-column label="已用" width="120">
              <template #default="{ row }">{{ formatBytes(row.usedSpace) }}</template>
            </el-table-column>
            <el-table-column label="剩余" width="120">
              <template #default="{ row }">{{ formatBytes(row.freeSpace) }}</template>
            </el-table-column>
            <el-table-column label="使用率">
              <template #default="{ row }">
                <el-progress 
                  :percentage="row.usagePercent" 
                  :color="getProgressColor(row.usagePercent)"
                  :stroke-width="16"
                  :text-inside="true"
                  :format="(p) => p.toFixed(1) + '%'"
                />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { GaugeChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { serverApi } from '@/api'

use([CanvasRenderer, GaugeChart, LineChart, GridComponent, TooltipComponent, LegendComponent])

const loading = ref(false)
const metrics = ref(null)

// 历史数据
const networkHistory = ref({
  timestamps: [],
  receiveRate: [],
  sendRate: []
})

let refreshTimer = null

const avgDiskUsage = computed(() => {
  const partitions = metrics.value?.disk?.partitions || []
  if (partitions.length === 0) return 0
  const total = partitions.reduce((sum, p) => sum + (p.usagePercent || 0), 0)
  return total / partitions.length
})

const cpuGaugeOption = computed(() => ({
  series: [{
    type: 'gauge',
    progress: { show: true, width: 15, itemStyle: { color: '#409eff' } },
    axisLine: { lineStyle: { width: 15, color: [[1, '#e4e7ed']] } },
    axisTick: { show: false },
    splitLine: { show: false },
    axisLabel: { show: false },
    pointer: { show: false },
    anchor: { show: false },
    title: { show: false },
    detail: { valueAnimation: true, fontSize: 24, offsetCenter: [0, 0], formatter: '{value}%' },
    data: [{ value: Math.round(metrics.value?.cpu?.usage || 0) }]
  }]
}))

const memoryGaugeOption = computed(() => ({
  series: [{
    type: 'gauge',
    progress: { show: true, width: 15, itemStyle: { color: '#67c23a' } },
    axisLine: { lineStyle: { width: 15, color: [[1, '#e4e7ed']] } },
    axisTick: { show: false },
    splitLine: { show: false },
    axisLabel: { show: false },
    pointer: { show: false },
    anchor: { show: false },
    title: { show: false },
    detail: { valueAnimation: true, fontSize: 24, offsetCenter: [0, 0], formatter: '{value}%' },
    data: [{ value: Math.round(metrics.value?.memory?.usagePercent || 0) }]
  }]
}))

const heapGaugeOption = computed(() => ({
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
    detail: { valueAnimation: true, fontSize: 24, offsetCenter: [0, 0], formatter: '{value}%' },
    data: [{ value: Math.round(metrics.value?.jvm?.heapUsagePercent || 0) }]
  }]
}))

const networkChartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['下载', '上传'], bottom: 0 },
  grid: { left: 50, right: 20, top: 10, bottom: 40 },
  xAxis: { type: 'category', data: networkHistory.value.timestamps, boundaryGap: false },
  yAxis: { type: 'value', axisLabel: { formatter: v => formatSpeed(v) } },
  series: [
    { name: '下载', type: 'line', smooth: true, areaStyle: { opacity: 0.3 }, data: networkHistory.value.receiveRate, itemStyle: { color: '#409eff' } },
    { name: '上传', type: 'line', smooth: true, areaStyle: { opacity: 0.3 }, data: networkHistory.value.sendRate, itemStyle: { color: '#67c23a' } }
  ]
}))

const fetchData = async () => {
  loading.value = true
  try {
    const res = await serverApi.metrics()
    metrics.value = res.data
    
    // 更新网络历史
    const now = new Date().toLocaleTimeString()
    networkHistory.value.timestamps.push(now)
    networkHistory.value.receiveRate.push(res.data?.network?.receiveRate || 0)
    networkHistory.value.sendRate.push(res.data?.network?.sendRate || 0)
    
    // 保持最近30条
    if (networkHistory.value.timestamps.length > 30) {
      networkHistory.value.timestamps.shift()
      networkHistory.value.receiveRate.shift()
      networkHistory.value.sendRate.shift()
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const formatBytes = (bytes) => {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let i = 0
  while (bytes >= 1024 && i < units.length - 1) {
    bytes /= 1024
    i++
  }
  return bytes.toFixed(2) + ' ' + units[i]
}

const formatSpeed = (bytesPerSec) => {
  if (!bytesPerSec) return '0 B/s'
  const units = ['B/s', 'KB/s', 'MB/s', 'GB/s']
  let i = 0
  while (bytesPerSec >= 1024 && i < units.length - 1) {
    bytesPerSec /= 1024
    i++
  }
  return bytesPerSec.toFixed(2) + ' ' + units[i]
}

const formatUptime = (ms) => {
  if (!ms) return '0s'
  const seconds = Math.floor(ms / 1000)
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const secs = seconds % 60
  
  const parts = []
  if (days > 0) parts.push(`${days}天`)
  if (hours > 0) parts.push(`${hours}小时`)
  if (minutes > 0) parts.push(`${minutes}分钟`)
  if (secs > 0 || parts.length === 0) parts.push(`${secs}秒`)
  
  return parts.join(' ')
}

const formatMs = (ms) => {
  if (!ms) return '0ms'
  if (ms < 1000) return ms + 'ms'
  return (ms / 1000).toFixed(2) + 's'
}

const getProgressColor = (percent) => {
  if (percent >= 90) return '#f56c6c'
  if (percent >= 70) return '#e6a23c'
  return '#67c23a'
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
    
    &.cpu-icon { background: linear-gradient(135deg, #409eff, #66b1ff); }
    &.memory-icon { background: linear-gradient(135deg, #67c23a, #85ce61); }
    &.disk-icon { background: linear-gradient(135deg, #e6a23c, #f0c78a); }
    &.network-icon { background: linear-gradient(135deg, #f56c6c, #fab6b6); }
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

.gauge-chart {
  height: 160px;
}

.gauge-label {
  text-align: center;
  color: #909399;
  font-size: 13px;
  margin-top: -16px;
}

.trend-chart {
  height: 200px;
}

.mini-stat {
  text-align: center;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
  
  .mini-value {
    font-size: 20px;
    font-weight: 600;
    color: #303133;
    
    &.primary { color: #409eff; }
    &.success { color: #67c23a; }
    &.warning { color: #e6a23c; }
    &.danger { color: #f56c6c; }
  }
  
  .mini-label {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
  }
}
</style>
