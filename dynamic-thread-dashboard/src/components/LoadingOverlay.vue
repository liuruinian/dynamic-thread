<template>
  <div class="loading-overlay" v-if="loading">
    <div class="loading-content">
      <el-icon class="loading-icon" :size="48">
        <Loading />
      </el-icon>
      <div class="loading-text">{{ text }}</div>
    </div>
  </div>
  <div v-else>
    <slot></slot>
  </div>
</template>

<script setup>
import { Loading } from '@element-plus/icons-vue'

defineProps({
  loading: {
    type: Boolean,
    default: false
  },
  text: {
    type: String,
    default: '加载中...'
  }
})
</script>

<style lang="scss" scoped>
.loading-overlay {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  
  .loading-content {
    text-align: center;
    
    .loading-icon {
      animation: rotating 1.5s linear infinite;
      color: #409eff;
    }
    
    .loading-text {
      margin-top: 12px;
      color: #909399;
      font-size: 14px;
    }
  }
}

@keyframes rotating {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
