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
              <input type="text" v-model="userInfo.username" disabled>
            </div>
            <div class="form-group">
              <label>邮箱</label>
              <input type="email" v-model="userInfo.email">
            </div>
            <div class="form-group">
              <label>注册时间</label>
              <input type="text" v-model="userInfo.registerDate" disabled>
            </div>
            <button class="btn btn-primary" @click="saveProfile">保存修改</button>
          </div>
        </div>

        <!-- 修改密码 -->
        <div v-if="activeTab === 'password'" class="user-info-section">
          <h3>修改密码</h3>
          <div class="user-info-form">
            <div class="form-group">
              <label>当前密码</label>
              <input type="password" v-model="passwordForm.currentPassword" placeholder="请输入当前密码">
            </div>
            <div class="form-group">
              <label>新密码</label>
              <input type="password" v-model="passwordForm.newPassword" placeholder="请输入新密码">
            </div>
            <div class="form-group">
              <label>确认新密码</label>
              <input type="password" v-model="passwordForm.confirmPassword" placeholder="请确认新密码">
            </div>
            <button class="btn btn-primary" @click="changePassword">修改密码</button>
          </div>
        </div>

        <!-- 操作日志 -->
        <div v-if="activeTab === 'logs'" class="user-info-section">
          <h3>操作日志</h3>
          <div class="log-list">
            <div v-for="log in operationLogs" :key="log.id" class="log-item">
              <div class="log-time">{{ log.time }}</div>
              <div class="log-action">{{ log.action }}</div>
              <div class="log-ip">IP: {{ log.ip }}</div>
            </div>
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
import { ref, reactive } from 'vue'

interface UserInfo {
  username: string
  email: string
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
  ip: string
}

const activeTab = ref('profile')

// 用户信息
const userInfo = reactive<UserInfo>({
  username: '用户',
  email: 'user@example.com',
  registerDate: '2024-01-01'
})

// 密码表单
const passwordForm = reactive<PasswordForm>({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 操作日志
const operationLogs = ref<OperationLog[]>([
  { id: 1, time: '2024-01-15 10:30:00', action: '登录系统', ip: '192.168.1.100' },
  { id: 2, time: '2024-01-15 11:20:00', action: '上传文档: 机器学习入门.pdf', ip: '192.168.1.100' },
  { id: 3, time: '2024-01-15 14:15:00', action: '删除文档: 测试文档.txt', ip: '192.168.1.100' },
  { id: 4, time: '2024-01-16 09:00:00', action: '登录系统', ip: '192.168.1.100' }
])

// 存储信息
const usedStorage = ref('6.8MB')
const totalStorage = ref('1GB')
const storageUsage = ref(0.66)
const documentCount = ref(3)
const averageSize = ref('2.27MB')

// 保存个人信息
const saveProfile = () => {
  // TODO: 调用API保存用户信息
  console.log('保存用户信息:', userInfo)
  alert('保存成功！')
}

// 修改密码
const changePassword = () => {
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    alert('两次输入的新密码不一致')
    return
  }
  
  if (passwordForm.newPassword.length < 6) {
    alert('密码长度不能少于6位')
    return
  }

  // TODO: 调用API修改密码
  console.log('修改密码')
  alert('密码修改成功！')
  
  // 清空表单
  passwordForm.currentPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}
</script>

<style scoped>
.log-list {
  margin-top: 20px;
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
