<template>
  <div id="intelligent-qa" class="content-page active">
    <div class="qa-container">
      <!-- 左侧：会话管理和文档选择 -->
      <div class="qa-left">
        <div class="session-management">
          <button class="btn-sm btn-primary" @click="openCreateDialog">+ 新建会话</button>
          <div class="session-list" ref="sessionListRef" @scroll="handleSessionListScroll">
            <div v-if="sessions.length === 0 && !loadingSessions" class="empty-session">
              暂无会话，请新建或等待加载
            </div>
            <div
              v-for="session in sessions"
              :key="session.id"
              class="session-item"
              :class="{ active: currentSessionId === session.id }"
              @click="switchSession(session.id)"
            >
              <div class="session-title">{{ session.name }}</div>
              <div class="session-summary">
                {{ session.messages.length > 0 ? session.messages[0].content : '无消息' }}
              </div>
              <div class="session-actions" @click.stop>
                <button class="session-action-btn" title="编辑" @click="openEditDialog(session)">✎</button>
                <button class="session-action-btn session-action-delete" title="删除" @click="handleDeleteSession(session)">✕</button>
              </div>
            </div>

            <!-- 加载更多提示 -->
            <div v-if="loadingSessions" class="loading-more">
              <span class="loading-spinner"></span>
              加载中...
            </div>
            <div v-else-if="!hasMoreSessions && sessions.length > 0" class="no-more">
              已加载全部会话
            </div>
          </div>
        </div>

        <!-- 会话创建/编辑弹窗 -->
        <div v-if="dialogVisible" class="dialog-overlay" @click.self="dialogVisible = false">
          <div class="dialog-panel">
            <div class="dialog-header">
              <h3>{{ dialogMode === 'create' ? '新建会话' : '编辑会话' }}</h3>
              <button class="dialog-close" @click="dialogVisible = false">✕</button>
            </div>
            <div class="dialog-body">
              <label class="dialog-label">会话名称</label>
              <input
                ref="dialogInputRef"
                v-model="sessionFormName"
                class="dialog-input"
                placeholder="输入会话名称（留空则自动生成）"
                @keyup.enter="handleDialogSubmit"
              >
            </div>
            <div class="dialog-footer">
              <button class="btn" @click="dialogVisible = false">取消</button>
              <button class="btn btn-primary" :disabled="saving" @click="handleDialogSubmit">
                {{ saving ? '保存中...' : '确定' }}
              </button>
            </div>
          </div>
        </div>

        <div class="qa-left-header">
          <button class="btn-sm" @click="selectAllDocuments">全选</button>
          <button class="btn-sm" @click="deselectAllDocuments">取消全选</button>
        </div>
        
        <div class="document-select-list">
          <div v-if="loadingDocuments" class="loading-documents">
            加载文档中...
          </div>
          <div v-else-if="documents.length === 0" class="loading-documents">
            暂无文档，请先上传
          </div>
          <template v-else>
            <div
              v-for="doc in documents"
              :key="doc.id"
              class="document-select-item"
              :class="{ active: selectedDocs.includes(doc.id) }"
              @click="toggleDocumentSelection(doc.id)"
            >
              <input
                type="checkbox"
                :checked="selectedDocs.includes(doc.id)"
                @click.stop
              >
              <span>{{ doc.name }}</span>
            </div>
          </template>
        </div>
      </div>

      <!-- 中间：聊天区域 -->
      <div class="qa-middle">
        <div class="qa-middle-header">
          <div class="selected-documents">
            <span 
              v-for="docId in selectedDocs" 
              :key="docId"
              class="document-tag"
            >
              {{ getDocumentName(docId) }}
              <button class="tag-remove" @click="removeSelectedDoc(docId)">×</button>
            </span>
          </div>
        </div>
        
        <div class="chat-container" ref="chatContainer">
          <div 
            v-for="(message, index) in currentMessages" 
            :key="index"
            class="chat-message"
            :class="`${message.type}-message`"
          >
            <div class="message-content">
              <div v-if="message.type === 'ai'" class="markdown-body" v-html="message.renderedContent"></div>
              <div v-else>{{ message.content }}</div>
            </div>
          </div>
        </div>
        
        <div class="chat-input">
          <textarea 
            v-model="userInput"
            placeholder="请输入您的问题..."
            @keyup.enter.ctrl="sendMessage"
            :disabled="isStreaming"
          ></textarea>
          <button 
            v-if="!isStreaming" 
            class="btn btn-primary" 
            @click="sendMessage"
          >
            发送
          </button>
          <button 
            v-else 
            class="btn btn-danger" 
            @click="stopStreaming"
          >
            ⏹ 停止
          </button>
        </div>
      </div>

      <!-- 右侧：引用溯源 -->
      <div class="qa-right">
        <div class="qa-right-header">
          <h3>引用溯源</h3>
        </div>
        <div class="reference-content">
          <div 
            v-for="(ref, index) in references" 
            :key="index"
            class="reference-item"
          >
            <h4>[{{ index + 1 }}] {{ ref.source }}</h4>
            <p>{{ ref.content }}</p>
            <button class="btn-sm">查看原文</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onUnmounted, onMounted } from 'vue'
