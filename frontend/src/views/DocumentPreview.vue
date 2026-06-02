<template>
  <div id="document-preview" class="content-page active">
    <div class="preview-header">
      <div class="preview-info">
        <div class="document-icon pdf-icon">{{ document?.fileType?.toUpperCase() || 'DOC' }}</div>
        <div class="document-info">
          <h3>{{ document?.name || '文档预览' }}</h3>
          <div class="document-meta">
            <span>{{ formatDate(document?.uploadTime) }}</span>
            <span>{{ formatSize(document?.fileSize) }}</span>
          </div>
        </div>
      </div>
      <div class="preview-actions">
        <button class="btn" @click="goBack">返回列表</button>
        <button class="btn" :disabled="!document" @click="downloadDocument">下载</button>
        <button class="btn btn-primary" :disabled="!document" @click="generateQA">生成问答</button>
      </div>
    </div>

    <div class="preview-content">
      <div v-if="loading" class="preview-state">加载中...</div>
      <div v-else-if="errorMessage" class="preview-state preview-error">{{ errorMessage }}</div>
      <iframe
        v-else
        class="document-frame"
        :src="previewUrl"
        :title="document?.name || '文档预览'"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getById } from '@/api/wendangguanli'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const errorMessage = ref('')
const document = ref<API.DocumentVO | null>(null)

const docId = computed(() => Number(route.params.id))
const previewUrl = computed(() => `/api/document/${docId.value}/preview`)

const loadDocument = async () => {
  if (!Number.isFinite(docId.value) || docId.value <= 0) {
    errorMessage.value = '文档 ID 无效'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getById({ id: docId.value })
    if (res.data.code === 0 && res.data.data) {
      document.value = res.data.data
    } else {
      errorMessage.value = res.data.message || '文档加载失败'
    }
  } catch (error: any) {
    errorMessage.value = error?.response?.data?.message || error?.message || '文档加载失败'
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  router.push('/document-management')
}

const downloadDocument = () => {
  if (!document.value) return
  window.open(`/api/document/${docId.value}/download`, '_blank')
}

const generateQA = () => {
  if (!document.value) return
  message.success('已带入当前文档')
  router.push({
    path: '/intelligent-qa',
    query: { docId: String(docId.value) },
  })
}

const formatSize = (size?: number) => {
  if (!size) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

const formatDate = (date?: string) => {
  if (!date) return '-'
  return new Date(date).toLocaleString()
}

onMounted(loadDocument)
</script>

<style scoped>
.document-frame {
  width: 100%;
  height: calc(100vh - 220px);
  min-height: 560px;
  border: 0;
  background: #fff;
}

.preview-state {
  min-height: 360px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666;
}

.preview-error {
  color: #b42318;
}
</style>
