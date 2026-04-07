/**
 * 格式化数字
 * @param {number} num - 要格式化的数字
 * @param {number} decimals - 小数位数
 * @returns {string} 格式化后的字符串
 */
export function formatNumber(num, decimals = 1) {
  if (num === null || num === undefined) return '0'
  if (isNaN(num)) return String(num)
  
  if (num >= 1000000000) {
    return (num / 1000000000).toFixed(decimals) + 'B'
  }
  if (num >= 1000000) {
    return (num / 1000000).toFixed(decimals) + 'M'
  }
  if (num >= 1000) {
    return (num / 1000).toFixed(decimals) + 'K'
  }
  return num.toString()
}

/**
 * 格式化百分比
 * @param {number} value - 值
 * @param {number} total - 总数
 * @param {number} decimals - 小数位数
 * @returns {number} 百分比值
 */
export function calcPercent(value, total, decimals = 0) {
  if (!total || total === 0) return 0
  const percent = (value / total) * 100
  return decimals === 0 ? Math.round(percent) : parseFloat(percent.toFixed(decimals))
}

/**
 * 格式化时间
 * @param {Date|string|number} date - 日期
 * @param {string} format - 格式
 * @returns {string} 格式化后的时间字符串
 */
export function formatDate(date, format = 'YYYY-MM-DD HH:mm:ss') {
  const d = new Date(date)
  const padZero = (n) => String(n).padStart(2, '0')
  
  const tokens = {
    YYYY: d.getFullYear(),
    MM: padZero(d.getMonth() + 1),
    DD: padZero(d.getDate()),
    HH: padZero(d.getHours()),
    mm: padZero(d.getMinutes()),
    ss: padZero(d.getSeconds())
  }
  
  return format.replace(/YYYY|MM|DD|HH|mm|ss/g, match => tokens[match])
}

/**
 * 防抖函数
 * @param {Function} fn - 要防抖的函数
 * @param {number} delay - 延迟时间
 * @returns {Function} 防抖后的函数
 */
export function debounce(fn, delay = 300) {
  let timer = null
  return function (...args) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn.apply(this, args), delay)
  }
}

/**
 * 节流函数
 * @param {Function} fn - 要节流的函数
 * @param {number} interval - 间隔时间
 * @returns {Function} 节流后的函数
 */
export function throttle(fn, interval = 300) {
  let lastTime = 0
  return function (...args) {
    const now = Date.now()
    if (now - lastTime >= interval) {
      lastTime = now
      fn.apply(this, args)
    }
  }
}

/**
 * 深拷贝
 * @param {any} obj - 要拷贝的对象
 * @returns {any} 拷贝后的对象
 */
export function deepClone(obj) {
  if (obj === null || typeof obj !== 'object') return obj
  if (obj instanceof Date) return new Date(obj)
  if (obj instanceof Array) return obj.map(item => deepClone(item))
  if (obj instanceof Object) {
    const copy = {}
    Object.keys(obj).forEach(key => {
      copy[key] = deepClone(obj[key])
    })
    return copy
  }
  return obj
}

/**
 * 获取进度条颜色
 * @param {number} percent - 百分比
 * @returns {string} 颜色值
 */
export function getProgressColor(percent) {
  if (percent >= 80) return '#f56c6c'
  if (percent >= 50) return '#e6a23c'
  return '#67c23a'
}

/**
 * 下载 JSON 文件
 * @param {object} data - JSON 数据
 * @param {string} filename - 文件名
 */
export function downloadJSON(data, filename) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

/**
 * 复制文本到剪贴板
 * @param {string} text - 要复制的文本
 * @returns {Promise<void>}
 */
export async function copyToClipboard(text) {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch (err) {
    // 降级方案
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    return true
  }
}