import { useSSEFetch, createAbortController } from '@/utils/sse'
import { marked } from 'marked'
import { listSessions, listChat, createSession, updateSession, deleteSession } from '@/api/liaotianguanli'
import { list as listDocuments } from '@/api/wendangguanli'
import { message, Modal } from 'ant-design-vue'

// 配置 marked 选项
marked.setOptions({
  breaks: true,        // 支持 GitHub 风格的换行（单个换行符转为 <br>）
  gfm: true,           // 启用 GitHub Flavored Markdown（表格、任务列表等）
  headerIds: false,    // 不自动生成标题 ID
  mangle: false,       // 不转义邮箱地址
  pedantic: false,     // 不使用严格的 Markdown 解析
  sanitize: false,     // 不启用内置 sanitization（我们将使用 DOMPurify）
  smartLists: true,    // 使用更智能的列表行为
  smartypants: false   // 不转换引号和破折号
})

interface Message {
  type: 'user' | 'ai'
  content: string
}

interface Session {
  id: number  // 前端会话 ID
  name: string
  messages: Message[]
  backendSessionId?: number  // 后端会话 ID（从 SSE 响应中获取）
  loaded?: boolean  // 是否已加载消息历史
}

interface Document {
  id: number
  name: string
}

interface Reference {
  source: string
  content: string
}

// 会话数据
const sessions = ref<Session[]>([])
const currentSessionId = ref<number | null>(null)

// 分页相关状态
const currentPage = ref(1)           // 当前页码
const pageSize = ref(10)             // 每页大小
const hasMoreSessions = ref(true)    // 是否还有更多数据
const loadingSessions = ref(false)   // 是否正在加载
const sessionListRef = ref<HTMLElement | null>(null)  // 会话列表容器引用

// 文档数据
const documents = ref<Document[]>([])
const selectedDocs = ref<number[]>([])
const loadingDocuments = ref(false)

// 加载文档列表
const fetchDocuments = async () => {
  loadingDocuments.value = true
  try {
    const res = await listDocuments()
    if (res.data.code === 0 && res.data.data) {
      documents.value = res.data.data.map(doc => ({
        id: doc.id!,
        name: doc.name || '',
      }))
    }
  } catch {
    console.error('获取文档列表失败')
  } finally {
    loadingDocuments.value = false
  }
}

// 用户输入
const userInput = ref('')

// 引用数据
const references = ref<Reference[]>([
  {
    source: '机器学习入门.pdf',
    content: '机器学习是人工智能的一个分支，它允许计算机在没有明确编程的情况下学习和改进。'
  },
  {
    source: '机器学习入门.pdf',
    content: '机器学习可以分为监督学习、无监督学习和强化学习等类型。'
  }
])

// 聊天容器引用
const chatContainer = ref<HTMLElement | null>(null)

// SSE 中断控制器
let abortController: AbortController | null = null

// 是否正在接收流式响应
const isStreaming = ref(false)

