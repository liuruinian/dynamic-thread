<template>
  <div class="alarm">
    <div class="page-header">
      <h2>告警配置</h2>
      <p>配置线程池告警规则和通知方式</p>
    </div>

    <el-row :gutter="20">
      <el-col :span="16">
        <!-- 告警规则 -->
        <div class="card">
          <div class="card-header">
            <h3 class="section-title">告警规则</h3>
            <el-button type="primary" :icon="Plus" @click="addRule">添加规则</el-button>
          </div>
          
          <el-table :data="rules" style="width: 100%">
            <el-table-column prop="name" label="规则名称" width="180" />
            <el-table-column prop="threadPoolId" label="线程池" width="150" />
            <el-table-column prop="metric" label="监控指标" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ getMetricLabel(row.metric) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="operator" label="条件" width="80">
              <template #default="{ row }">
                {{ row.operator }} {{ row.threshold }}{{ row.metric === 'queueUsage' || row.metric === 'threadUsage' ? '%' : '' }}
              </template>
            </el-table-column>
            <el-table-column prop="level" label="告警级别" width="100">
              <template #default="{ row }">
                <el-tag :type="getLevelType(row.level)" size="small">{{ row.level }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" @change="updateRule(row)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row, $index }">
                <el-button type="primary" link @click="editRule(row, $index)">编辑</el-button>
                <el-button type="danger" link @click="deleteRule($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 告警历史 -->
        <div class="card" style="margin-top: 20px;">
          <div class="card-header">
            <h3 class="section-title">告警历史</h3>
            <el-space>
              <el-button v-if="alertHistory.length > 0" type="danger" text :icon="Delete" @click="clearHistory">清空</el-button>
              <el-button :icon="Refresh" @click="loadHistory" :loading="historyLoading">刷新</el-button>
            </el-space>
          </div>
          
          <!-- 筛选区域 -->
          <div class="filter-bar">
            <el-row :gutter="12" align="middle">
              <el-col :span="5">
                <el-select v-model="historyFilter.level" placeholder="告警级别" clearable size="small" style="width: 100%">
                  <el-option value="CRITICAL" label="严重" />
                  <el-option value="WARNING" label="警告" />
                  <el-option value="INFO" label="信息" />
                </el-select>
              </el-col>
              <el-col :span="6">
                <el-select v-model="historyFilter.threadPoolId" placeholder="线程池" clearable size="small" style="width: 100%">
                  <el-option v-for="pool in threadPools" :key="pool.threadPoolId" :value="pool.threadPoolId" :label="pool.threadPoolId" />
                </el-select>
              </el-col>
              <el-col :span="5">
                <el-select v-model="historyFilter.status" placeholder="状态" clearable size="small" style="width: 100%">
                  <el-option value="active" label="未解决" />
                  <el-option value="resolved" label="已解决" />
                </el-select>
              </el-col>
              <el-col :span="8">
                <el-switch v-model="autoRefreshHistory" active-text="自动刷新" size="small" />
              </el-col>
            </el-row>
          </div>

          <!-- 历史记录表格 -->
          <el-table :data="filteredHistory" style="width: 100%" v-loading="historyLoading" 
                    :row-class-name="getRowClassName" max-height="400">
            <el-table-column width="60">
              <template #default="{ row }">
                <el-tag :type="getLevelType(row.level)" size="small" effect="dark">
                  {{ row.level === 'CRITICAL' ? '严重' : row.level === 'WARNING' ? '警告' : '信息' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="threadPoolId" label="线程池" width="150" />
            <el-table-column prop="ruleName" label="规则名称" width="150" show-overflow-tooltip />
            <el-table-column label="告警内容" min-width="200">
              <template #default="{ row }">
                <span>{{ getMetricLabel(row.metric) }} = 
                  <strong :class="getLevelType(row.level)">{{ formatValue(row.value, row.metric) }}</strong>
                  ，超过阈值 {{ formatValue(row.threshold, row.metric) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="通知状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.notified" type="success" size="small">已通知</el-tag>
                <el-tag v-else type="info" size="small">未通知</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="time" label="时间" width="160" />
            <el-table-column label="状态" width="90" fixed="right">
              <template #default="{ row }">
                <el-tag v-if="row.resolved" type="success" size="small">已解决</el-tag>
                <el-button v-else type="primary" size="small" link @click="resolveAlarm(row)">标记解决</el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页 -->
          <div class="pagination-bar" v-if="historyTotal > historyPageSize">
            <el-pagination 
              v-model:current-page="historyPage"
              :page-size="historyPageSize"
              :total="historyTotal"
              layout="total, prev, pager, next"
              @current-change="loadHistory"
              small
            />
          </div>

          <el-empty v-if="!historyLoading && alertHistory.length === 0" description="暂无告警记录" />
        </div>
      </el-col>

      <el-col :span="8">
        <!-- 通知渠道 -->
        <div class="card">
          <h3 class="section-title">通知渠道</h3>
          <el-space direction="vertical" fill style="width: 100%">
            <div class="channel-item">
              <div class="channel-info">
                <el-icon :size="24" color="#409eff"><Message /></el-icon>
                <div class="channel-text">
                  <div class="channel-name">邮件通知</div>
                  <div class="channel-desc">发送告警邮件到指定邮箱</div>
                </div>
              </div>
              <el-space>
                <el-button size="small" type="primary" link @click="showEmailConfig">配置</el-button>
                <el-switch v-model="channels.email" @change="(val) => togglePlatform('EMAIL', val)" />
              </el-space>
            </div>
            <div class="channel-item">
              <div class="channel-info">
                <el-icon :size="24" color="#07c160"><ChatDotRound /></el-icon>
                <div class="channel-text">
                  <div class="channel-name">企业微信</div>
                  <div class="channel-desc">推送到企业微信群</div>
                </div>
              </div>
              <el-space>
                <el-button size="small" type="primary" link @click="showWechatConfig">配置</el-button>
                <el-switch v-model="channels.wechat" @change="(val) => togglePlatform('WECHAT', val)" />
              </el-space>
            </div>
            <div class="channel-item">
              <div class="channel-info">
                <el-icon :size="24" color="#1890ff"><ChatLineSquare /></el-icon>
                <div class="channel-text">
                  <div class="channel-name">钉钉</div>
                  <div class="channel-desc">推送到钉钉群</div>
                </div>
              </div>
              <el-space>
                <el-button size="small" type="primary" link @click="showDingtalkConfig">配置</el-button>
                <el-switch v-model="channels.dingtalk" @change="(val) => togglePlatform('DING', val)" />
              </el-space>
            </div>
            <div class="channel-item">
              <div class="channel-info">
                <el-icon :size="24" color="#ff6633"><Notification /></el-icon>
                <div class="channel-text">
                  <div class="channel-name">Webhook</div>
                  <div class="channel-desc">自定义 HTTP 回调</div>
                </div>
              </div>
              <el-space>
                <el-button size="small" type="primary" link @click="showWebhookConfig">配置</el-button>
                <el-switch v-model="channels.webhook" @change="(val) => togglePlatform('WEBHOOK', val)" />
              </el-space>
            </div>
          </el-space>
        </div>

        <!-- 告警统计 -->
        <div class="card" style="margin-top: 20px;">
          <h3 class="section-title">告警统计</h3>
          <el-row :gutter="16">
            <el-col :span="12">
              <div class="mini-stat">
                <div class="mini-value danger">{{ alertStats.critical }}</div>
                <div class="mini-label">严重告警</div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="mini-stat">
                <div class="mini-value warning">{{ alertStats.warning }}</div>
                <div class="mini-label">警告告警</div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="mini-stat">
                <div class="mini-value info">{{ alertStats.info }}</div>
                <div class="mini-label">信息告警</div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="mini-stat">
                <div class="mini-value success">{{ alertStats.resolved }}</div>
                <div class="mini-label">已恢复</div>
              </div>
            </el-col>
          </el-row>
          <div class="total-stat">
            <span>告警总数：</span>
            <strong>{{ alertStats.total || 0 }}</strong>
          </div>
        </div>

        <!-- 告警限流 -->
        <div class="card" style="margin-top: 20px;">
          <div class="card-header">
            <h3 class="section-title">告警限流</h3>
            <el-switch 
              v-model="rateLimitEnabled" 
              @change="toggleRateLimit"
              active-text="启用"
              inactive-text="禁用"
            />
          </div>
          
          <el-alert type="info" :closable="false" style="margin-bottom: 12px;">
            <template #title>
              实际间隔 = max(规则间隔, 全局间隔)，全局间隔作为最低保护
            </template>
          </el-alert>
          
          <div class="rate-limit-info">
            <el-descriptions :column="2" size="small" border>
              <el-descriptions-item label="全局最低间隔">
                <div class="editable-interval">
                  <el-input-number 
                    v-model="rateLimitInterval" 
                    :min="1" 
                    :max="3600" 
                    size="small"
                    @change="updateRateLimitInterval"
                    style="width: 100px"
                  />
                  <span class="unit">秒</span>
                </div>
              </el-descriptions-item>
              <el-descriptions-item label="已发送告警">
                <span class="stat-value success">{{ rateLimitStats.totalSentCount || 0 }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="已限流告警">
                <span class="stat-value warning">{{ rateLimitStats.totalThrottledCount || 0 }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="当前窗口限流">
                <span class="stat-value info">{{ rateLimitStats.currentWindowThrottledCount || 0 }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </div>

          <!-- 限流状态详情 -->
          <div v-if="Object.keys(rateLimitStates).length > 0" class="rate-limit-states">
            <div class="states-header">
              <span class="states-title">限流状态详情</span>
              <el-button size="small" type="danger" text :icon="Delete" @click="resetAllRateLimits">
                重置全部
              </el-button>
            </div>
            <el-table :data="rateLimitStateList" size="small" max-height="200">
              <el-table-column prop="key" label="线程池:指标" min-width="180">
                <template #default="{ row }">
                  <el-tag size="small" type="info">{{ row.key }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="上次告警" width="100">
                <template #default="{ row }">
                  <span v-if="row.lastAlarmTime">{{ formatRateLimitTime(row.lastAlarmTime) }}</span>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
              <el-table-column label="限流次数" width="80" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.throttledCountInWindow > 0" type="warning" size="small">
                    {{ row.throttledCountInWindow }}
                  </el-tag>
                  <span v-else class="text-muted">0</span>
                </template>
              </el-table-column>
              <el-table-column label="冷却中" width="70" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.secondsUntilNextAllowed > 0" type="danger" size="small">
                    {{ row.secondsUntilNextAllowed }}s
                  </el-tag>
                  <el-tag v-else type="success" size="small">就绪</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="60" fixed="right">
                <template #default="{ row }">
                  <el-button type="primary" size="small" link @click="resetRateLimit(row.key)">
                    重置
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <el-empty v-else description="暂无限流状态" :image-size="60" />
        </div>

        <!-- 快速测试 -->
        <div class="card" style="margin-top: 20px;">
          <h3 class="section-title">告警测试</h3>
          <el-form :model="testForm" label-position="top" size="small">
            <el-form-item label="模拟类型">
              <el-radio-group v-model="testForm.type">
                <el-radio value="queue">队列使用率</el-radio>
                <el-radio value="thread">线程使用率</el-radio>
                <el-radio value="reject">拒绝任务</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="模拟值">
              <el-input-number v-model="testForm.value" :min="0" :max="100" style="width: 100%" />
            </el-form-item>
            <el-button type="warning" :icon="Bell" @click="simulateAlarm" :loading="simulateTesting" style="width: 100%">
              模拟告警
            </el-button>
          </el-form>
        </div>
      </el-col>
    </el-row>

    <!-- 钉钉配置对话框 -->
    <el-dialog v-model="dingtalkDialogVisible" title="钉钉机器人配置" width="600px">
      <el-form :model="dingtalkConfig" label-width="100px" v-loading="dingtalkConfigLoading">
        <el-alert 
          v-if="dingtalkConfig.configSource === 'server'" 
          type="success" 
          :closable="false" 
          style="margin-bottom: 16px;"
        >
          <template #title>
            <el-icon><CircleCheck /></el-icon>
            配置来源：服务端{{ dingtalkConfig.configDetail ? ` (${dingtalkConfig.configDetail})` : '' }}
          </template>
        </el-alert>
        <el-alert 
          v-else 
          type="info" 
          :closable="false" 
          style="margin-bottom: 16px;"
        >
          <template #title>
            <el-icon><InfoFilled /></el-icon>
            配置来源：本地缓存
          </template>
        </el-alert>
        <el-form-item label="Webhook" required>
          <el-input v-model="dingtalkConfig.webhook" placeholder="请输入钉钉机器人 Webhook 地址" />
        </el-form-item>
        <el-form-item label="签名密钥">
          <el-input v-model="dingtalkConfig.secret" placeholder="请输入加签密钥 (SEC...)，可选" show-password />
          <div class="form-tip">若钉钉机器人启用了加签验证，请填写对应密钥</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="testDingtalk" :loading="dingtalkTesting">
            <el-icon><Bell /></el-icon>
            发送测试消息
          </el-button>
          <el-button @click="testDingtalkAlert" :loading="dingtalkTesting">
            <el-icon><Warning /></el-icon>
            模拟告警推送
          </el-button>
          <el-button @click="loadDingtalkConfig" :icon="Refresh">
            重新加载
          </el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dingtalkDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDingtalkConfig">保存并启用</el-button>
      </template>
    </el-dialog>

    <!-- 企业微信配置对话框 -->
    <el-dialog v-model="wechatDialogVisible" title="企业微信机器人配置" width="600px">
      <el-form :model="wechatConfig" label-width="100px" v-loading="wechatConfigLoading">
        <el-alert 
          v-if="wechatConfig.configSource === 'server'" 
          type="success" 
          :closable="false" 
          style="margin-bottom: 16px;"
        >
          <template #title>
            <el-icon><CircleCheck /></el-icon>
            配置来源：服务端{{ wechatConfig.configDetail ? ` (${wechatConfig.configDetail})` : '' }}
          </template>
        </el-alert>
        <el-alert 
          v-else 
          type="info" 
          :closable="false" 
          style="margin-bottom: 16px;"
        >
          <template #title>
            <el-icon><InfoFilled /></el-icon>
            配置来源：本地缓存
          </template>
        </el-alert>
        <el-form-item label="Webhook" required>
          <el-input v-model="wechatConfig.webhook" placeholder="请输入企业微信机器人 Webhook 地址" />
          <div class="form-tip">企业微信群机器人 Webhook 地址可在群设置 > 群机器人 中获取</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="testWechat" :loading="wechatTesting">
            <el-icon><Bell /></el-icon>
            发送测试消息
          </el-button>
          <el-button @click="testWechatAlert" :loading="wechatTesting">
            <el-icon><Warning /></el-icon>
            模拟告警推送
          </el-button>
          <el-button @click="loadWechatConfig" :icon="Refresh">
            重新加载
          </el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="wechatDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveWechatConfig">保存并启用</el-button>
      </template>
    </el-dialog>

    <!-- Webhook 配置对话框 -->
    <el-dialog v-model="webhookDialogVisible" title="Webhook 配置" width="600px">
      <el-form :model="webhookConfig" label-width="120px" v-loading="webhookConfigLoading">
        <el-alert 
          v-if="webhookConfig.configSource === 'server'" 
          type="success" 
          :closable="false" 
          style="margin-bottom: 16px;"
        >
          <template #title>
            <el-icon><CircleCheck /></el-icon>
            配置来源：服务端{{ webhookConfig.configDetail ? ` (${webhookConfig.configDetail})` : '' }}
          </template>
        </el-alert>
        <el-alert 
          v-else 
          type="info" 
          :closable="false" 
          style="margin-bottom: 16px;"
        >
          <template #title>
            <el-icon><InfoFilled /></el-icon>
            配置来源：本地缓存
          </template>
        </el-alert>
        <el-form-item label="Webhook URL" required>
          <el-input v-model="webhookConfig.webhook" placeholder="https://your-server.com/webhook" />
          <div class="form-tip">告警信息将以 JSON 格式 POST 到指定 URL</div>
        </el-form-item>
        <el-form-item label="认证密钥">
          <el-input v-model="webhookConfig.secret" placeholder="可选，用于请求签名验证" show-password />
          <div class="form-tip">密钥将用于生成 HMAC-SHA256 签名，通过 X-Signature 请求头发送</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="testWebhook" :loading="webhookTesting">
            <el-icon><Bell /></el-icon>
            发送测试消息
          </el-button>
          <el-button @click="testWebhookAlert" :loading="webhookTesting">
            <el-icon><Warning /></el-icon>
            模拟告警推送
          </el-button>
          <el-button @click="loadWebhookConfig" :icon="Refresh">
            重新加载
          </el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="webhookDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveWebhookConfig">保存并启用</el-button>
      </template>
    </el-dialog>

    <!-- 邮件通知配置对话框 -->
    <el-dialog v-model="emailDialogVisible" title="邮件通知配置" width="640px">
      <el-form :model="emailConfig" label-width="120px" v-loading="emailConfigLoading">
        <el-alert 
          v-if="emailConfig.configSource === 'server'" 
          type="success" 
          :closable="false" 
          style="margin-bottom: 16px;"
        >
          <template #title>
            <el-icon><CircleCheck /></el-icon>
            配置来源：服务端{{ emailConfig.configDetail ? ` (${emailConfig.configDetail})` : '' }}
          </template>
        </el-alert>
        <el-alert 
          v-else 
          type="info" 
          :closable="false" 
          style="margin-bottom: 16px;"
        >
          <template #title>
            <el-icon><InfoFilled /></el-icon>
            配置来源：本地缓存
          </template>
        </el-alert>
        <el-form-item label="SMTP 服务器" required>
          <el-row :gutter="16">
            <el-col :span="16">
              <el-input v-model="emailConfig.smtpHost" placeholder="smtp.qq.com" />
            </el-col>
            <el-col :span="8">
              <el-input-number v-model="emailConfig.smtpPort" :min="1" :max="65535" style="width: 100%" controls-position="right" />
            </el-col>
          </el-row>
          <div class="form-tip">SMTP 服务器地址和端口（如 smtp.qq.com:465）</div>
        </el-form-item>
        <el-form-item label="SSL/TLS">
          <el-switch v-model="emailConfig.ssl" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px;">
            SSL 通常使用 465 端口，STARTTLS 使用 587 端口
          </span>
        </el-form-item>
        <el-form-item label="登录账号" required>
          <el-input v-model="emailConfig.username" placeholder="your-email@example.com" />
        </el-form-item>
        <el-form-item label="登录密码" required>
          <el-input v-model="emailConfig.password" placeholder="SMTP 授权码或密码" show-password />
          <div class="form-tip">QQ邮箱/163邮箱等需使用授权码而非登录密码</div>
        </el-form-item>
        <el-form-item label="发件人地址" required>
          <el-input v-model="emailConfig.fromAddress" placeholder="alarm@example.com" />
        </el-form-item>
        <el-form-item label="收件人地址" required>
          <el-input v-model="emailConfig.toAddresses" placeholder="admin@example.com" />
          <div class="form-tip">多个收件人用英文逗号分隔</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="testEmail" :loading="emailTesting">
            <el-icon><Bell /></el-icon>
            发送测试邮件
          </el-button>
          <el-button @click="testEmailAlert" :loading="emailTesting">
            <el-icon><Warning /></el-icon>
            模拟告警推送
          </el-button>
          <el-button @click="loadEmailConfig" :icon="Refresh">
            重新加载
          </el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="emailDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEmailConfig">保存并启用</el-button>
      </template>
    </el-dialog>

    <!-- 规则编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingIndex >= 0 ? '编辑规则' : '添加规则'" width="600px">
      <el-form :model="ruleForm" label-width="100px" :rules="formRules" ref="formRef">
        <el-form-item label="规则名称" prop="name">
          <el-input v-model="ruleForm.name" placeholder="请输入规则名称" />
        </el-form-item>
        <el-form-item label="线程池" prop="threadPoolId">
          <el-select v-model="ruleForm.threadPoolId" placeholder="选择线程池" style="width: 100%">
            <el-option value="*" label="所有线程池" />
            <el-option 
              v-for="pool in threadPools" 
              :key="pool.threadPoolId" 
              :value="pool.threadPoolId" 
              :label="pool.threadPoolId" 
            />
          </el-select>
        </el-form-item>
        <el-form-item label="监控指标" prop="metric">
          <el-select v-model="ruleForm.metric" placeholder="选择监控指标" style="width: 100%">
            <el-option value="threadUsage" label="线程使用率 (%)" />
            <el-option value="queueUsage" label="队列使用率 (%)" />
            <el-option value="activeCount" label="活跃线程数" />
            <el-option value="queueSize" label="队列大小" />
            <el-option value="rejectedCount" label="拒绝任务数" />
          </el-select>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="条件" prop="operator">
              <el-select v-model="ruleForm.operator" style="width: 100%">
                <el-option value=">" label="大于" />
                <el-option value=">=" label="大于等于" />
                <el-option value="<" label="小于" />
                <el-option value="<=" label="小于等于" />
                <el-option value="=" label="等于" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="阈值" prop="threshold">
              <el-input-number v-model="ruleForm.threshold" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="告警级别" prop="level">
          <el-radio-group v-model="ruleForm.level">
            <el-radio-button value="INFO">信息</el-radio-button>
            <el-radio-button value="WARNING">警告</el-radio-button>
            <el-radio-button value="CRITICAL">严重</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="告警间隔" prop="interval">
          <el-input-number v-model="ruleForm.interval" :min="10" :max="3600" style="width: 100%" />
          <span style="margin-left: 8px; color: #909399;">秒（同一规则两次告警的最小间隔）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { Plus, Bell, Warning, Refresh, CircleCheck, InfoFilled, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { threadPoolApi, alarmApi } from '@/api'

const dialogVisible = ref(false)
const editingIndex = ref(-1)
const formRef = ref(null)
const threadPools = ref([])
const loading = ref(false)
const historyLoading = ref(false)
const autoRefreshHistory = ref(true)
let refreshTimer = null

// 告警规则
const rules = ref([])

const ruleForm = reactive({
  name: '',
  threadPoolId: '*',
  metric: 'threadUsage',
  operator: '>',
  threshold: 80,
  level: 'WARNING',
  interval: 60
})

const formRules = {
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  threadPoolId: [{ required: true, message: '请选择线程池', trigger: 'change' }],
  metric: [{ required: true, message: '请选择监控指标', trigger: 'change' }],
  threshold: [{ required: true, message: '请输入阈值', trigger: 'blur' }]
}

const channels = reactive({
  email: false,
  wechat: false,
  dingtalk: false,
  webhook: false
})

// 钉钉配置
const dingtalkDialogVisible = ref(false)
const dingtalkTesting = ref(false)
const dingtalkConfigLoading = ref(false)
const dingtalkConfig = reactive({
  webhook: '',
  secret: '',
  configSource: 'local',
  configDetail: ''
})

// 企业微信配置
const wechatDialogVisible = ref(false)
const wechatTesting = ref(false)
const wechatConfigLoading = ref(false)
const wechatConfig = reactive({
  webhook: '',
  configSource: 'local',
  configDetail: ''
})

// Webhook 配置
const webhookDialogVisible = ref(false)
const webhookTesting = ref(false)
const webhookConfigLoading = ref(false)
const webhookConfig = reactive({
  webhook: '',
  secret: '',
  configSource: 'local',
  configDetail: ''
})

// 邮件通知配置
const emailDialogVisible = ref(false)
const emailTesting = ref(false)
const emailConfigLoading = ref(false)
const emailConfig = reactive({
  smtpHost: '',
  smtpPort: 465,
  username: '',
  password: '',
  fromAddress: '',
  toAddresses: '',
  ssl: true,
  configSource: 'local',
  configDetail: ''
})

// 告警历史
const alertHistory = ref([])
const historyPage = ref(1)
const historyPageSize = ref(20)
const historyTotal = ref(0)
const historyFilter = reactive({
  level: '',
  threadPoolId: '',
  status: ''
})

// 告警统计
const alertStats = reactive({
  critical: 0,
  warning: 0,
  info: 0,
  resolved: 0,
  total: 0
})

// 限流相关
const rateLimitEnabled = ref(true)
const rateLimitInterval = ref(60)
const rateLimitStats = reactive({
  totalSentCount: 0,
  totalThrottledCount: 0,
  currentWindowThrottledCount: 0,
  activeKeys: 0
})
const rateLimitStates = ref({})

// 限流状态列表（计算属性）
const rateLimitStateList = computed(() => {
  return Object.entries(rateLimitStates.value).map(([key, state]) => ({
    key,
    ...state
  }))
})

// 模拟告警测试
const testForm = reactive({
  type: 'queue',
  value: 90
})
const simulateTesting = ref(false)

// 筛选后的历史记录
const filteredHistory = computed(() => {
  let result = alertHistory.value
  if (historyFilter.level) {
    result = result.filter(r => r.level === historyFilter.level)
  }
  if (historyFilter.threadPoolId) {
    result = result.filter(r => r.threadPoolId === historyFilter.threadPoolId)
  }
  if (historyFilter.status === 'resolved') {
    result = result.filter(r => r.resolved)
  } else if (historyFilter.status === 'active') {
    result = result.filter(r => !r.resolved)
  }
  return result
})

// 辅助方法
const getMetricLabel = (metric) => {
  const labels = {
    threadUsage: '线程使用率',
    queueUsage: '队列使用率',
    activeCount: '活跃线程数',
    queueSize: '队列大小',
    rejectedCount: '拒绝任务数'
  }
  return labels[metric] || metric
}

const getLevelType = (level) => {
  const types = { INFO: 'info', WARNING: 'warning', CRITICAL: 'danger' }
  return types[level] || 'info'
}

const formatValue = (value, metric) => {
  if (value === null || value === undefined) return '-'
  const isPercent = metric && (metric.includes('Usage') || metric.includes('Percent'))
  return isPercent ? `${Number(value).toFixed(1)}%` : String(Math.round(value))
}

const formatTime = (timeArray) => {
  if (!timeArray) return ''
  if (typeof timeArray === 'string') return timeArray
  if (Array.isArray(timeArray) && timeArray.length >= 5) {
    const [year, month, day, hour, minute, second = 0] = timeArray
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')} ${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:${String(second).padStart(2, '0')}`
  }
  return String(timeArray)
}

const getRowClassName = ({ row }) => {
  if (row.resolved) return 'row-resolved'
  if (row.level === 'CRITICAL') return 'row-critical'
  if (row.level === 'WARNING') return 'row-warning'
  return ''
}

// ==================== 规则管理 ====================

const loadRules = async () => {
  try {
    const res = await alarmApi.listRules()
    rules.value = res.rules || []
  } catch (e) {
    console.error('Failed to load rules:', e)
  }
}

const addRule = () => {
  editingIndex.value = -1
  Object.assign(ruleForm, {
    name: '',
    threadPoolId: '*',
    metric: 'threadUsage',
    operator: '>',
    threshold: 80,
    level: 'WARNING',
    interval: 60
  })
  dialogVisible.value = true
}

const editRule = (rule, index) => {
  editingIndex.value = index
  Object.assign(ruleForm, {
    id: rule.id,
    name: rule.name,
    threadPoolId: rule.threadPoolId,
    metric: rule.metric,
    operator: rule.operator,
    threshold: rule.threshold,
    level: rule.level,
    interval: rule.interval || 60
  })
  dialogVisible.value = true
}

const saveRule = async () => {
  await formRef.value?.validate()
  loading.value = true
  try {
    if (editingIndex.value >= 0 && ruleForm.id) {
      await alarmApi.updateRule(ruleForm.id, ruleForm)
      ElMessage.success('规则更新成功')
    } else {
      await alarmApi.addRule(ruleForm)
      ElMessage.success('规则添加成功')
    }
    dialogVisible.value = false
    await loadRules()
  } catch (e) {
    ElMessage.error('保存规则失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

const deleteRule = async (index) => {
  const rule = rules.value[index]
  await ElMessageBox.confirm('确定要删除这条规则吗？', '提示', { type: 'warning' })
  try {
    await alarmApi.deleteRule(rule.id)
    ElMessage.success('规则已删除')
    await loadRules()
  } catch (e) {
    ElMessage.error('删除失败: ' + e.message)
  }
}

const updateRule = async (rule) => {
  try {
    await alarmApi.toggleRule(rule.id, rule.enabled)
    ElMessage.success(rule.enabled ? '规则已启用' : '规则已禁用')
  } catch (e) {
    rule.enabled = !rule.enabled
    ElMessage.error('操作失败: ' + e.message)
  }
}

// ==================== 历史记录 ====================

const loadHistory = async () => {
  historyLoading.value = true
  try {
    const res = await alarmApi.getHistory(historyPage.value - 1, historyPageSize.value)
    alertHistory.value = (res.records || []).map(record => ({
      ...record,
      time: formatTime(record.time)
    }))
    historyTotal.value = res.total || 0
  } catch (e) {
    console.error('Failed to load history:', e)
  } finally {
    historyLoading.value = false
  }
}

const loadStatistics = async () => {
  try {
    const res = await alarmApi.getStatistics()
    if (res.statistics) {
      Object.assign(alertStats, res.statistics)
    }
  } catch (e) {
    console.error('Failed to load statistics:', e)
  }
}

const clearHistory = async () => {
  await ElMessageBox.confirm('确定要清空所有告警历史记录吗？', '提示', { type: 'warning' })
  try {
    await alarmApi.clearHistory()
    ElMessage.success('告警历史已清空')
    await loadHistory()
    await loadStatistics()
  } catch (e) {
    ElMessage.error('清空失败: ' + e.message)
  }
}

const resolveAlarm = async (record) => {
  try {
    await alarmApi.resolveAlarm(record.id)
    record.resolved = true
    ElMessage.success('告警已标记为已解决')
    await loadStatistics()
  } catch (e) {
    ElMessage.error('操作失败: ' + e.message)
  }
}

// ==================== 模拟告警 ====================

const simulateAlarm = async () => {
  simulateTesting.value = true
  try {
    const res = await alarmApi.simulate(testForm.type, testForm.value)
    if (res.success) {
      if (res.triggeredCount > 0) {
        ElMessage.success(`成功触发 ${res.triggeredCount} 个告警`)
      } else {
        ElMessage.warning('未触发任何告警，请检查规则配置和阈值设置')
      }
      // 刷新历史和统计
      await loadHistory()
      await loadStatistics()
      await loadRateLimitData()
    } else {
      ElMessage.error(res.message || '模拟失败')
    }
  } catch (e) {
    ElMessage.error('模拟告警失败: ' + e.message)
  } finally {
    simulateTesting.value = false
  }
}

// ==================== 限流管理 ====================

const loadRateLimitData = async () => {
  try {
    const [statsRes, statesRes] = await Promise.all([
      alarmApi.getRateLimitStatistics(),
      alarmApi.getRateLimitStates()
    ])
    
    if (statsRes.statistics) {
      Object.assign(rateLimitStats, statsRes.statistics)
      rateLimitEnabled.value = statsRes.statistics.enabled
      rateLimitInterval.value = statsRes.statistics.defaultIntervalSeconds || 60
    }
    
    if (statesRes.states) {
      rateLimitStates.value = statesRes.states
    }
  } catch (e) {
    console.error('Failed to load rate limit data:', e)
  }
}

const toggleRateLimit = async (enabled) => {
  try {
    await alarmApi.toggleRateLimit(enabled)
    ElMessage.success(enabled ? '告警限流已启用' : '告警限流已禁用')
  } catch (e) {
    rateLimitEnabled.value = !enabled
    ElMessage.error('操作失败: ' + e.message)
  }
}

const updateRateLimitInterval = async (value) => {
  try {
    await alarmApi.setRateLimitInterval(value)
    ElMessage.success(`限流间隔已设置为 ${value} 秒`)
  } catch (e) {
    ElMessage.error('设置失败: ' + e.message)
  }
}

const resetAllRateLimits = async () => {
  try {
    await ElMessageBox.confirm('确定要重置所有限流状态吗？这将允许所有告警立即发送。', '提示', { type: 'warning' })
    await alarmApi.resetAllRateLimits()
    ElMessage.success('所有限流状态已重置')
    await loadRateLimitData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('重置失败: ' + e.message)
    }
  }
}

const resetRateLimit = async (key) => {
  try {
    const [threadPoolId, metric] = key.split(':')
    await alarmApi.resetRateLimit(threadPoolId, metric)
    ElMessage.success(`${key} 限流状态已重置`)
    await loadRateLimitData()
  } catch (e) {
    ElMessage.error('重置失败: ' + e.message)
  }
}

const formatRateLimitTime = (timeArray) => {
  if (!timeArray) return ''
  if (Array.isArray(timeArray) && timeArray.length >= 5) {
    const [, , , hour, minute, second = 0] = timeArray
    return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:${String(second).padStart(2, '0')}`
  }
  return ''
}

// ==================== 通知平台 ====================

const loadEnabledPlatforms = async () => {
  try {
    const res = await alarmApi.getEnabledPlatforms()
    const platforms = res.platforms || []
    channels.email = platforms.includes('EMAIL')
    channels.wechat = platforms.includes('WECHAT')
    channels.dingtalk = platforms.includes('DING') || platforms.includes('DINGTALK')
    channels.webhook = platforms.includes('WEBHOOK')
  } catch (e) {
    console.error('Failed to load enabled platforms:', e)
  }
}

const togglePlatform = async (platform, enabled) => {
  try {
    if (enabled) {
      await alarmApi.enablePlatform(platform)
    } else {
      await alarmApi.disablePlatform(platform)
    }
    ElMessage.success(enabled ? `${platform} 已启用` : `${platform} 已禁用`)
  } catch (e) {
    ElMessage.error('操作失败: ' + e.message)
  }
}

// ==================== 钉钉配置 ====================

const showDingtalkConfig = async () => {
  dingtalkDialogVisible.value = true
  await loadDingtalkConfig()
}

const loadDingtalkConfig = async () => {
  dingtalkConfigLoading.value = true
  try {
    const res = await alarmApi.getPlatformConfig('DING')
    if (res && res.found && res.platform) {
      dingtalkConfig.webhook = res.platform.webhookUrl || ''
      dingtalkConfig.secret = res.platform.hasSecret === 'true' ? (res.platform.secret || '') : ''
      dingtalkConfig.configSource = 'server'
      dingtalkConfig.configDetail = res.configSource || ''
      ElMessage.success('已从服务端加载钉钉配置')
    } else {
      const localConfig = localStorage.getItem('dingtalk_config')
      if (localConfig) {
        const parsed = JSON.parse(localConfig)
        dingtalkConfig.webhook = parsed.webhook || ''
        dingtalkConfig.secret = parsed.secret || ''
        dingtalkConfig.configSource = 'local'
        ElMessage.info('从本地缓存加载配置')
      } else {
        dingtalkConfig.configSource = 'local'
        ElMessage.info('请手动填写钉钉配置')
      }
    }
  } catch (e) {
    console.error('Failed to load dingtalk config:', e)
    const localConfig = localStorage.getItem('dingtalk_config')
    if (localConfig) {
      const parsed = JSON.parse(localConfig)
      dingtalkConfig.webhook = parsed.webhook || ''
      dingtalkConfig.secret = parsed.secret || ''
      dingtalkConfig.configSource = 'local'
    }
  } finally {
    dingtalkConfigLoading.value = false
  }
}

const testDingtalk = async () => {
  if (!dingtalkConfig.webhook) {
    ElMessage.warning('请先填写 Webhook 地址')
    return
  }
  
  dingtalkTesting.value = true
  try {
    const res = await alarmApi.testPlatform('DING', {
      webhookUrl: dingtalkConfig.webhook,
      secret: dingtalkConfig.secret
    })
    
    if (res.success) {
      ElMessage.success('测试消息发送成功，请查看钉钉群')
    } else {
      ElMessage.error(`发送失败: ${res.message}`)
    }
  } catch (e) {
    ElMessage.error(`发送异常: ${e.message}`)
  } finally {
    dingtalkTesting.value = false
  }
}

const testDingtalkAlert = async () => {
  if (!dingtalkConfig.webhook) {
    ElMessage.warning('请先填写 Webhook 地址')
    return
  }
  
  dingtalkTesting.value = true
  try {
    // 先在后端配置并注册钉钉平台
    await alarmApi.configurePlatform('DING', {
      webhookUrl: dingtalkConfig.webhook,
      secret: dingtalkConfig.secret
    })
    
    // 通过后端模拟告警，触发完整的告警流程
    const res = await alarmApi.simulate('thread', 95)
    
    if (res.success && res.triggeredCount > 0) {
      ElMessage.success(`模拟告警发送成功，触发 ${res.triggeredCount} 个告警，请查看钉钉群`)
    } else if (res.success && res.triggeredCount === 0) {
      ElMessage.warning('未触发告警规则，请检查规则配置。已尝试直接发送测试消息。')
      // 降级为直接测试
      await alarmApi.testPlatform('DING', {
        webhookUrl: dingtalkConfig.webhook,
        secret: dingtalkConfig.secret
      })
    } else {
      ElMessage.error(`模拟失败: ${res.message}`)
    }
    
    // 刷新历史和统计
    await loadHistory()
    await loadStatistics()
  } catch (e) {
    ElMessage.error(`模拟告警异常: ${e.message}`)
  } finally {
    dingtalkTesting.value = false
  }
}

const saveDingtalkConfig = async () => {
  if (!dingtalkConfig.webhook) {
    ElMessage.warning('请输入 Webhook 地址')
    return
  }
  try {
    await alarmApi.configurePlatform('DING', {
      webhookUrl: dingtalkConfig.webhook,
      secret: dingtalkConfig.secret
    })
    localStorage.setItem('dingtalk_config', JSON.stringify(dingtalkConfig))
    channels.dingtalk = true
    dingtalkDialogVisible.value = false
    ElMessage.success('钉钉配置已保存并启用')
  } catch (e) {
    ElMessage.error('配置失败: ' + e.message)
  }
}

// ==================== 企业微信 ====================

const showWechatConfig = async () => {
  wechatDialogVisible.value = true
  await loadWechatConfig()
}

const loadWechatConfig = async () => {
  wechatConfigLoading.value = true
  try {
    const res = await alarmApi.getPlatformConfig('WECHAT')
    if (res && res.found && res.platform) {
      wechatConfig.webhook = res.platform.webhookUrl || ''
      wechatConfig.configSource = 'server'
      wechatConfig.configDetail = res.configSource || ''
      ElMessage.success('已从服务端加载企业微信配置')
    } else {
      const localConfig = localStorage.getItem('wechat_config')
      if (localConfig) {
        const parsed = JSON.parse(localConfig)
        wechatConfig.webhook = parsed.webhook || ''
        wechatConfig.configSource = 'local'
        ElMessage.info('从本地缓存加载配置')
      } else {
        wechatConfig.configSource = 'local'
        ElMessage.info('请手动填写企业微信配置')
      }
    }
  } catch (e) {
    console.error('Failed to load wechat config:', e)
    const localConfig = localStorage.getItem('wechat_config')
    if (localConfig) {
      const parsed = JSON.parse(localConfig)
      wechatConfig.webhook = parsed.webhook || ''
      wechatConfig.configSource = 'local'
    }
  } finally {
    wechatConfigLoading.value = false
  }
}

const testWechat = async () => {
  if (!wechatConfig.webhook) {
    ElMessage.warning('请先填写 Webhook 地址')
    return
  }
  wechatTesting.value = true
  try {
    const res = await alarmApi.testPlatform('WECHAT', {
      webhookUrl: wechatConfig.webhook
    })
    if (res.success) {
      ElMessage.success('测试消息发送成功，请查看企业微信群')
    } else {
      ElMessage.error('发送失败: ' + res.message)
    }
  } catch (e) {
    ElMessage.error('测试失败: ' + e.message)
  } finally {
    wechatTesting.value = false
  }
}

const testWechatAlert = async () => {
  if (!wechatConfig.webhook) {
    ElMessage.warning('请先填写 Webhook 地址')
    return
  }
  
  wechatTesting.value = true
  try {
    // 先在后端配置并注册企业微信平台
    await alarmApi.configurePlatform('WECHAT', {
      webhookUrl: wechatConfig.webhook
    })
    
    // 通过后端模拟告警，触发完整的告警流程
    const res = await alarmApi.simulate('thread', 95)
    
    if (res.success && res.triggeredCount > 0) {
      ElMessage.success(`模拟告警发送成功，触发 ${res.triggeredCount} 个告警，请查看企业微信群`)
    } else if (res.success && res.triggeredCount === 0) {
      ElMessage.warning('未触发告警规则，已尝试直接发送测试消息。')
      await alarmApi.testPlatform('WECHAT', {
        webhookUrl: wechatConfig.webhook
      })
    } else {
      ElMessage.error(`模拟失败: ${res.message}`)
    }
    
    // 刷新历史和统计
    await loadHistory()
    await loadStatistics()
  } catch (e) {
    ElMessage.error(`模拟告警异常: ${e.message}`)
  } finally {
    wechatTesting.value = false
  }
}

const saveWechatConfig = async () => {
  if (!wechatConfig.webhook) {
    ElMessage.warning('请输入 Webhook 地址')
    return
  }
  try {
    await alarmApi.configurePlatform('WECHAT', {
      webhookUrl: wechatConfig.webhook
    })
    localStorage.setItem('wechat_config', JSON.stringify(wechatConfig))
    channels.wechat = true
    wechatDialogVisible.value = false
    ElMessage.success('企业微信配置已保存并启用')
  } catch (e) {
    ElMessage.error('配置失败: ' + e.message)
  }
}

// ==================== Webhook ====================

const showWebhookConfig = async () => {
  webhookDialogVisible.value = true
  await loadWebhookConfig()
}

const loadWebhookConfig = async () => {
  webhookConfigLoading.value = true
  try {
    const res = await alarmApi.getPlatformConfig('WEBHOOK')
    if (res && res.found && res.platform) {
      webhookConfig.webhook = res.platform.webhookUrl || ''
      webhookConfig.secret = '' // never returned from server
      webhookConfig.configSource = 'server'
      webhookConfig.configDetail = res.configSource || ''
      ElMessage.success('已从服务端加载 Webhook 配置')
    } else {
      const localConfig = localStorage.getItem('webhook_config')
      if (localConfig) {
        const parsed = JSON.parse(localConfig)
        webhookConfig.webhook = parsed.webhook || ''
        webhookConfig.secret = parsed.secret || ''
        webhookConfig.configSource = 'local'
        ElMessage.info('从本地缓存加载配置')
      } else {
        webhookConfig.configSource = 'local'
        ElMessage.info('请手动填写 Webhook 配置')
      }
    }
  } catch (e) {
    console.error('Failed to load webhook config:', e)
    const localConfig = localStorage.getItem('webhook_config')
    if (localConfig) {
      const parsed = JSON.parse(localConfig)
      webhookConfig.webhook = parsed.webhook || ''
      webhookConfig.secret = parsed.secret || ''
      webhookConfig.configSource = 'local'
    }
  } finally {
    webhookConfigLoading.value = false
  }
}

const testWebhook = async () => {
  if (!webhookConfig.webhook) {
    ElMessage.warning('请先填写 Webhook URL')
    return
  }
  webhookTesting.value = true
  try {
    const res = await alarmApi.testPlatform('WEBHOOK', {
      webhookUrl: webhookConfig.webhook,
      secret: webhookConfig.secret
    })
    if (res.success) {
      ElMessage.success('测试消息发送成功')
    } else {
      ElMessage.error('发送失败: ' + res.message)
    }
  } catch (e) {
    ElMessage.error('测试失败: ' + e.message)
  } finally {
    webhookTesting.value = false
  }
}

const testWebhookAlert = async () => {
  if (!webhookConfig.webhook) {
    ElMessage.warning('请先填写 Webhook URL')
    return
  }
  
  webhookTesting.value = true
  try {
    // 先在后端配置并注册 Webhook 平台
    await alarmApi.configurePlatform('WEBHOOK', {
      webhookUrl: webhookConfig.webhook,
      secret: webhookConfig.secret
    })
    
    // 通过后端模拟告警
    const res = await alarmApi.simulate('thread', 95)
    
    if (res.success && res.triggeredCount > 0) {
      ElMessage.success(`模拟告警发送成功，触发 ${res.triggeredCount} 个告警`)
    } else if (res.success && res.triggeredCount === 0) {
      ElMessage.warning('未触发告警规则，已尝试直接发送测试消息。')
      await alarmApi.testPlatform('WEBHOOK', {
        webhookUrl: webhookConfig.webhook,
        secret: webhookConfig.secret
      })
    } else {
      ElMessage.error(`模拟失败: ${res.message}`)
    }
    
    await loadHistory()
    await loadStatistics()
  } catch (e) {
    ElMessage.error(`模拟告警异常: ${e.message}`)
  } finally {
    webhookTesting.value = false
  }
}

const saveWebhookConfig = async () => {
  if (!webhookConfig.webhook) {
    ElMessage.warning('请输入 Webhook URL')
    return
  }
  try {
    await alarmApi.configurePlatform('WEBHOOK', {
      webhookUrl: webhookConfig.webhook,
      secret: webhookConfig.secret
    })
    localStorage.setItem('webhook_config', JSON.stringify(webhookConfig))
    channels.webhook = true
    webhookDialogVisible.value = false
    ElMessage.success('Webhook 配置已保存并启用')
  } catch (e) {
    ElMessage.error('配置失败: ' + e.message)
  }
}

// ==================== 邮件通知 ====================

const showEmailConfig = async () => {
  emailDialogVisible.value = true
  await loadEmailConfig()
}

const loadEmailConfig = async () => {
  emailConfigLoading.value = true
  try {
    const res = await alarmApi.getPlatformConfig('EMAIL')
    if (res && res.found && res.platform) {
      emailConfig.smtpHost = res.platform.smtpHost || ''
      emailConfig.smtpPort = parseInt(res.platform.smtpPort) || 465
      emailConfig.username = res.platform.username || ''
      emailConfig.fromAddress = res.platform.fromAddress || ''
      emailConfig.toAddresses = res.platform.toAddresses || ''
      emailConfig.ssl = res.platform.ssl === 'true'
      emailConfig.password = '' // never returned from server
      emailConfig.configSource = 'server'
      emailConfig.configDetail = res.configSource || ''
      ElMessage.success('已从服务端加载邮件配置')
    } else {
      const localConfig = localStorage.getItem('email_config')
      if (localConfig) {
        const parsed = JSON.parse(localConfig)
        Object.assign(emailConfig, parsed)
        emailConfig.configSource = 'local'
        ElMessage.info('从本地缓存加载配置')
      } else {
        emailConfig.configSource = 'local'
        ElMessage.info('请手动填写邮件配置')
      }
    }
  } catch (e) {
    console.error('Failed to load email config:', e)
    const localConfig = localStorage.getItem('email_config')
    if (localConfig) {
      const parsed = JSON.parse(localConfig)
      Object.assign(emailConfig, parsed)
      emailConfig.configSource = 'local'
    }
  } finally {
    emailConfigLoading.value = false
  }
}

const buildEmailPayload = () => ({
  smtpHost: emailConfig.smtpHost,
  smtpPort: String(emailConfig.smtpPort),
  username: emailConfig.username,
  password: emailConfig.password,
  fromAddress: emailConfig.fromAddress,
  toAddresses: emailConfig.toAddresses,
  ssl: String(emailConfig.ssl)
})

const testEmail = async () => {
  if (!emailConfig.smtpHost || !emailConfig.toAddresses) {
    ElMessage.warning('请先填写 SMTP 服务器和收件人地址')
    return
  }
  emailTesting.value = true
  try {
    const res = await alarmApi.testPlatform('EMAIL', buildEmailPayload())
    if (res.success) {
      ElMessage.success('测试邮件发送成功，请查看收件箱')
    } else {
      ElMessage.error('发送失败: ' + res.message)
    }
  } catch (e) {
    ElMessage.error('测试失败: ' + e.message)
  } finally {
    emailTesting.value = false
  }
}

const testEmailAlert = async () => {
  if (!emailConfig.smtpHost || !emailConfig.toAddresses) {
    ElMessage.warning('请先填写 SMTP 服务器和收件人地址')
    return
  }
  
  emailTesting.value = true
  try {
    // 先在后端配置并注册邮件平台
    await alarmApi.configurePlatform('EMAIL', buildEmailPayload())
    
    // 通过后端模拟告警
    const res = await alarmApi.simulate('thread', 95)
    
    if (res.success && res.triggeredCount > 0) {
      ElMessage.success(`模拟告警发送成功，触发 ${res.triggeredCount} 个告警，请查看收件箱`)
    } else if (res.success && res.triggeredCount === 0) {
      ElMessage.warning('未触发告警规则，已尝试直接发送测试邮件。')
      await alarmApi.testPlatform('EMAIL', buildEmailPayload())
    } else {
      ElMessage.error(`模拟失败: ${res.message}`)
    }
    
    await loadHistory()
    await loadStatistics()
  } catch (e) {
    ElMessage.error(`模拟告警异常: ${e.message}`)
  } finally {
    emailTesting.value = false
  }
}

const saveEmailConfig = async () => {
  if (!emailConfig.smtpHost) {
    ElMessage.warning('请输入 SMTP 服务器地址')
    return
  }
  if (!emailConfig.toAddresses) {
    ElMessage.warning('请输入收件人地址')
    return
  }
  try {
    await alarmApi.configurePlatform('EMAIL', buildEmailPayload())
    // Save to localStorage (without password for security)
    const configToSave = { ...emailConfig, password: '' }
    localStorage.setItem('email_config', JSON.stringify(configToSave))
    channels.email = true
    emailDialogVisible.value = false
    ElMessage.success('邮件通知配置已保存并启用')
  } catch (e) {
    ElMessage.error('配置失败: ' + e.message)
  }
}

// ==================== 数据刷新 ===================

const loadThreadPools = async () => {
  try {
    const res = await threadPoolApi.states()
    const pools = []
    if (res.data) {
      for (const [appId, appInstances] of Object.entries(res.data)) {
        for (const [instanceId, states] of Object.entries(appInstances)) {
          for (const state of states) {
            pools.push({ appId, instanceId, ...state })
          }
        }
      }
    }
    threadPools.value = pools
  } catch (e) {
    console.error('Failed to load thread pools:', e)
  }
}

const refreshData = async () => {
  await Promise.all([
    loadRules(),
    loadHistory(),
    loadStatistics(),
    loadEnabledPlatforms(),
    loadRateLimitData()
  ])
}

watch(autoRefreshHistory, (val) => {
  if (val) {
    refreshTimer = setInterval(async () => {
      await loadHistory()
      await loadStatistics()
    }, 10000)
  } else {
    if (refreshTimer) clearInterval(refreshTimer)
  }
})

onMounted(async () => {
  loading.value = true
  try {
    await loadThreadPools()
    await refreshData()
  } finally {
    loading.value = false
  }
  
  if (autoRefreshHistory.value) {
    refreshTimer = setInterval(async () => {
      await loadHistory()
      await loadStatistics()
    }, 10000)
  }
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})
</script>

<style lang="scss" scoped>
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

.filter-bar {
  margin-bottom: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
}

.pagination-bar {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.channel-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
  
  .channel-info {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  
  .channel-text {
    .channel-name {
      font-weight: 600;
      color: #303133;
    }
    .channel-desc {
      font-size: 12px;
      color: #909399;
      margin-top: 4px;
    }
  }
}

.mini-stat {
  text-align: center;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 12px;
  
  .mini-value {
    font-size: 24px;
    font-weight: 600;
    
    &.danger { color: #f56c6c; }
    &.warning { color: #e6a23c; }
    &.info { color: #409eff; }
    &.success { color: #67c23a; }
  }
  
  .mini-label {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
  }
}

.total-stat {
  text-align: center;
  padding: 12px;
  background: #ecf5ff;
  border-radius: 8px;
  color: #409eff;
  
  strong {
    font-size: 18px;
    margin-left: 8px;
  }
}

// 表格行样式
:deep(.row-resolved) {
  background-color: #f0f9eb !important;
  opacity: 0.7;
}

:deep(.row-critical) {
  background-color: #fef0f0 !important;
}

:deep(.row-warning) {
  background-color: #fdf6ec !important;
}

// 文字颜色
.danger {
  color: #f56c6c;
}

.warning {
  color: #e6a23c;
}

.info {
  color: #409eff;
}

.success {
  color: #67c23a;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

// 限流相关样式
.rate-limit-info {
  margin-bottom: 16px;
}

.editable-interval {
  display: flex;
  align-items: center;
  gap: 4px;
  
  .unit {
    color: #909399;
    font-size: 12px;
  }
}

.stat-value {
  font-weight: 600;
  font-size: 14px;
  
  &.success { color: #67c23a; }
  &.warning { color: #e6a23c; }
  &.info { color: #409eff; }
  &.danger { color: #f56c6c; }
}

.rate-limit-states {
  margin-top: 16px;
  
  .states-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
    
    .states-title {
      font-size: 13px;
      font-weight: 500;
      color: #606266;
    }
  }
}

.text-muted {
  color: #c0c4cc;
}
</style>
