<template>
  <div class="navbar" :class="'nav' + settingsStore.navType">
    <div class="navbar-left">
      <hamburger id="hamburger-container" :is-active="appStore.sidebar.opened" class="hamburger-container" @toggleClick="toggleSideBar" />
      <breadcrumb v-if="settingsStore.navType == 1" id="breadcrumb-container" class="breadcrumb-container" />
      <top-nav v-if="settingsStore.navType == 2" id="topmenu-container" class="topmenu-container" />
      <template v-if="settingsStore.navType == 3">
        <logo v-show="settingsStore.sidebarLogo" :collapse="false"></logo>
        <top-bar id="topbar-container" class="topbar-container" />
      </template>
    </div>

    <div class="right-menu">
      <template v-if="appStore.device !== 'mobile'">
        <header-search id="header-search" class="right-menu-item" />

        <screenfull id="screenfull" class="right-menu-item" />

        <div id="theme-switch" class="right-menu-item" @click="toggleTheme" title="主题模式">
          <svg-icon v-if="settingsStore.isDark" icon-class="sunny" />
          <svg-icon v-if="!settingsStore.isDark" icon-class="moon" />
        </div>

        <size-select id="size-select" class="right-menu-item" />

        <header-notice id="header-notice" class="right-menu-item" />
      </template>

      <span class="right-menu-divider"></span>

      <el-dropdown @command="handleCommand" class="avatar-container right-menu-item avatar-item" trigger="hover">
        <div class="avatar-wrapper">
          <img :src="userStore.avatar" class="user-avatar" />
          <span class="user-nickname">{{ userStore.nickName }}</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <router-link to="/user/profile">
              <el-dropdown-item>个人中心</el-dropdown-item>
            </router-link>
            <el-dropdown-item command="setLayout" v-if="settingsStore.showSettings">
              <span>布局设置</span>
            </el-dropdown-item>
            <el-dropdown-item command="lockScreen">
              <span>锁定屏幕</span>
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <span>退出登录</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { ElMessageBox } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb'
import TopNav from './TopNav'
import TopBar from './TopBar'
import Logo from './Sidebar/Logo'
import Hamburger from '@/components/Hamburger'
import Screenfull from '@/components/Screenfull'
import SizeSelect from '@/components/SizeSelect'
import HeaderSearch from '@/components/HeaderSearch'
import useAppStore from '@/store/modules/app'
import useUserStore from '@/store/modules/user'
import useLockStore from '@/store/modules/lock'
import useSettingsStore from '@/store/modules/settings'
import HeaderNotice from './HeaderNotice'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const lockStore = useLockStore()
const settingsStore = useSettingsStore()

function toggleSideBar() {
  appStore.toggleSideBar()
}

function handleCommand(command) {
  switch (command) {
    case "setLayout":
      setLayout()
      break
    case "lockScreen":
      lockScreen()
      break
    case "logout":
      logout()
      break
    default:
      break
  }
}

function logout() {
  ElMessageBox.confirm('确定注销并退出系统吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    userStore.logOut().then(() => {
      location.href = '/index'
    })
  }).catch(() => { })
}

const emits = defineEmits(['setLayout'])
function setLayout() {
  emits('setLayout')
}

function lockScreen() {
  const currentPath = route.fullPath
  lockStore.lockScreen(currentPath)
  router.push('/lock')
}

async function toggleTheme(event) {
  const x = event?.clientX || window.innerWidth / 2
  const y = event?.clientY || window.innerHeight / 2
  const wasDark = settingsStore.isDark

  const isReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches
  const isSupported = document.startViewTransition && !isReducedMotion

  if (!isSupported) {
    settingsStore.toggleTheme()
    return
  }

  try {
    const transition = document.startViewTransition(async () => {
      await new Promise((resolve) => setTimeout(resolve, 10))
      settingsStore.toggleTheme()
      await nextTick()
    })
    await transition.ready

    const endRadius = Math.hypot(Math.max(x, window.innerWidth - x), Math.max(y, window.innerHeight - y))
    const clipPath = [`circle(0px at ${x}px ${y}px)`, `circle(${endRadius}px at ${x}px ${y}px)`]
    document.documentElement.animate(
      {
        clipPath: !wasDark ? [...clipPath].reverse() : clipPath
      }, {
      duration: 650,
      easing: "cubic-bezier(0.4, 0, 0.2, 1)",
      fill: "forwards",
      pseudoElement: !wasDark ? "::view-transition-old(root)" : "::view-transition-new(root)"
    }
    )
    await transition.finished
  } catch (error) {
    console.warn("View transition failed, falling back to immediate toggle:", error)
    settingsStore.toggleTheme()
  }
}
</script>

<style lang='scss' scoped>
.navbar {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--ap-nav-h);
  padding: 0 20px 0 12px;
  box-sizing: border-box;
  background: var(--navbar-bg);
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  border: none;
  box-shadow: none;
  overflow: hidden;

  .navbar-left {
    display: flex;
    align-items: center;
    min-width: 0;
    gap: 4px;
  }

  .hamburger-container {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    border-radius: 50%;
    cursor: pointer;
    color: var(--ap-text-secondary);
    transition: background-color 0.2s var(--ap-ease), color 0.2s var(--ap-ease);
    -webkit-tap-highlight-color: transparent;
    flex-shrink: 0;

    &:hover {
      background: var(--ap-fill);
      color: var(--ap-text);
    }
  }

  .breadcrumb-container {
    flex-shrink: 0;
    margin-left: 4px;
  }

  .topmenu-container {
    position: absolute;
    left: 56px;
  }

  .topbar-container {
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: center;
    overflow: hidden;
    margin-left: 8px;
  }

  // ---------------- 右侧操作区 ----------------
  .right-menu {
    display: flex;
    align-items: center;
    gap: 2px;
    height: 100%;
    margin-left: auto;

    &:focus {
      outline: none;
    }

    .right-menu-item {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 34px;
      height: 34px;
      border-radius: 50%;
      font-size: 17px;
      color: var(--ap-text-secondary);
      cursor: pointer;
      transition: background-color 0.2s var(--ap-ease), color 0.2s var(--ap-ease);

      &:hover {
        background: var(--ap-fill);
        color: var(--ap-text);
      }

      svg {
        transition: transform 0.3s var(--ap-ease);
      }

      &:hover svg {
        transform: scale(1.06);
      }
    }
  }

  .right-menu-divider {
    width: 1px;
    height: 18px;
    margin: 0 10px;
    background: var(--ap-hairline);
  }

  .avatar-container {
    width: auto !important;
    padding-right: 0;

    .avatar-wrapper {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 3px 12px 3px 3px;
      border-radius: var(--ap-radius-pill);
      cursor: pointer;
      transition: background-color 0.2s var(--ap-ease);

      &:hover {
        background: var(--ap-fill);
      }

      .user-avatar {
        width: 28px;
        height: 28px;
        border-radius: 50%;
        object-fit: cover;
        border: 1px solid var(--ap-hairline);
      }

      .user-nickname {
        font-size: 13px;
        font-weight: 500;
        letter-spacing: -0.01em;
        color: var(--ap-text);
        max-width: 120px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        line-height: 1.2;
      }
    }
  }

  &.nav3 {
    .hamburger-container {
      display: none !important;
    }
  }
}
</style>
