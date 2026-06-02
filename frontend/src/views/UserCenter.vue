<template>
  <div id="user-center" class="content-page active">
    <div class="user-container">
      <!-- 左侧导航 -->
      <div class="user-sidebar">
        <a 
          href="#" 
          class="user-nav-item"
          :class="{ active: activeTab === 'profile' }"
          @click.prevent="activeTab = 'profile'"
        >
          个人信息
        </a>
        <a 
          href="#" 
          class="user-nav-item"
          :class="{ active: activeTab === 'password' }"
          @click.prevent="activeTab = 'password'"
        >
          修改密码
        </a>
        <a 
          href="#" 
          class="user-nav-item"
          :class="{ active: activeTab === 'logs' }"
          @click.prevent="activeTab = 'logs'"
        >
          操作日志
        </a>
        <a 
          href="#" 
          class="user-nav-item"
          :class="{ active: activeTab === 'storage' }"
          @click.prevent="activeTab = 'storage'"
        >
          存储空间
        </a>
      </div>

      <!-- 右侧内容区 -->
      <div class="user-content">
        <!-- 个人信息 -->
        <div v-if="activeTab === 'profile'" class="user-info-section">
          <h3>个人信息</h3>
          <div class="user-info-form">
            <div class="form-group">
              <label>用户名</label>
              <input type="text" v-model="userInfo.username" :disabled="profileLoading || savingProfile">
            </div>
            <div class="form-group">
              <label>邮箱</label>
              <input type="email" v-model="userInfo.email" :disabled="profileLoading || savingProfile">
            </div>
            <div class="form-group">
              <label>昵称</label>
              <input type="text" v-model="userInfo.nickname" :disabled="profileLoading || savingProfile">
            </div>
            <div class="form-group">
              <label>注册时间</label>
              <input type="text" v-model="userInfo.registerDate" disabled>
            </div>
            <button class="btn btn-primary" :disabled="savingProfile" @click="saveProfile">
              {{ savingProfile ? '保存中...' : '保存修改' }}
            </button>
          </div>
        </div>

        <!-- 修改密码 -->
        <div v-if="activeTab === 'password'" class="user-info-section">
          <h3>修改密码</h3>
          <div class="user-info-form">
            <div class="form-group">
              <label>当前密码</label>
              <input type="password" v-model="passwordForm.currentPassword" placeholder="请输入当前密码" :disabled="savingPassword">
            </div>
            <div class="form-group">
              <label>新密码</label>
              <input type="password" v-model="passwordForm.newPassword" placeholder="请输入新密码" :disabled="savingPassword">
            </div>
            <div class="form-group">
              <label>确认新密码</label>
              <input type="password" v-model="passwordForm.confirmPassword" placeholder="请确认新密码" :disabled="savingPassword">
            </div>
            <button class="btn btn-primary" :disabled="savingPassword" @click="changePassword">
              {{ savingPassword ? '修改中...' : '修改密码' }}
            </button>
          </div>
        </div>

        <!-- 操作日志 -->
        <div v-if="activeTab === 'logs'" class="user-info-section">
          <h3>操作日志</h3>
          <div class="log-list">
            <div v-if="logsLoading" class="user-empty">正在加载操作日志...</div>
            <div v-else-if="operationLogs.length === 0" class="user-empty">暂无操作日志</div>
            <template v-else>
              <div v-for="log in operationLogs" :key="log.id" class="log-item">
                <div class="log-time">{{ log.time }}</div>
                <div class="log-action">{{ log.action }}</div>
                <div class="log-ip">{{ log.source }}</div>
              </div>
            </template>
          </div>
        </div>

        <!-- 存储空间 -->
        <div v-if="activeTab === 'storage'" class="user-info-section">
          <h3>存储空间</h3>
          <div class="storage-info">
            <div class="storage-bar">
              <div 
                class="storage-used" 
                :style="{ width: storageUsage + '%' }"
              ></div>
            </div>
            <div class="storage-text">
              已使用: {{ usedStorage }} / {{ totalStorage }}
            </div>
            <div class="storage-detail">
              <p>文档数量: {{ documentCount }}</p>
              <p>平均文档大小: {{ averageSize }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { getCurrentUser, updateProfile, updatePassword, listOperationLogs, getStorageSummary } from '@/api/yonghuguanli'
import { useUserStore } from '@/stores/user'

interface UserInfo {
  username: string
  email: string
  nickname: string
  registerDate: string
}

interface PasswordForm {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

interface OperationLog {
  id: number
  time: string
  action: string
  source: string
}

const activeTab = ref('profile')
const userStore = useUserStore()

const profileLoading = ref(false)
const savingProfile = ref(false)
const savingPassword = ref(false)
const logsLoading = ref(false)

// 用户信息
const userInfo = reactive<UserInfo>({
  username: '',
  email: '',
  nickname: '',
  registerDate: ''
})

// 密码表单
const passwordForm = reactive<PasswordForm>({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 操作日志
const operationLogs = ref<OperationLog[]>([])

// 存储信息
const usedStorage = ref('0B')
const totalStorage = ref('1GB')
const storageUsage = ref(0)
const documentCount = ref(0)
const averageSize = ref('0B')

const applyUserInfo = (user?: API.UserVO | null) => {
  if (!user) return
  userInfo.username = user.username || ''
  userInfo.email = user.email || ''
  userInfo.nickname = user.nickname || user.username || ''
  userInfo.registerDate = formatTime(user.registerTime)
}

const formatTime = (value?: string) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value.replace('T', ' ').slice(0, 19)
  }
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const formatBytes = (bytes?: number) => {
  const value = Number(bytes || 0)
  if (value < 1024) return `${value}B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let size = value / 1024
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex++
  }
  return `${size.toFixed(size >= 10 ? 1 : 2)}${units[unitIndex]}`
}

const loadProfile = async () => {
  profileLoading.value = true
  try {
    applyUserInfo(userStore.currentUser)
    const res = await getCurrentUser()
    if (res.data.code === 0 && res.data.data) {
      userStore.setUser(res.data.data)
      applyUserInfo(res.data.data)
    } else {
      message.error(res.data.message || '获取用户信息失败')
    }
  } catch {
    message.error('获取用户信息失败')
  } finally {
    profileLoading.value = false
  }
}

const loadLogs = async () => {
  logsLoading.value = true
  try {
    const res = await listOperationLogs()
    if (res.data.code === 0 && res.data.data) {
      operationLogs.value = res.data.data.map((log, index) => ({
        id: log.id || index + 1,
        time: formatTime(log.time),
        action: log.action || '',
        source: log.source || '系统',
      }))
    } else {
      operationLogs.value = []
    }
  } catch {
    operationLogs.value = []
  } finally {
    logsLoading.value = false
  }
}

const loadStorage = async () => {
  try {
    const res = await getStorageSummary()
    if (res.data.code === 0 && res.data.data) {
      const summary = res.data.data
      usedStorage.value = formatBytes(summary.usedBytes)
      totalStorage.value = formatBytes(summary.totalBytes)
      storageUsage.value = summary.usagePercent || 0
      documentCount.value = summary.documentCount || 0
      averageSize.value = formatBytes(summary.averageBytes)
    }
  } catch {
    usedStorage.value = '0B'
    storageUsage.value = 0
    documentCount.value = 0
    averageSize.value = '0B'
  }
}

// 保存个人信息
const saveProfile = async () => {
  if (!userInfo.username.trim() || !userInfo.email.trim()) {
    message.warning('用户名和邮箱不能为空')
    return
  }

  savingProfile.value = true
  try {
    const res = await updateProfile({
      username: userInfo.username.trim(),
      email: userInfo.email.trim(),
      nickname: userInfo.nickname.trim() || userInfo.username.trim(),
    })
    if (res.data.code === 0 && res.data.data) {
      userStore.setUser(res.data.data)
      applyUserInfo(res.data.data)
      message.success('保存成功')
      loadLogs()
    } else {
      message.error(res.data.message || '保存失败')
    }
  } catch {
    message.error('保存失败，请稍后重试')
  } finally {
    savingProfile.value = false
  }
}

// 修改密码
const changePassword = async () => {
  if (!passwordForm.currentPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) {
    message.warning('请完整填写密码信息')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    message.warning('两次输入的新密码不一致')
    return
  }
  
  if (passwordForm.newPassword.length < 6) {
    message.warning('密码长度不能少于6位')
    return
  }

  savingPassword.value = true
  try {
    const res = await updatePassword({ ...passwordForm })
    if (res.data.code === 0 && res.data.data) {
      message.success('密码修改成功')
      passwordForm.currentPassword = ''
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
      loadLogs()
    } else {
      message.error(res.data.message || '密码修改失败')
    }
  } catch {
    message.error('密码修改失败，请稍后重试')
  } finally {
    savingPassword.value = false
  }
}

onMounted(() => {
  loadProfile()
  loadLogs()
  loadStorage()
})
</script>

<style scoped>
.log-list {
  margin-top: 20px;
}

.user-empty {
  padding: 20px 12px;
  color: #7A7A8A;
  text-align: center;
  border: 1px dashed var(--border-color, #E6E3DC);
  border-radius: 6px;
  background: #F9F7F3;
}

.log-item {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color, #E6E3DC);
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: background 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  border-radius: 6px;
  margin-bottom: 2px;
}

.log-item:last-child {
  border-bottom: none;
}

.log-item:hover {
  background-color: rgba(76, 91, 168, 0.03);
}

.log-time {
  font-size: 12px;
  color: #A8A8B4;
  min-width: 150px;
  font-family: 'SFMono-Regular', Consolas, Menlo, monospace;
}

.log-action {
  flex: 1;
  margin: 0 20px;
  color: #2C2C3A;
}

.log-ip {
  font-size: 12px;
  color: #A8A8B4;
}

.storage-info {
  margin-top: 20px;
}

.storage-bar {
  height: 20px;
  background-color: #F3F0EB;
  border-radius: 10px;
  overflow: hidden;
  margin-bottom: 12px;
  box-shadow: inset 0 1px 3px rgba(44, 44, 58, 0.06);
}

.storage-used {
  height: 100%;
  background: linear-gradient(90deg, #4C5BA8, #7B88D1);
  border-radius: 10px;
  transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
  animation: storagePulse 2.5s ease-in-out infinite;
  position: relative;
}

.storage-used::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 10px;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(255, 255, 255, 0.25) 50%,
    transparent 100%
  );
  background-size: 200% 100%;
  animation: shimmer 2s ease-in-out infinite;
}

@keyframes storagePulse {
  0%, 100% { opacity: 1; }
  50%      { opacity: 0.85; }
}

@keyframes shimmer {
  0%   { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}

.storage-text {
  font-size: 14px;
  color: #2C2C3A;
  margin-bottom: 16px;
}

.storage-detail {
  font-size: 14px;
  color: #7A7A8A;
}

.storage-detail p {
  margin-bottom: 8px;
}
</style>
