<template>
  <section class="app-main">
    <!--
      注意：这里刻意不使用 <transition mode="out-in">。
      原因：TagsView 会在路由变化的同一 tick 里把新页面加入 cachedViews，
      keep-alive 的 include 随之变化会打断 out-in 的离场钩子，
      导致新页面永远停在 enter-from（opacity:0）——表现为「点菜单空白，F5 后才出现」。
      页面切换直接瞬时替换，更稳也更快。
    -->
    <router-view v-slot="{ Component, route }">
      <keep-alive :include="tagsViewStore.cachedViews">
        <component v-if="!route.meta.link" :is="Component" :key="route.path"/>
      </keep-alive>
    </router-view>
    <iframe-toggle />
    <copyright />
  </section>
</template>

<script setup>
import copyright from "./Copyright/index"
import iframeToggle from "./IframeToggle/index"
import useTagsViewStore from '@/store/modules/tagsView'

const route = useRoute()
const tagsViewStore = useTagsViewStore()

onMounted(() => {
  addIframe()
})

watchEffect(() => {
  addIframe()
})

function addIframe() {
  if (route.meta.link) {
    useTagsViewStore().addIframeView(route)
  }
}
</script>

<style lang="scss" scoped>
.app-main {
  /* 56 = navbar */
  min-height: calc(100vh - var(--ap-nav-h));
  width: 100%;
  position: relative;
  overflow: hidden;
  background: var(--ap-bg);
}

.fixed-header + .app-main {
  overflow-y: auto;
  scrollbar-gutter: auto;
  height: calc(100vh - var(--ap-nav-h));
  min-height: 0px;
  margin-top: var(--ap-nav-h);
}

.app-main:has(.copyright) {
  padding-bottom: 36px;
}

.hasTagsView {
  .app-main {
    /* 96 = navbar + tags-view = 56 + 40 */
    min-height: calc(100vh - var(--ap-nav-h) - var(--ap-tags-h));
  }

  .fixed-header + .app-main {
    margin-top: calc(var(--ap-nav-h) + var(--ap-tags-h));
    height: calc(100vh - var(--ap-nav-h) - var(--ap-tags-h));
    min-height: 0px;
  }
}

/* 移动端 fixed-header 优化 */
@media screen and (max-width: 991px) {
  .fixed-header + .app-main {
    padding-bottom: max(60px, calc(constant(safe-area-inset-bottom) + 40px));
    padding-bottom: max(60px, calc(env(safe-area-inset-bottom) + 40px));
    overscroll-behavior-y: none;
  }

  .hasTagsView .fixed-header + .app-main {
    padding-bottom: max(60px, calc(constant(safe-area-inset-bottom) + 40px));
    padding-bottom: max(60px, calc(env(safe-area-inset-bottom) + 40px));
    overscroll-behavior-y: none;
  }
}

@supports (-webkit-touch-callout: none) {
  @media screen and (max-width: 991px) {
    .fixed-header + .app-main {
      padding-bottom: max(17px, calc(constant(safe-area-inset-bottom) + 10px));
      padding-bottom: max(17px, calc(env(safe-area-inset-bottom) + 10px));
      height: calc(100svh - var(--ap-nav-h));
      height: calc(100dvh - var(--ap-nav-h));
    }

    .hasTagsView .fixed-header + .app-main {
      padding-bottom: max(17px, calc(constant(safe-area-inset-bottom) + 10px));
      padding-bottom: max(17px, calc(env(safe-area-inset-bottom) + 10px));
      height: calc(100svh - var(--ap-nav-h) - var(--ap-tags-h));
      height: calc(100dvh - var(--ap-nav-h) - var(--ap-tags-h));
    }
  }
}
</style>

<style lang="scss">
/* 滚动条交由 apple.scss 统一处理，这里只保留必要的兜底 */
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-track {
  background-color: transparent;
}

::-webkit-scrollbar-thumb {
  background-color: rgba(0, 0, 0, 0.16);
  border-radius: 8px;
}

html.dark ::-webkit-scrollbar-thumb {
  background-color: rgba(255, 255, 255, 0.2);
}
</style>