// 安全地渲染 Markdown(带简单的 XSS 防护)
const safeMarkdownToHtml = (markdown: string): string => {
  if (!markdown) return ''
  
  try {
    // 预处理:修复标题格式(确保 # 后有空格)
    const fixedMarkdown = fixMarkdownHeaders(markdown)
    
    // 使用 marked 解析 Markdown
    const html = marked.parse(fixedMarkdown)
    
    // 简单的 XSS 防护：移除危险标签
    // 注意：生产环境建议使用 DOMPurify
    const sanitized = String(html)
      .replace(/<script[^>]*>.*?<\/script>/gi, '')  // 移除 script 标签
      .replace(/on\w+="[^"]*"/gi, '')                // 移除内联事件
      .replace(/on\w+='[^']*'/gi, '')                  // 移除内联事件（单引号）
    
    return sanitized
  } catch (error) {
    console.error('Markdown 解析失败:', error)
    // 降级处理：将原始文本转义后返回
    return `<p style="color: red;">[Markdown 解析失败]</p><pre>${markdown}</pre>`
  }
}

// 获取当前会话的消息（带 Markdown 渲染）
const currentMessages = computed(() => {
  const session = sessions.value.find(s => s.id === currentSessionId.value)
  if (!session) return []
  
  return session.messages.map(msg => {
    // AI 消息使用 Markdown 渲染，用户消息保持纯文本
    const renderedContent = msg.type === 'ai' ? safeMarkdownToHtml(msg.content) : msg.content
    
    // 调试日志：查看原始内容和渲染后的内容（仅开发环境）
    if (msg.type === 'ai' && msg.content && import.meta.env.DEV) {
      console.log('=== Markdown 调试信息 ===')
      console.log('原始内容前100字符:', msg.content.substring(0, 100))
      console.log('渲染后HTML前200字符:', String(renderedContent).substring(0, 200))
    }
    
    return {
      ...msg,
      renderedContent
    }
  })
})

// 获取文档名称
const getDocumentName = (docId: number) => {
  const doc = documents.value.find(d => d.id === docId)
  return doc ? doc.name : ''
}

// 加载会话列表（支持分页追加）
const loadSessions = async (isLoadMore = false) => {
  // 防止重复加载
  if (loadingSessions.value) return
  
  // 如果没有更多数据，不再加载
  if (isLoadMore && !hasMoreSessions.value) return
  
  try {
    loadingSessions.value = true
    const pageToLoad = isLoadMore ? currentPage.value + 1 : 1
    
    console.log(`开始加载会话列表... 页码: ${pageToLoad}, 每页: ${pageSize.value}`)
    
    const res = await listSessions({
      pageSize: pageSize.value,
      // 如果后端支持游标分页，可以传入 lastCreateTime
      // lastCreateTime: isLoadMore && sessions.value.length > 0 
      //   ? sessions.value[sessions.value.length - 1].createTime 
      //   : undefined
    })
    
    console.log('后端响应:', res.data)
    
    if (res.data.code === 0 && res.data.data) {
      // 注意：后端返回的是分页结构，数据在 data.records 中
      const records = res.data.data.records || []
      const totalRow = res.data.data.totalRow || 0
      
      console.log(`本次加载记录数: ${records.length}, 总记录数: ${totalRow}`)
      
      // 将后端返回的 QaSession 转换为前端 Session 格式
      const newSessions = records.map((qaSession) => ({
        id: qaSession.id!,
        name: qaSession.sessionName || `会话 ${qaSession.id}`,
        messages: [], // 初始化为空，后续按需加载消息历史
        backendSessionId: qaSession.id,
        loaded: false  // 标记是否已加载消息历史
      }))
      
      if (isLoadMore) {
        // 追加模式：将新数据添加到现有列表后面
        sessions.value = [...sessions.value, ...newSessions]
      } else {
        // 首次加载模式：替换整个列表
        sessions.value = newSessions
        
        // 如果有会话，默认选中第一个并加载其消息
        if (sessions.value.length > 0) {
          currentSessionId.value = sessions.value[0].id
          // 异步加载第一个会话的消息
          loadSessionMessages(sessions.value[0].id)
        }
      }
      
      // 更新分页状态
      currentPage.value = pageToLoad
      hasMoreSessions.value = sessions.value.length < totalRow
      
      console.log(`✅ 会话列表加载成功, 当前总数: ${sessions.value.length}, 是否还有更多: ${hasMoreSessions.value}`)
    } else {
      console.error('❌ 会话列表加载失败:', res.data.message)
    }
  } catch (error) {
    console.error('❌ 加载会话列表异常:', error)
  } finally {
    loadingSessions.value = false
  }
}

