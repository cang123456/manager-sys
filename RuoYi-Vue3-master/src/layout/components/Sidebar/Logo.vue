<template>
  <div class="sidebar-logo-container" :class="{ 'collapse': collapse }">
    <transition name="sidebarLogoFade">
      <router-link v-if="collapse" key="collapse" class="sidebar-logo-link" to="/">
        <span class="logo-mark" aria-hidden="true"></span>
      </router-link>
      <router-link v-else key="expand" class="sidebar-logo-link" to="/">
        <span class="logo-mark" aria-hidden="true"></span>
        <span class="sidebar-title">{{ title }}</span>
      </router-link>
    </transition>
  </div>
</template>

<script setup>
defineProps({
  collapse: {
    type: Boolean,
    required: true
  }
})

const title = import.meta.env.VITE_APP_TITLE
</script>

<style lang="scss" scoped>
.sidebarLogoFade-enter-active {
  transition: opacity 0.6s var(--ap-ease);
}

.sidebarLogoFade-enter,
.sidebarLogoFade-leave-to {
  opacity: 0;
}

.sidebar-logo-container {
  position: relative;
  display: flex;
  align-items: center;
  height: 64px;
  padding: 0 16px;
  background: transparent;
  overflow: hidden;

  .sidebar-logo-link {
    display: flex;
    align-items: center;
    gap: 10px;
    height: 100%;
    width: 100%;
    text-decoration: none;

    /* 纯几何标记：圆角深色块 + 2×2 白点，不用任何字形 */
    .logo-mark {
      position: relative;
      flex-shrink: 0;
      width: 26px;
      height: 26px;
      border-radius: 8px;
      background: linear-gradient(160deg, #323236 0%, #1d1d1f 100%);
      box-shadow: inset 0 0.5px 0 rgba(255, 255, 255, 0.2),
        0 1px 2px rgba(0, 0, 0, 0.14);
      transition: transform 0.3s var(--ap-ease-out);
    }

    .logo-mark::before {
      content: "";
      position: absolute;
      top: 9px;
      left: 9px;
      width: 3px;
      height: 3px;
      border-radius: 1px;
      background: rgba(255, 255, 255, 0.96);
      box-shadow: 5px 0 0 rgba(255, 255, 255, 0.96),
        0 5px 0 rgba(255, 255, 255, 0.96),
        5px 5px 0 rgba(255, 255, 255, 0.96);
    }

    &:hover .logo-mark {
      transform: scale(1.05);
    }

    .sidebar-title {
      display: inline-block;
      margin: 0;
      font-size: 15px;
      font-weight: 600;
      letter-spacing: -0.02em;
      line-height: 1.2;
      color: var(--ap-text);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }

  &.collapse {
    padding: 0;
    justify-content: center;

    .sidebar-logo-link {
      justify-content: center;
      gap: 0;
    }
  }
}

/* 暗色：深色块反转为浅色块 + 深色点 */
html.dark .logo-mark {
  background: linear-gradient(160deg, #f5f5f7 0%, #d2d2d7 100%);
  box-shadow: inset 0 0.5px 0 rgba(255, 255, 255, 0.6);

  &::before {
    background: #1d1d1f;
    box-shadow: 5px 0 0 #1d1d1f, 0 5px 0 #1d1d1f, 5px 5px 0 #1d1d1f;
  }
}
</style>
