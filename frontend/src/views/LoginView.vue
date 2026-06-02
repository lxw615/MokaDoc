<template>
  <div class="login-container">
    <div class="login-left">
      <div class="logo">
        <h1>智能文档系统</h1>
        <p>让知识触手可及</p>
      </div>
    </div>
    <div class="login-right">
      <div class="form-tabs">
        <button
          class="tab"
          :class="{ active: activeTab === 'login' }"
          @click="activeTab = 'login'"
        >
          登录
        </button>
        <button
          class="tab"
          :class="{ active: activeTab === 'register' }"
          @click="activeTab = 'register'"
        >
          注册
        </button>
      </div>
      <div class="form-content">
        <!-- 登录表单 -->
        <div v-if="activeTab === 'login'" class="form active">
          <div class="form-group">
            <label>用户名/邮箱</label>
            <input
              type="text"
              v-model="loginForm.account"
              placeholder="请输入用户名或邮箱"
            >
          </div>
          <div class="form-group">
            <label>密码</label>
            <input
              type="password"
              v-model="loginForm.password"
              placeholder="请输入密码"
            >
          </div>
          <div class="form-options">
            <label>
              <input type="checkbox" v-model="loginForm.rememberMe">
              记住我
            </label>
            <a href="#">忘记密码</a>
          </div>
          <button class="btn btn-primary" :disabled="loginLoading" @click="handleLogin">
            {{ loginLoading ? '登录中...' : '登录' }}
          </button>
        </div>

        <!-- 注册表单 -->
        <div v-if="activeTab === 'register'" class="form active">
          <div class="form-group">
            <label>用户名</label>
            <input
              type="text"
              v-model="registerForm.username"
              placeholder="请输入用户名（4-50个字符）"
            >
          </div>
          <div class="form-group">
            <label>邮箱</label>
            <input
              type="email"
              v-model="registerForm.email"
              placeholder="请输入邮箱"
            >
          </div>
          <div class="form-group">
            <label>密码</label>
            <input
              type="password"
              v-model="registerForm.password"
              placeholder="请输入密码（至少6位）"
            >
          </div>
          <div class="form-group">
            <label>确认密码</label>
            <input
              type="password"
              v-model="registerForm.confirmPassword"
              placeholder="请确认密码"
            >
          </div>
          <button class="btn btn-primary" :disabled="registerLoading" @click="handleRegister">
            {{ registerLoading ? '注册中...' : '注册' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { userLogin, userRegister } from '@/api/yonghuguanli'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const activeTab = ref('login')
const loginLoading = ref(false)
const registerLoading = ref(false)

// 登录表单数据
const loginForm = reactive({
  account: '',
  password: '',
  rememberMe: false
})

// 注册表单数据
const registerForm = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})

// 处理登录
const handleLogin = async () => {
  if (!loginForm.account || !loginForm.password) {
    message.warning('请输入用户名/邮箱和密码')
    return
  }

  loginLoading.value = true
  try {
    const body = {
      account: loginForm.account,
      password: loginForm.password,
    }
    console.log('[Login] 发送登录请求:', JSON.stringify(body))
    const res = await userLogin(body)
    console.log('[Login] 收到响应:', JSON.stringify(res.data))
    if (res.data.code === 0 && res.data.data?.user) {
      userStore.setUser(res.data.data.user)
      message.success('登录成功')
      router.push('/document-management')
    } else {
      console.warn('[Login] 登录失败:', res.data)
      message.error(res.data.message || '登录失败')
    }
  } catch (err) {
    console.error('[Login] 请求异常:', err)
    message.error('网络错误，请稍后重试')
  } finally {
    loginLoading.value = false
  }
}

// 处理注册
const handleRegister = async () => {
  if (!registerForm.username || !registerForm.email || !registerForm.password) {
    message.warning('请填写所有必填字段')
    return
  }
  if (registerForm.username.length < 4 || registerForm.username.length > 50) {
    message.warning('用户名长度应为4-50个字符')
    return
  }
  if (registerForm.password.length < 6) {
    message.warning('密码长度至少为6位')
    return
  }
  if (registerForm.password !== registerForm.confirmPassword) {
    message.warning('两次输入的密码不一致')
    return
  }

  registerLoading.value = true
  try {
    const res = await userRegister({
      username: registerForm.username,
      email: registerForm.email,
      password: registerForm.password,
    })
    if (res.data.code === 0) {
      message.success('注册成功！请登录')
      activeTab.value = 'login'
    } else {
      message.error(res.data.message || '注册失败')
    }
  } catch {
    message.error('网络错误，请稍后重试')
  } finally {
    registerLoading.value = false
  }
}
</script>

