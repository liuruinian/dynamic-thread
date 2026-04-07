import CryptoJS from 'crypto-js'

/**
 * 钉钉机器人通知服务
 */
export class DingTalkService {
  constructor(webhook, secret) {
    this.webhook = webhook
    this.secret = secret
  }

  /**
   * 生成签名
   */
  generateSign() {
    const timestamp = Date.now()
    const stringToSign = `${timestamp}\n${this.secret}`
    const hash = CryptoJS.HmacSHA256(stringToSign, this.secret)
    const sign = CryptoJS.enc.Base64.stringify(hash)
    return { timestamp, sign: encodeURIComponent(sign) }
  }

  /**
   * 获取带签名的完整 URL（使用代理）
   */
  getSignedUrl() {
    const { timestamp, sign } = this.generateSign()
    // 使用代理路径解决跨域问题
    const accessToken = this.webhook.split('access_token=')[1]
    return `/dingtalk/robot/send?access_token=${accessToken}&timestamp=${timestamp}&sign=${sign}`
  }

  /**
   * 发送文本消息
   */
  async sendText(content, atMobiles = [], atAll = false) {
    return this.send({
      msgtype: 'text',
      text: { content },
      at: { atMobiles, isAtAll: atAll }
    })
  }

  /**
   * 发送 Markdown 消息
   */
  async sendMarkdown(title, text, atMobiles = [], atAll = false) {
    return this.send({
      msgtype: 'markdown',
      markdown: { title, text },
      at: { atMobiles, isAtAll: atAll }
    })
  }

  /**
   * 发送告警消息
   */
  async sendAlert(alert) {
    const levelEmoji = {
      INFO: 'ℹ️',
      WARNING: '⚠️',
      CRITICAL: '🚨'
    }
    
    const title = `${levelEmoji[alert.level] || '📢'} 线程池告警`
    const text = `### ${title}
    
**告警级别**: ${alert.level}

**线程池**: ${alert.threadPoolId}

**告警规则**: ${alert.ruleName}

**监控指标**: ${alert.metric}

**当前值**: ${alert.value}${alert.metric.includes('Usage') ? '%' : ''}

**阈值**: ${alert.threshold}${alert.metric.includes('Usage') ? '%' : ''}

**告警时间**: ${alert.time || new Date().toLocaleString()}

---
> Dynamic Thread Pool 监控平台`

    return this.sendMarkdown(title, text)
  }

  /**
   * 发送测试消息
   */
  async sendTest() {
    const title = '🔔 钉钉告警测试'
    const text = `### ${title}

**状态**: ✅ 连接成功

**消息**: 这是一条来自 Dynamic Thread Pool Dashboard 的测试消息

**时间**: ${new Date().toLocaleString()}

---
**线程池监控平台** 告警通知已配置成功！

> 当线程池状态异常时，您将收到实时告警通知。`

    return this.sendMarkdown(title, text)
  }

  /**
   * 发送消息
   */
  async send(message) {
    const url = this.getSignedUrl()
    
    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(message)
      })
      
      const result = await response.json()
      
      if (result.errcode === 0) {
        return { success: true, message: '发送成功' }
      } else {
        return { success: false, message: result.errmsg || '发送失败' }
      }
    } catch (error) {
      return { success: false, message: error.message || '网络错误' }
    }
  }
}

/**
 * 创建钉钉服务实例
 */
export function createDingTalkService(webhook, secret) {
  return new DingTalkService(webhook, secret)
}
