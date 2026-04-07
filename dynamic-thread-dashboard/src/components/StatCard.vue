<template>
  <div class="stat-card-component" :class="[type, { clickable }]" @click="handleClick">
    <div class="stat-icon" v-if="icon" :style="iconStyle">
      <el-icon :size="iconSize">
        <component :is="icon" />
      </el-icon>
    </div>
    <div class="stat-content">
      <div class="stat-value" :style="{ color: valueColor }">
        <span class="value-number">{{ formattedValue }}</span>
        <span class="value-suffix" v-if="suffix">{{ suffix }}</span>
      </div>
      <div class="stat-label">{{ label }}</div>
      <div class="stat-trend" v-if="trend !== undefined">
        <el-icon :size="14" :color="trend >= 0 ? '#67c23a' : '#f56c6c'">
          <CaretTop v-if="trend >= 0" />
          <CaretBottom v-else />
        </el-icon>
        <span :class="trend >= 0 ? 'up' : 'down'">{{ Math.abs(trend) }}%</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { CaretTop, CaretBottom } from '@element-plus/icons-vue'

const props = defineProps({
  value: {
    type: [Number, String],
    default: 0
  },
  label: {
    type: String,
    required: true
  },
  icon: {
    type: Object,
    default: null
  },
  type: {
    type: String,
    default: 'default', // default, success, warning, danger, info
    validator: val => ['default', 'success', 'warning', 'danger', 'info'].includes(val)
  },
  suffix: {
    type: String,
    default: ''
  },
  trend: {
    type: Number,
    default: undefined
  },
  clickable: {
    type: Boolean,
    default: false
  },
  iconSize: {
    type: Number,
    default: 28
  },
  format: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['click'])

const typeColors = {
  default: { bg: '#409eff', value: '#303133' },
  success: { bg: '#67c23a', value: '#67c23a' },
  warning: { bg: '#e6a23c', value: '#e6a23c' },
  danger: { bg: '#f56c6c', value: '#f56c6c' },
  info: { bg: '#909399', value: '#909399' }
}

const iconStyle = computed(() => ({
  background: `linear-gradient(135deg, ${typeColors[props.type].bg}, ${typeColors[props.type].bg}dd)`
}))

const valueColor = computed(() => 
  props.type === 'default' ? typeColors.default.value : typeColors[props.type].value
)

const formattedValue = computed(() => {
  if (!props.format) return props.value
  const num = Number(props.value)
  if (isNaN(num)) return props.value
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num
})

const handleClick = () => {
  if (props.clickable) {
    emit('click')
  }
}
</script>

<style lang="scss" scoped>
.stat-card-component {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
  transition: all 0.3s;
  
  &.clickable {
    cursor: pointer;
    
    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 16px 0 rgba(0, 0, 0, 0.1);
    }
  }
  
  .stat-icon {
    width: 56px;
    height: 56px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    flex-shrink: 0;
  }
  
  .stat-content {
    flex: 1;
    min-width: 0;
    
    .stat-value {
      display: flex;
      align-items: baseline;
      gap: 4px;
      
      .value-number {
        font-size: 28px;
        font-weight: 600;
        line-height: 1.2;
      }
      
      .value-suffix {
        font-size: 14px;
        color: #909399;
      }
    }
    
    .stat-label {
      font-size: 14px;
      color: #909399;
      margin-top: 4px;
    }
    
    .stat-trend {
      display: flex;
      align-items: center;
      gap: 4px;
      margin-top: 8px;
      font-size: 13px;
      
      .up {
        color: #67c23a;
      }
      
      .down {
        color: #f56c6c;
      }
    }
  }
}
</style>
