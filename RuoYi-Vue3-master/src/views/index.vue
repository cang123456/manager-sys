<template>
  <!-- 首页内容已清空，等待后续需求 -->
  <div class="page-blank"></div>
</template>

<script setup name="Index">
// 显式声明组件名：keep-alive 的 include 依据组件 name 匹配，缺省会缓存失效
defineOptions({ name: 'Index' })
</script>

<style lang="scss" scoped>
.page-blank {
  min-height: calc(100vh - var(--ap-nav-h) - var(--ap-tags-h));
  width: 100%;
  background: var(--ap-bg);
}
</style>
