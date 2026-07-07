<template>
  <div class="forbidden-container">
    <el-result
      icon="warning"
      title="403"
      sub-title="抱歉，您没有权限访问此页面"
    >
      <template #extra>
        <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
</script>

<style scoped>
.forbidden-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}
</style>