<style scoped>
.login-container {
    display: flex;
    height: 100vh;
    background: linear-gradient(135deg, #E8E4DB, #D5D9E8, #E3DFD4, #CBD0E4);
    background-size: 400% 400%;
    animation: gradientShift 18s ease infinite;
}

@keyframes gradientShift {
    0%   { background-position: 0% 50%; }
    50%  { background-position: 100% 50%; }
    100% { background-position: 0% 50%; }
}

.login-left {
    flex: 0 0 60%;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 40px;
}

.login-left .logo {
    animation: fadeInUp 0.7s cubic-bezier(0.4, 0, 0.2, 1) both;
}

@keyframes fadeInUp {
    from { opacity: 0; transform: translateY(18px); }
    to   { opacity: 1; transform: translateY(0); }
}

.login-left .logo h1 {
    font-size: 36px;
    color: var(--primary-color);
    margin-bottom: 16px;
    letter-spacing: 2px;
}

.login-left .logo p {
    font-size: 18px;
    color: var(--text-secondary);
}

.login-right {
    flex: 0 0 40%;
    max-width: 500px;
    min-width: 350px;
    background: white;
    padding: 40px;
    display: flex;
    flex-direction: column;
    justify-content: center;
    box-shadow: 0 16px 48px rgba(44, 44, 58, 0.12);
    animation: slideInRight 0.6s cubic-bezier(0.4, 0, 0.2, 1) 0.15s both;
}

@keyframes slideInRight {
    from { opacity: 0; transform: translateX(24px); }
    to   { opacity: 1; transform: translateX(0); }
}

.form-tabs {
    display: flex;
    margin-bottom: 30px;
}

.tab {
    flex: 1;
    padding: 10px;
    border: none;
    background: none;
    font-size: 16px;
    color: var(--text-secondary);
    cursor: pointer;
    border-bottom: 2px solid var(--border-color, #E6E3DC);
    transition: color 0.28s cubic-bezier(0.4, 0, 0.2, 1),
                border-color 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.tab:hover {
    color: var(--primary-light, #7B88D1);
}

.tab.active {
    color: var(--primary-color);
    border-bottom-color: var(--primary-color);
    font-weight: 600;
}

.form {
    display: none;
}

.form.active {
    display: block;
    animation: fadeInUp 0.4s cubic-bezier(0.4, 0, 0.2, 1) both;
}

.form-group {
    margin-bottom: 20px;
}

.form-group label {
    display: block;
    margin-bottom: 8px;
    font-weight: 500;
}

.form-group input {
    width: 100%;
    padding: 10px 14px;
    border: 1.5px solid var(--border-color, #E6E3DC);
    border-radius: 6px;
    font-size: 14px;
    background: #F9F7F3;
    transition: border-color 0.28s cubic-bezier(0.4, 0, 0.2, 1),
                box-shadow 0.28s cubic-bezier(0.4, 0, 0.2, 1),
                background 0.28s cubic-bezier(0.4, 0, 0.2, 1);
    outline: none;
}

.form-group input:focus {
    border-color: var(--primary-color);
    box-shadow: 0 0 0 3px rgba(76, 91, 168, 0.18);
    background: white;
}

.form-options {
    display: flex;
    justify-content: space-between;
    margin-bottom: 30px;
}

.form-options label {
    display: flex;
    align-items: center;
    cursor: pointer;
}

.form-options input[type="checkbox"] {
    margin-right: 8px;
    accent-color: var(--primary-color);
}

.form-options a {
    color: var(--primary-color);
    text-decoration: none;
    position: relative;
    transition: color 0.18s cubic-bezier(0.4, 0, 0.2, 1);
}

.form-options a::after {
    content: '';
    position: absolute;
    bottom: -2px;
    left: 0;
    width: 100%;
    height: 1px;
    background: var(--primary-color);
    transform: scaleX(0);
    transform-origin: right;
    transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.form-options a:hover::after {
    transform: scaleX(1);
    transform-origin: left;
}

.btn-primary:disabled {
    opacity: 0.55;
    cursor: not-allowed;
}

@media (max-width: 1024px) {
    .login-left {
        flex: 0 0 50%;
    }

    .login-right {
        flex: 0 0 50%;
    }
}

@media (max-width: 768px) {
    .login-container {
        flex-direction: column;
    }

    .login-left {
        flex: 0 0 auto;
        padding: 30px 20px;
        min-height: 200px;
    }

    .login-left .logo h1 {
        font-size: 28px;
    }

    .login-left .logo p {
        font-size: 16px;
    }

    .login-right {
        flex: 1;
        width: 100%;
        max-width: 100%;
        min-width: auto;
        height: auto;
        padding: 30px 20px;
    }
}
</style>
