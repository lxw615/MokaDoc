<template>
  <div id="document-management" class="content-page active">
    <div class="document-header">
      <button class="btn btn-primary" :disabled="uploadLoading" @click="triggerUpload">
        {{ uploadLoading ? '上传中...' : '📤 上传文档' }}
      </button>
      <input
        ref="fileInputRef"
        type="file"
        accept=".pdf,.docx,.xlsx,.xls,.txt,.md,.markdown,.json,.csv,.java,.vue,.js,.ts,.xml,.yml,.yaml"
        style="display: none"
        @change="handleFileSelected"
      />
      <div class="search-box">
        <input
          type="text"
          v-model="searchQuery"
          placeholder="搜索文档..."
        >
        <button class="search-btn">🔍</button>
      </div>
      <div class="filter-dropdown">
        <button class="btn" disabled>筛选 ▼</button>
      </div>
    </div>

    <div class="view-toggle">
      <button
        class="btn"
        :class="{ active: viewMode === 'card' }"
        @click="viewMode = 'card'"
      >
        卡片视图
      </button>
      <button
        class="btn"
        :class="{ active: viewMode === 'table' }"
        @click="viewMode = 'table'"
      >
        表格视图
      </button>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="loading-state">
      加载中...
    </div>

    <!-- 空状态 -->
    <div v-else-if="documents.length === 0" class="empty-state">
      <p>暂无文档</p>
      <p class="empty-hint">点击"上传文档"按钮上传你的第一个文档</p>
    </div>

    <!-- 文档列表 -->
    <div v-else class="document-list">
      <div
        v-for="doc in filteredDocuments"
        :key="doc.id"
        class="document-card"
      >
        <div class="document-icon" :class="getIconClass(doc.fileType)">
          {{ doc.fileType.toUpperCase() }}
        </div>
        <h3 :title="doc.name">{{ doc.name }}</h3>
        <div class="document-meta">
          <span>{{ formatDate(doc.uploadTime) }}</span>
          <span>{{ formatSize(doc.fileSize) }}</span>
        </div>
        <div class="document-actions">
          <button class="btn-sm" @click="previewDocument(doc)">👁️ 预览</button>
          <button class="btn-sm" @click="shareDocument(doc)">🔗 分享</button>
          <button class="btn-sm danger" :disabled="deletingId === doc.id" @click="deleteDocument(doc)">
            {{ deletingId === doc.id ? '删除中...' : '🗑️ 删除' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { list, deleteUsingDelete, upload as uploadApi } from '@/api/wendangguanli'

const router = useRouter()
const searchQuery = ref('')
const viewMode = ref('card')
const loading = ref(false)
const uploadLoading = ref(false)
const deletingId = ref<number | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const MAX_UPLOAD_SIZE = 200 * 1024 * 1024

// 文档列表
interface DocItem {
  id: number
  name: string
  fileType: string
  fileSize: number
  uploadTime: string
  description?: string
}

const documents = ref<DocItem[]>([])

// 获取文档列表
const fetchDocuments = async () => {
  loading.value = true
  try {
    const res = await list()
    if (res.data.code === 0 && res.data.data) {
      documents.value = res.data.data
        .filter((doc): doc is API.DocumentVO & { id: number } => doc.id !== undefined)
        .map(doc => ({
          id: doc.id,
          name: doc.name || '未命名文档',
          fileType: doc.fileType || '',
          fileSize: doc.fileSize || 0,
          uploadTime: doc.uploadTime || '',
          description: doc.description,
        }))
    } else {
      message.error(res.data.message || '获取文档列表失败')
    }
  } catch {
    message.error('网络错误，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(fetchDocuments)

// 根据搜索条件过滤文档
const filteredDocuments = computed(() => {
  if (!searchQuery.value) return documents.value
  const q = searchQuery.value.toLowerCase()
  return documents.value.filter(doc =>
    doc.name.toLowerCase().includes(q)
  )
})

// 获取图标样式类
const getIconClass = (fileType: string) => {
  const iconMap: Record<string, string> = {
    pdf: 'pdf-icon',
    txt: 'txt-icon',
    md: 'md-icon',
    doc: 'doc-icon',
    docx: 'doc-icon',
  }
  return iconMap[fileType] || ''
}

// 格式化文件大小
const formatSize = (bytes: number) => {
  if (!bytes) return '0B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return `${size.toFixed(1)}${units[i]}`
}

// 格式化日期
const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

// 触发文件上传
const triggerUpload = () => {
  fileInputRef.value?.click()
}

// 处理文件选择
const handleFileSelected = async (e: Event) => {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  if (file.size > MAX_UPLOAD_SIZE) {
    message.error('文件过大，最大支持 200MB，请压缩后重试')
    target.value = ''
    return
  }

  uploadLoading.value = true
  try {
    const res = await uploadApi(file)
    if (res.data.code === 0 && res.data.data) {
      message.success('上传成功')
      await fetchDocuments()
    } else {
      message.error(res.data.message || '上传失败')
    }
  } catch (error: any) {
    console.error('文件上传失败:', error)
    const responseData = error?.response?.data
    message.error(
      responseData?.message ||
      responseData?.error ||
      (error?.response?.status ? `文件上传失败，HTTP ${error.response.status}` : '') ||
      error?.message ||
      '文件上传失败'
    )
  } finally {
    uploadLoading.value = false
    // 重置 input 以允许再次选择同一文件
    target.value = ''
  }
}

// 预览文档
const previewDocument = (doc: DocItem) => {
  router.push(`/document-preview/${doc.id}`)
}

// 分享文档
const shareDocument = (_doc: DocItem) => {
  message.info('分享功能开发中')
}

// 删除文档
const deleteDocument = async (doc: DocItem) => {
  if (!confirm(`确定要删除 "${doc.name}" 吗？`)) return

  deletingId.value = doc.id
  try {
    const res = await deleteUsingDelete({ id: doc.id })
    if (res.data.code === 0) {
      message.success('文档已删除')
      documents.value = documents.value.filter(d => d.id !== doc.id)
    } else {
      message.error(res.data.message || '删除失败')
    }
  } catch {
    message.error('删除失败，请稍后重试')
  } finally {
    deletingId.value = null
  }
}
</script>

<style scoped>
/* 样式已从全局 style.css 引入 */
</style>
