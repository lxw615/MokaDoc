<template>
  <div class="app-layout">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <div class="logo">
        <h1>智能文档系统</h1>
      </div>
      <nav class="nav">
        <router-link 
          to="/document-management" 
          class="nav-item"
          active-class="active"
        >
          📁 文档管理
        </router-link>
        <router-link 
          to="/intelligent-qa" 
          class="nav-item"
          active-class="active"
        >
          💬 智能问答
        </router-link>
        <router-link 
          to="/knowledge-graph" 
          class="nav-item"
          active-class="active"
        >
          🌐 知识图谱
        </router-link>
        <router-link 
          to="/user-center" 
          class="nav-item"
          active-class="active"
        >
          👤 用户中心
        </router-link>
      </nav>
    </aside>

    <!-- 顶部栏 -->
    <header class="topbar">
      <div class="topbar-left">
        <h2>{{ pageTitle }}</h2>
      </div>
      <div class="topbar-right">
        <div class="topbar-menu">
          <button class="icon-btn" title="提醒" @click.stop="toggleMenu('notifications')">🔔</button>
          <div v-if="openMenu === 'notifications'" class="topbar-popover" @click.stop>
            <div class="popover-title">提醒</div>
            <div class="popover-item">文档解析、图谱构建和问答完成后会在这里提示。</div>
            <button class="popover-action" @click="goTo('/knowledge-graph')">查看图谱任务</button>
          </div>
        </div>
        <div class="topbar-menu">
          <button class="icon-btn" title="设置" @click.stop="toggleMenu('settings')">⚙️</button>
          <div v-if="openMenu === 'settings'" class="topbar-popover" @click.stop>
            <div class="popover-title">设置</div>
            <label class="popover-toggle">
              <input v-model="compactMode" type="checkbox">
              紧凑显示
            </label>
            <label class="popover-toggle">
              <input v-model="showHints" type="checkbox">
              显示页面提示
            </label>
          </div>
        </div>
        <div class="user-menu topbar-menu">
          <button class="user-btn" @click.stop="toggleMenu('user')">👤 {{ displayName }}</button>
          <div v-if="openMenu === 'user'" class="topbar-popover user-popover" @click.stop>
            <div class="popover-title">{{ displayName }}</div>
            <div class="popover-muted">{{ userStore.currentUser?.email || '未设置邮箱' }}</div>
            <button class="popover-action" @click="goTo('/user-center')">个人信息</button>
            <button class="popover-action" @click="handleLogout">退出登录</button>
          </div>
        </div>
      </div>
    </header>

    <!-- 主内容区 -->
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { userLogout } from '@/api/yonghuguanli'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const openMenu = ref<'notifications' | 'settings' | 'user' | null>(null)
const compactMode = ref(false)
const showHints = ref(true)

// 根据路由名称动态设置页面标题
const pageTitle = computed(() => {
  const titleMap: Record<string, string> = {
    'document-management': '文档管理',
    'intelligent-qa': '智能问答',
    'knowledge-graph': '知识图谱',
    'user-center': '用户中心',
    'document-preview': '文档预览'
  }
  return titleMap[route.name as string] || '智能文档系统'
})

const displayName = computed(() => {
  const user = userStore.currentUser
  return user?.nickname || user?.username || '用户'
})

const toggleMenu = (menu: 'notifications' | 'settings' | 'user') => {
  openMenu.value = openMenu.value === menu ? null : menu
}

const goTo = (path: string) => {
  openMenu.value = null
  router.push(path)
}

const handleLogout = async () => {
  try {
    await userLogout()
  } catch {
    // 本地退出即可，后端 session 失效失败不影响用户离开。
  }
  userStore.logout()
  openMenu.value = null
  message.success('已退出登录')
  router.push('/login')
}

const closeMenu = () => {
  openMenu.value = null
}

onMounted(() => {
  if (!userStore.currentUser) {
    userStore.fetchCurrentUser()
  }
  document.addEventListener('click', closeMenu)
})

onUnmounted(() => {
  document.removeEventListener('click', closeMenu)
})
</script>

<style scoped>
.app-layout {
    display: grid;
    grid-template-columns: 240px 1fr;
    grid-template-rows: 64px 1fr;
    grid-template-areas:
        "sidebar header"
        "sidebar main";
    height: 100vh;
}

/* 侧边栏 */
.sidebar {
    grid-area: sidebar;
    background-color: var(--surface-color, #FFFFFF);
    border-right: 1px solid var(--border-color, #E6E3DC);
    padding: 20px;
    overflow-y: auto;
}

.sidebar .logo h1 {
    font-size: 20px;
    color: var(--primary-color);
    margin-bottom: 30px;
    letter-spacing: 1px;
    animation: fadeInDown 0.5s var(--transition-ease, cubic-bezier(0.4, 0, 0.2, 1)) both;
}

@keyframes fadeInDown {
    from { opacity: 0; transform: translateY(-12px); }
    to   { opacity: 1; transform: translateY(0); }
}

/* 顶部栏 */
.topbar {
    grid-area: header;
    background-color: var(--surface-color, #FFFFFF);
    border-bottom: 1px solid var(--border-color, #E6E3DC);
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 20px;
}

.topbar-menu {
    position: relative;
}

.topbar-popover {
    position: absolute;
    top: 42px;
    right: 8px;
    width: 240px;
    padding: 12px;
    background: var(--surface-color, #FFFFFF);
    border: 1px solid var(--border-color, #E6E3DC);
    border-radius: 8px;
    box-shadow: var(--shadow-lg, 0 8px 32px rgba(44, 44, 58, 0.10));
    z-index: 50;
}

.user-popover {
    width: 260px;
}

.popover-title {
    font-weight: 600;
    margin-bottom: 8px;
    color: var(--text-color, #2C2C3A);
}

.popover-muted,
.popover-item {
    color: var(--text-secondary, #7A7A8A);
    font-size: 13px;
    line-height: 1.5;
    margin-bottom: 10px;
}

.popover-action {
    width: 100%;
    border: 1px solid var(--border-color, #E6E3DC);
    background: var(--surface-color, #FFFFFF);
    color: var(--text-color, #2C2C3A);
    border-radius: 6px;
    padding: 8px 10px;
    margin-top: 6px;
    cursor: pointer;
    text-align: left;
}

.popover-action:hover {
    border-color: var(--primary-color, #4C5BA8);
    background: var(--primary-soft, rgba(76, 91, 168, 0.08));
    color: var(--primary-color, #4C5BA8);
}

.popover-toggle {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 0;
    color: var(--text-color, #2C2C3A);
}

/* 主内容区 */
.main-content {
    grid-area: main;
    padding: 24px;
    overflow-y: auto;
    height: 100%;
    box-sizing: border-box;
    background: var(--bg-color, #F3F0EB);
}

@media (max-width: 768px) {
    .app-layout {
        grid-template-columns: 1fr;
        grid-template-rows: auto auto 1fr;
        grid-template-areas:
            "header"
            "sidebar"
            "main";
    }

    .sidebar {
        width: 100%;
        border-right: none;
        border-bottom: 1px solid var(--border-color, #E6E3DC);
    }
}
</style>