// 处理会话列表滚动事件
const handleSessionListScroll = (event: Event) => {
  const target = event.target as HTMLElement
  if (!target) return
  
  // 计算滚动位置
  const scrollTop = target.scrollTop              // 已滚动距离
  const scrollHeight = target.scrollHeight        // 总高度
  const clientHeight = target.clientHeight        // 可见高度
  
  // 当滚动到距离底部 50px 时触发加载
  const threshold = 50
  if (scrollHeight - scrollTop - clientHeight < threshold) {
    console.log('触发加载更多')
    loadSessions(true)  // 加载更多
  }
}

// --- 会话 CRUD 弹窗 ---
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingSession = ref<Session | null>(null)
const sessionFormName = ref('')
const saving = ref(false)
const dialogInputRef = ref<HTMLInputElement | null>(null)

const openCreateDialog = () => {
  dialogMode.value = 'create'
  editingSession.value = null
  sessionFormName.value = ''
  dialogVisible.value = true
  nextTick(() => dialogInputRef.value?.focus())
}

const openEditDialog = (session: Session) => {
  dialogMode.value = 'edit'
  editingSession.value = session
  sessionFormName.value = session.name
  dialogVisible.value = true
  nextTick(() => dialogInputRef.value?.focus())
}

const handleDialogSubmit = async () => {
  if (saving.value) return
  saving.value = true

  try {
    if (dialogMode.value === 'create') {
      const res = await createSession({ sessionName: sessionFormName.value || undefined })
      if (res.data.code === 0 && res.data.data) {
        const qa = res.data.data
        const newSession: Session = {
          id: qa.id!,
          name: qa.sessionName || `会话 ${qa.id}`,
          messages: [],
          backendSessionId: qa.id,
          loaded: true,
        }
        sessions.value.unshift(newSession)
        currentSessionId.value = newSession.id
        message.success('会话创建成功')
        dialogVisible.value = false
      } else {
        message.error(res.data.message || '创建失败')
      }
    } else {
      const s = editingSession.value
      if (!s) return
      const res = await updateSession(
        { sessionId: s.id },
        { sessionName: sessionFormName.value || undefined },
      )
      if (res.data.code === 0 && res.data.data) {
        s.name = sessionFormName.value || s.name
        message.success('会话已更新')
        dialogVisible.value = false
      } else {
        message.error(res.data.message || '更新失败')
      }
    }
  } catch {
    message.error('网络错误，请稍后重试')
  } finally {
    saving.value = false
  }
}

const handleDeleteSession = (session: Session) => {
  Modal.confirm({
    title: '删除会话',
    content: `确定要删除「${session.name}」吗？该操作不可撤销。`,
    okText: '删除',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        const res = await deleteSession({ sessionId: session.id })
        if (res.data.code === 0) {
          // 从列表中移除
          const idx = sessions.value.findIndex(s => s.id === session.id)
          if (idx !== -1) sessions.value.splice(idx, 1)

          // 如果删除的是当前会话，切换到下一个
          if (currentSessionId.value === session.id) {
            if (sessions.value.length > 0) {
              currentSessionId.value = sessions.value[0].id
              loadSessionMessages(sessions.value[0].id)
            } else {
              currentSessionId.value = null
            }
          }
          message.success('会话已删除')
        } else {
          message.error(res.data.message || '删除失败')
        }
      } catch {
        message.error('网络错误，请稍后重试')
      }
    },
  })
}

