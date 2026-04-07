<template>
  <div class="empty-state">
    <el-empty :description="description" :image-size="imageSize">
      <template #image v-if="$slots.image">
        <slot name="image"></slot>
      </template>
      <template #default v-if="$slots.default">
        <slot></slot>
      </template>
    </el-empty>
  </div>
</template>

<script setup>
defineProps({
  description: {
    type: String,
    default: '暂无数据'
  },
  imageSize: {
    type: Number,
    default: 120
  }
})
</script>

<style lang="scss" scoped>
.empty-state {
  padding: 40px 0;
}
</style>
