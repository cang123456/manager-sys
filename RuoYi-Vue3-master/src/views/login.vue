<template>
  <div class="login">
    <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form">
      <h3 class="title">{{ title }}</h3>
      <el-form-item prop="username">
        <el-input
          v-model="loginForm.username"
          type="text"
          size="large"
          auto-complete="off"
          placeholder="账号"
        >
          <template #prefix><svg-icon icon-class="user" class="el-input__icon input-icon" /></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="password">
        <el-input
          v-model="loginForm.password"
          type="password"
          size="large"
          auto-complete="off"
          placeholder="密码"
          @keyup.enter="handleLogin"
        >
          <template #prefix><svg-icon icon-class="password" class="el-input__icon input-icon" /></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="code" v-if="captchaEnabled">
        <el-input
          v-model="loginForm.code"
          size="large"
          auto-complete="off"
          placeholder="验证码"
          style="width: 62%"
          @keyup.enter="handleLogin"
        >
          <template #prefix><svg-icon icon-class="validCode" class="el-input__icon input-icon" /></template>
        </el-input>
        <div class="login-code">
          <img :src="codeUrl" @click="getCode" class="login-code-img"/>
        </div>
      </el-form-item>
      <el-checkbox v-model="loginForm.rememberMe" class="remember">记住密码</el-checkbox>
      <el-form-item class="submit-item">
        <el-button
          :loading="loading"
          size="large"
          type="primary"
          class="submit-btn"
          @click.prevent="handleLogin"
        >
          <span v-if="!loading">登 录</span>
          <span v-else>登 录 中...</span>
        </el-button>
        <div class="register-link" v-if="register">
          <router-link class="link-type" :to="'/register'">立即注册</router-link>
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { getCodeImg } from "@/api/login"
import Cookies from "js-cookie"
import { encrypt, decrypt } from "@/utils/jsencrypt"
import useUserStore from '@/store/modules/user'

const title = import.meta.env.VITE_APP_TITLE
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()

const loginForm = ref({
  username: "admin",
  password: "admin123",
  rememberMe: false,
  code: "",
  uuid: ""
})

const loginRules = {
  username: [{ required: true, trigger: "blur", message: "请输入您的账号" }],
  password: [{ required: true, trigger: "blur", message: "请输入您的密码" }],
  code: [{ required: true, trigger: "change", message: "请输入验证码" }]
}

const codeUrl = ref("")
const loading = ref(false)
// 验证码开关
const captchaEnabled = ref(true)
// 注册开关
const register = ref(false)
const redirect = ref(undefined)

watch(route, (newRoute) => {
    redirect.value = newRoute.query && newRoute.query.redirect
}, { immediate: true })

function handleLogin() {
  proxy.$refs.loginRef.validate(valid => {
    if (valid) {
      loading.value = true
      // 勾选了需要记住密码设置在 cookie 中设置记住用户名和密码
      if (loginForm.value.rememberMe) {
        Cookies.set("username", loginForm.value.username, { expires: 30 })
        Cookies.set("password", encrypt(loginForm.value.password), { expires: 30 })
        Cookies.set("rememberMe", loginForm.value.rememberMe, { expires: 30 })
      } else {
        // 否则移除
        Cookies.remove("username")
        Cookies.remove("password")
        Cookies.remove("rememberMe")
      }
      // 调用action的登录方法
      userStore.login(loginForm.value).then(() => {
        const query = route.query
        const otherQueryParams = Object.keys(query).reduce((acc, cur) => {
          if (cur !== "redirect") {
            acc[cur] = query[cur]
          }
          return acc
        }, {})
        router.push({ path: redirect.value || "/", query: otherQueryParams })
      }).catch(() => {
        loading.value = false
        // 重新获取验证码
        if (captchaEnabled.value) {
          getCode()
        }
      })
    }
  })
}

function getCode() {
  getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled
    if (captchaEnabled.value) {
      codeUrl.value = "data:image/gif;base64," + res.img
      loginForm.value.uuid = res.uuid
    }
  })
}

function getCookie() {
  const username = Cookies.get("username")
  const password = Cookies.get("password")
  const rememberMe = Cookies.get("rememberMe")
  loginForm.value = {
    username: username === undefined ? loginForm.value.username : username,
    password: password === undefined ? loginForm.value.password : decrypt(password),
    rememberMe: rememberMe === undefined ? false : Boolean(rememberMe)
  }
}

getCode()
getCookie()
</script>

<style lang='scss' scoped>
// ============================================================
// 登录页 · Apple 风
// 整屏中性灰 + 居中白卡 + 单一强调蓝
// ============================================================
.login {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 32px 20px;
  background: var(--ap-bg);
  overflow: hidden;

  /* 两团极淡的柔光，给纯色底一点层次，不喧宾夺主 */
  &::before,
  &::after {
    content: "";
    position: absolute;
    border-radius: 50%;
    filter: blur(90px);
    opacity: 0.5;
    pointer-events: none;
  }

  &::before {
    width: 620px;
    height: 620px;
    top: -240px;
    left: 50%;
    transform: translateX(-72%);
    background: radial-gradient(circle, rgba(0, 113, 227, 0.16), transparent 68%);
  }

  &::after {
    width: 520px;
    height: 520px;
    bottom: -220px;
    right: 4%;
    background: radial-gradient(circle, rgba(175, 82, 222, 0.1), transparent 68%);
  }
}

.login-form {
  position: relative;
  z-index: 1;
  width: 384px;
  max-width: 100%;
  padding: 44px 40px 36px;
  background: var(--ap-surface);
  border: 1px solid var(--ap-hairline);
  border-radius: var(--ap-radius-xl);
  box-shadow: var(--ap-shadow-md);
  box-sizing: border-box;

  .el-form-item {
    margin-bottom: 18px;
  }

  .el-input {
    height: 42px;

    input {
      height: 42px;
      font-size: 15px;
    }
  }

  .input-icon {
    height: 40px;
    width: 14px;
    margin-left: 0;
    color: var(--ap-text-quaternary);
  }

  .el-input__wrapper {
    height: 42px;
    border-radius: var(--ap-radius-md);
    padding: 0 14px;
  }
}

.title {
  margin: 0 0 28px;
  text-align: center;
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.03em;
  line-height: 1.2;
  color: var(--ap-text);
}

.remember {
  margin: 0 0 20px 0;
  height: 22px;

  :deep(.el-checkbox__label) {
    font-size: 13px;
    color: var(--ap-text-secondary);
  }
}

.submit-item {
  width: 100%;
  margin-bottom: 0 !important;
}

.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  border-radius: var(--ap-radius-md);
}

.register-link {
  float: right;
  margin-top: 12px;
  font-size: 13px;
}

.login-code {
  width: 36%;
  height: 42px;
  float: right;
  display: flex;
  justify-content: flex-end;
  align-items: center;

  img {
    cursor: pointer;
    vertical-align: middle;
  }
}

.login-code-img {
  height: 42px;
  width: 100%;
  object-fit: contain;
  border-radius: var(--ap-radius-md);
  border: 1px solid var(--ap-hairline);
  box-sizing: border-box;
  background: var(--ap-surface-muted);
}

html.dark .login {
  &::before {
    background: radial-gradient(circle, rgba(10, 132, 255, 0.24), transparent 68%);
  }

  .login-form {
    background: var(--ap-surface);
    box-shadow: var(--ap-shadow-md);
  }
}
</style>