// 加载指定会话的消息历史
const loadSessionMessages = async (sessionId: number) => {
  const session = sessions.value.find(s => s.id === sessionId)
  if (!session || session.loaded) {
    console.log('会话不存在或已加载，跳过')
    return
  }
  
  try {
    console.log(`开始加载会话 ${sessionId} 的消息历史...`)
    
    const res = await listChat({
      sessionId: session.backendSessionId!,
      pageSize: 100  // 一次性加载较多消息
    })
    
    if (res.data.code === 0 && res.data.data) {
      const records = res.data.data.records || []
      
      // 将后端返回的 QaMessage 转换为前端 Message 格式
      // 注意：后端可能按时间降序返回(新的在前)，需要反转为升序(旧的在上，新的在下)
      const messages: Message[] = records
        .map((qaMessage) => ({
          type: qaMessage.messageType === 1 ? 'user' : 'ai',
          content: qaMessage.content || ''
        }))
        .reverse()  // 反转数组，使旧消息在上，新消息在下
      
      session.messages = messages
      session.loaded = true  // 标记为已加载
      
      console.log(`✅ 会话 ${sessionId} 消息加载成功, 共 ${messages.length} 条`)
      console.log('消息顺序:', messages.map(m => m.type + ':' + m.content.substring(0, 20)))
    } else {
      console.error('❌ 加载消息历史失败:', res.data.message)
    }
  } catch (error) {
    console.error('❌ 加载消息历史异常:', error)
  }
}

// 切换会话
const switchSession = async (sessionId: number) => {
  currentSessionId.value = sessionId
  
  // 异步加载该会话的消息历史
  await loadSessionMessages(sessionId)
}

// 切换文档选择
const toggleDocumentSelection = (docId: number) => {
  const index = selectedDocs.value.indexOf(docId)
  if (index > -1) {
    selectedDocs.value.splice(index, 1)
  } else {
    selectedDocs.value.push(docId)
  }
}

// 全选文档
const selectAllDocuments = () => {
  selectedDocs.value = documents.value.map(d => d.id)
}

// 取消全选
const deselectAllDocuments = () => {
  selectedDocs.value = []
}

// 移除选中的文档
const removeSelectedDoc = (docId: number) => {
  const index = selectedDocs.value.indexOf(docId)
  if (index > -1) {
    selectedDocs.value.splice(index, 1)
  }
}

// 发送消息
const sendMessage = async () => {
  const message = userInput.value.trim()
  if (!message) return

  // 如果正在流式传输，先中断
  if (isStreaming.value && abortController) {
    abortController.abort()
    abortController = null
    isStreaming.value = false
  }

  // 添加用户消息
  const session = sessions.value.find(s => s.id === currentSessionId.value)
  if (session) {
    session.messages.push({ type: 'user', content: message })
  }

  // 清空输入框
  userInput.value = ''

  // 滚动到底部
  await nextTick()
  scrollToBottom()

  // 创建 AI 消息占位符
  let aiMessageContent = ''
  if (session) {
    session.messages.push({ type: 'ai', content: '' })
  }

  // 开始流式请求
  isStreaming.value = true
  abortController = createAbortController()

  // 使用新的后端 API: POST /api/chat/ask
  // 实际请求: http://localhost:5174/api/chat/ask → http://localhost:8080/api/chat/ask
  const API_URL = '/api/chat/ask'
  
  // 构建请求体：如果当前会话已有后端 sessionId，则传入以继续对话
  const requestBody: { content: string; sessionId?: number; documentIds?: number[] } = {
    content: message,
    documentIds: selectedDocs.value.length > 0 ? [...selectedDocs.value] : undefined,
  }
  
  // 如果当前会话已经有后端分配的 sessionId，则传入
  if (session && session.backendSessionId) {
    requestBody.sessionId = session.backendSessionId
    console.log('使用已保存的 backendSessionId:', session.backendSessionId)
  } else {
    console.log('未找到 backendSessionId，将创建新会话')
  }

  try {
    console.log('发起 SSE 请求:', API_URL, '参数:', requestBody)
    
    await useSSEFetch(
      API_URL,
      requestBody,
      {
        method: 'POST',  // 使用 POST 方法
        onMessage: (data) => {
          console.log('收到SSE数据:', data)
          
          // 尝试解析 JSON 格式的数据
          try {
            const parsed = JSON.parse(data)
            
            // 处理不同类型的消息
            if (parsed.type === 'metadata' || (parsed.message && parsed.sessionId !== undefined)) {
              // 元数据类型：包含 sessionId 等信息
              console.log('接收到元数据:', parsed)
              
              // 保存后端返回的 sessionId 到当前会话
              if (session && !session.backendSessionId && parsed.sessionId) {
                session.backendSessionId = parsed.sessionId
                console.log('✅ 保存后端 sessionId:', parsed.sessionId, '到前端会话:', session.id)
              } else if (session && session.backendSessionId) {
                console.log('ℹ️  会话已有 backendSessionId:', session.backendSessionId, '，跳过更新')
              }
              
              // 不将元数据显示在对话中
              return
            }
            
            if (parsed.type === 'streaming' && parsed.content !== undefined) {
              // 流式内容类型：提取 content 字段并拼接
              const contentChunk = parsed.content
              
              if (contentChunk) {
                aiMessageContent += contentChunk
                
                // 更新最后一条 AI 消息
                if (session) {
                  const lastMessage = session.messages[session.messages.length - 1]
                  if (lastMessage && lastMessage.type === 'ai') {
                    lastMessage.content = aiMessageContent
                  }
                }
                
                // 实时滚动到底部
                scrollToBottom()
              }
              return
            }
            
            // 其他类型的 JSON 数据，记录日志但不处理
            console.log('未处理的JSON类型:', parsed.type)
            
          } catch (e) {
            // 不是 JSON 格式，可能是纯文本（兼容旧格式）
            console.warn('非JSON格式数据，直接拼接:', data)
            if (data) {
              aiMessageContent += data
              
              // 更新最后一条 AI 消息
              if (session) {
                const lastMessage = session.messages[session.messages.length - 1]
                if (lastMessage && lastMessage.type === 'ai') {
                  lastMessage.content = aiMessageContent
                }
              }
              
              scrollToBottom()
            }
          }
        },
        onError: (error) => {
          console.error('SSE error:', error)
          console.error('Error message:', error.message)
          console.error('Error name:', error.name)
          isStreaming.value = false
          abortController = null
          
          // 如果还没有内容，显示错误信息
          if (!aiMessageContent) {
            if (session) {
              const lastMessage = session.messages[session.messages.length - 1]
              if (lastMessage && lastMessage.type === 'ai') {
                // 提供更详细的错误信息
                let errorMsg = '抱歉，AI 服务出现错误'
                
                if (error.message.includes('404')) {
                  errorMsg += '：接口不存在，请检查后端服务是否启动'
                } else if (error.message.includes('401') || error.message.includes('403')) {
                  errorMsg += '：认证失败，请重新登录'
                } else if (error.message.includes('500')) {
                  errorMsg += '：服务器内部错误'
                } else if (error.message.includes('Failed to fetch') || error.message.includes('NetworkError')) {
                  errorMsg += '：网络连接失败，请检查后端服务是否运行在 http://localhost:8080'
                } else {
                  errorMsg += '，请稍后重试'
                }
                
                lastMessage.content = errorMsg
              }
            }
          }
        },
        onComplete: () => {
          console.log('SSE completed')
          isStreaming.value = false
          abortController = null
        },
        signal: abortController.signal,
      }
    )
  } catch (error) {
    console.error('Send message error:', error)
    console.error('Error details:', error)
    isStreaming.value = false
    abortController = null
    
    // 在 catch 中也更新错误信息
    if (session) {
      const lastMessage = session.messages[session.messages.length - 1]
      if (lastMessage && lastMessage.type === 'ai' && !lastMessage.content) {
        lastMessage.content = `请求失败：${error instanceof Error ? error.message : '未知错误'}`
      }
    }
  }
}

// 停止流式响应
const stopStreaming = () => {
  if (abortController) {
    abortController.abort()
    abortController = null
    isStreaming.value = false
    console.log('用户手动停止流式响应')
  }
}

// 滚动到底部
const scrollToBottom = () => {
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

// 组件挂载时加载会话列表和文档列表
onMounted(() => {
  loadSessions()
  fetchDocuments()
})

// 组件卸载时中断 SSE 连接
onUnmounted(() => {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
})

// 修复 Markdown 标题格式(确保 # 后有空格)
const fixMarkdownHeaders = (text: string): string => {
  if (!text) return text
  
  // 匹配任意位置的 1-6 级标题,如果 # 后直接跟非空格字符,则插入空格
  // 例如: ##一、 -> ## 一、
  // 注意:不使用 ^ 锚点,因为后端返回的可能是单行连续文本
  return text.replace(/(#{1,6})([^\s#])/g, '$1 $2')
}
</script>

<style scoped>
/* --- 加载动画 --- */
.loading-spinner {
    width: 18px;
    height: 18px;
    border: 2px solid var(--border-color, #E6E3DC);
    border-top-color: var(--primary-color, #4C5BA8);
    border-radius: 50%;
    animation: spin 0.7s linear infinite;
}

@keyframes spin {
    to { transform: rotate(360deg); }
}

/* --- Markdown 渲染样式 --- */
.message-content :deep(.markdown-body) {
  line-height: 1.8;
  font-size: 15px;
  color: #2C2C3A;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 标题 */
.message-content :deep(h1),
.message-content :deep(h2),
.message-content :deep(h3),
.message-content :deep(h4),
.message-content :deep(h5),
.message-content :deep(h6) {
  margin-top: 1.5em;
  margin-bottom: 0.75em;
  font-weight: 600;
  line-height: 1.25;
  color: #2C2C3A;
}

.message-content :deep(h1) {
  font-size: 2em;
  border-bottom: 2px solid var(--border-color, #E6E3DC);
  padding-bottom: 0.3em;
}

.message-content :deep(h2) {
  font-size: 1.5em;
  border-bottom: 1px solid var(--border-color, #E6E3DC);
  padding-bottom: 0.3em;
}

.message-content :deep(h3) { font-size: 1.25em; }
.message-content :deep(h4) { font-size: 1em; }

/* 段落 */
.message-content :deep(p) {
  margin-top: 0;
  margin-bottom: 1em;
  line-height: 1.8;
  text-indent: 0;
}

/* 列表 */
.message-content :deep(ul),
.message-content :deep(ol) {
  margin-top: 0;
  margin-bottom: 1em;
  padding-left: 2em;
}

.message-content :deep(li) {
  margin: 0.25em 0;
  line-height: 1.6;
}

.message-content :deep(li > p) {
  margin: 0.25em 0;
}

/* 代码 */
.message-content :deep(code) {
  padding: 0.2em 0.4em;
  margin: 0;
  font-size: 85%;
  background-color: rgba(76, 91, 168, 0.06);
  border-radius: 4px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace;
  color: #4C5BA8;
}

.message-content :deep(pre) {
  padding: 1em;
  overflow: auto;
  font-size: 85%;
  line-height: 1.45;
  background-color: #F9F7F3;
  border-radius: 8px;
  margin: 1em 0;
  border: 1px solid var(--border-light, #F0EDE7);
}

.message-content :deep(pre code) {
  padding: 0;
  margin: 0;
  font-size: 100%;
  background: transparent;
  border: 0;
  word-break: normal;
  white-space: pre;
  color: inherit;
}

/* 引用块 */
.message-content :deep(blockquote) {
  margin: 1em 0;
  padding: 0.5em 1em;
  color: #7A7A8A;
  border-left: 3px solid var(--primary-color, #4C5BA8);
  background-color: rgba(76, 91, 168, 0.03);
  border-radius: 0 6px 6px 0;
}

.message-content :deep(blockquote > p:first-child) { margin-top: 0; }
.message-content :deep(blockquote > p:last-child)  { margin-bottom: 0; }

/* 表格 */
.message-content :deep(table) {
  border-spacing: 0;
  border-collapse: collapse;
  margin: 1em 0;
  width: 100%;
  display: block;
  overflow-x: auto;
}

.message-content :deep(table th),
.message-content :deep(table td) {
  padding: 8px 13px;
  border: 1px solid var(--border-color, #E6E3DC);
}

.message-content :deep(table th) {
  font-weight: 600;
  background-color: #F9F7F3;
}

.message-content :deep(table tr) {
  background-color: #fff;
  border-top: 1px solid var(--border-color, #E6E3DC);
}

.message-content :deep(table tr:nth-child(2n)) {
  background-color: #F9F7F3;
}

/* 链接 */
.message-content :deep(a) {
  color: var(--primary-color, #4C5BA8);
  text-decoration: none;
}

.message-content :deep(a:hover) {
  text-decoration: underline;
}

/* 分割线 */
.message-content :deep(hr) {
  height: 0.25em;
  padding: 0;
  margin: 24px 0;
  background-color: var(--border-color, #E6E3DC);
  border: 0;
}

/* 图片 */
.message-content :deep(img) {
  max-width: 100%;
  height: auto;
  box-sizing: content-box;
  border-radius: 8px;
  margin: 1em 0;
  box-shadow: 0 2px 12px rgba(44, 44, 58, 0.08);
}

/* 强调 */
.message-content :deep(strong) {
  font-weight: 600;
  color: #2C2C3A;
}

.message-content :deep(em) { font-style: italic; }

.message-content :deep(del) {
  text-decoration: line-through;
  opacity: 0.7;
}

.message-content :deep(mark) {
  background-color: rgba(200, 144, 74, 0.15);
  padding: 0.1em 0.3em;
  border-radius: 3px;
}

/* 任务列表 */
.message-content :deep(input[type="checkbox"]) {
  margin-right: 0.5em;
  accent-color: var(--primary-color, #4C5BA8);
}

/* --- 空会话提示 --- */
.empty-session {
  text-align: center;
  color: #7A7A8A;
  font-size: 14px;
  padding: 20px 10px;
}

/* --- 加载更多 --- */
.loading-more {
  text-align: center;
  padding: 12px 10px;
  color: #7A7A8A;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.no-more {
  text-align: center;
  padding: 12px 10px;
  color: #A8A8B4;
  font-size: 13px;
}

.loading-documents {
  text-align: center;
  padding: 30px 10px;
  color: #7A7A8A;
  font-size: 14px;
}

/* --- 固定框 + 内部滚动 (布局保留) --- */
.session-management {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  max-height: 340px;
}

.qa-left-header {
  flex-shrink: 0;
}

.document-select-list {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.qa-middle {
  height: 100%;
  min-height: 0;
}

.chat-container {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.qa-middle-header {
  flex-shrink: 0;
}

.chat-input {
  flex-shrink: 0;
}

.qa-right {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.qa-right-header {
  flex-shrink: 0;
}

.reference-content {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

/* --- 会话项操作按钮 --- */
.session-item {
  position: relative;
}

.session-actions {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.session-item:hover .session-actions {
  opacity: 1;
}

.session-action-btn {
  background: none;
  border: none;
  font-size: 14px;
  width: 26px;
  height: 26px;
  border-radius: 4px;
  cursor: pointer;
  color: #7A7A8A;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.18s cubic-bezier(0.4, 0, 0.2, 1);
}

.session-action-btn:hover {
  background-color: rgba(76, 91, 168, 0.08);
  color: var(--primary-color, #4C5BA8);
}

.session-action-delete:hover {
  background-color: rgba(196, 84, 92, 0.08);
  color: #C4545C;
}

/* --- 弹窗 --- */
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(44, 44, 58, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes fadeIn {
  from { opacity: 0; }
  to   { opacity: 1; }
}

.dialog-panel {
  background: var(--surface-color, #FFFFFF);
  border-radius: 12px;
  box-shadow: 0 16px 48px rgba(44, 44, 58, 0.18);
  width: 420px;
  max-width: 90vw;
  animation: fadeInScale 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes fadeInScale {
  from { opacity: 0; transform: scale(0.92); }
  to   { opacity: 1; transform: scale(1); }
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 0;
}

.dialog-header h3 {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-color, #2C2C3A);
}

.dialog-close {
  background: none;
  border: none;
  font-size: 16px;
  color: #A8A8B4;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: all 0.18s cubic-bezier(0.4, 0, 0.2, 1);
}

.dialog-close:hover {
  background-color: rgba(44, 44, 58, 0.06);
  color: #2C2C3A;
}

.dialog-body {
  padding: 20px 24px;
}

.dialog-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-color, #2C2C3A);
  margin-bottom: 8px;
}

.dialog-input {
  width: 100%;
  padding: 10px 14px;
  border: 1.5px solid var(--border-color, #E6E3DC);
  border-radius: 6px;
  font-size: 14px;
  outline: none;
  background: var(--bg-warm, #F9F7F3);
  transition: border-color 0.2s cubic-bezier(0.4, 0, 0.2, 1),
              box-shadow 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.dialog-input:focus {
  border-color: var(--primary-color, #4C5BA8);
  box-shadow: 0 0 0 3px rgba(76, 91, 168, 0.18);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 0 24px 20px;
}
</style>
