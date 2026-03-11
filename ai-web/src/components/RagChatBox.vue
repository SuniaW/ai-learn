<template>
  <div class="page-container">
    <!-- 1. 顶部 Header -->
    <div class="chat-header">
      <div class="header-left">
        <div class="logo-box">🚀</div>
        <div class="title-wrapper">
          <h2 class="title-text">技术站 · 智库问答</h2>
          <span class="status-tag">RAG Engine V2.0</span>
        </div>
      </div>

      <div class="upload-actions">
        <!-- 文件列表气泡展示 -->
        <el-popover
          v-if="uiFileList.length > 0"
          placement="bottom-end"
          :width="300"
          trigger="hover"
          popper-class="file-list-popover"
        >
          <template #reference>
            <el-button link type="primary" size="small" class="file-count-btn">
              <el-icon><Files /></el-icon>
              待处理文档 ({{ uiFileList.length }})
            </el-button>
          </template>

          <div class="popover-file-container">
            <div class="popover-header">已选择的文档</div>
            <div class="popover-list">
              <div v-for="file in uiFileList" :key="file.uid" class="popover-item">
                <div class="file-info">
                  <el-icon><Document /></el-icon>
                  <span class="file-name" :title="file.name">{{ file.name }}</span>
                </div>
                <el-icon class="remove-icon" @click="handleFileRemove(file, uiFileList)"
                ><CircleClose
                /></el-icon>
              </div>
            </div>
          </div>
        </el-popover>

        <el-upload
          action="#"
          multiple
          :auto-upload="false"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          :file-list="uiFileList"
          :show-file-list="false"
          class="upload-component"
        >
          <template #trigger>
            <el-button class="glass-btn" round :icon="Link" size="small">选择文档</el-button>
          </template>
        </el-upload>

        <el-button
          type="primary"
          class="upload-btn"
          round
          size="small"
          :disabled="selectedFiles.length === 0"
          :loading="isUploading"
          @click="uploadFiles"
        >
          上传入库
        </el-button>
      </div>
    </div>

    <!-- 批量上传进度条 -->
    <div v-if="isUploading" class="progress-bar-wrapper">
      <el-progress :percentage="uploadPercent" :stroke-width="2" :show-text="false" />
      <span class="progress-text">正在分析文档并构建索引: {{ uploadPercent }}%</span>
    </div>

    <!-- 2. 中间：消息显示区 -->
    <div class="message-container" ref="chatContainer">
      <div class="message-list-inner">
        <!-- 欢迎页 -->
        <div v-if="messages.length === 0 && !isLoading" class="welcome-wrapper">
          <div class="welcome-hero">
            <div class="hero-icon">👨‍💻</div>
            <h1 class="welcome-title">技术文档智能助手</h1>
            <p class="welcome-desc">上传规格书或代码，为您提供精准的关联分析。</p>
          </div>

          <div class="example-section">
            <div class="section-divider"><span>您可以这样问我</span></div>
            <div class="example-grid">
              <div
                v-for="(item, index) in exampleQuestions"
                :key="index"
                class="modern-card"
                @click="handleExampleClick(item.query)"
              >
                <div class="card-left-line" :style="{ background: item.color }"></div>
                <div class="card-main">
                  <div class="card-header-row">
                    <span class="card-emoji">{{ item.icon }}</span>
                    <span class="card-query">{{ item.query }}</span>
                  </div>
                  <div class="card-footer-row">
                    <span>点击提问</span>
                    <el-icon><Right /></el-icon>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-for="(msg, index) in messages" :key="index" :class="['msg-wrapper', msg.role]">
          <el-avatar :size="36" :src="msg.role === 'assistant' ? botAvatar : userAvatar" />
          <div class="msg-body">
            <!-- 修复点：确保元数据行包含响应时长展示 -->
            <div class="msg-meta" v-if="msg.role === 'assistant'">
              <span class="name-tag">智库助手</span>
              <span v-if="msg.duration" class="time-tag">
                <el-icon><Timer /></el-icon>
                {{ msg.duration.toFixed(1) }}s
              </span>
            </div>
            <div
              :class="[
                'msg-bubble',
                msg.role === 'assistant' ? 'assistant-bubble markdown-body' : 'user-bubble',
              ]"
            >
              <div v-if="msg.role === 'assistant'" v-html="renderMarkdown(msg.content)"></div>
              <div v-else class="user-raw-text">{{ msg.content }}</div>
            </div>
          </div>
        </div>

        <!-- 思考中状态 -->
        <div v-if="isLoading && isWaiting" class="msg-wrapper assistant">
          <el-avatar :size="36" :src="botAvatar" />
          <div class="msg-body">
            <div class="thinking-bubble">
              <div class="dot-typing"></div>
              <!-- 修复点：添加实时思考时间展示 -->
              <span>AI 正在思考... ({{ currentThinkingTime.toFixed(1) }}s)</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 3. 底部：输入区 -->
    <div class="footer-input">
      <div class="input-card-modern">
        <el-input
          v-model="queryInput"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 4 }"
          placeholder="在此输入您的疑问..."
          @keydown.enter.prevent="sendQuery"
        />
        <div class="input-toolbar">
          <div class="toolbar-left">
            <span class="feature-tag"><el-icon><Files /></el-icon>多文档模式</span>
          </div>
          <div class="toolbar-right">
            <el-button
              v-if="isLoading"
              type="danger"
              circle
              :icon="CircleClose"
              size="small"
              @click="stopGeneration"
            />
            <el-button
              v-else
              type="primary"
              class="send-btn"
              size="small"
              :disabled="!queryInput.trim()"
              @click="sendQuery"
            >
              发送<el-icon class="el-icon--right"><Position /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onUnmounted } from 'vue'
import { Link, Right, Timer, Position, Files, CircleClose, Document } from '@element-plus/icons-vue'
import axios from 'axios'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'github-markdown-css/github-markdown-light.css'
import 'highlight.js/styles/github-dark.css'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadUserFile } from 'element-plus'

// --- Markdown 配置 ---
const md = new MarkdownIt({
  html: true,
  linkify: true,
  breaks: true,
  highlight: (str, lang) => {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs"><code>${hljs.highlight(str, { language: lang, ignoreIllegals: true }).value}</code></pre>`
      } catch (__) {}
    }
    return `<pre class="hljs"><code>${md.utils.escapeHtml(str)}</code></pre>`
  },
})

const preprocessMarkdown = (text: string) => {
  if (!text) return ''
  return text.replace(/([^\n])(#+\s)/g, '$1\n\n$2').replace(/([^\n])(\d\.\s)/g, '$1\n$2')
}

const renderMarkdown = (content: string) => (content ? md.render(preprocessMarkdown(content)) : '')

// --- 状态管理 ---
const userAvatar = 'https://api.dicebear.com/7.x/avataaars/svg?seed=Felix'
const botAvatar = 'https://api.dicebear.com/7.x/bottts/svg?seed=Aneka'

interface Message {
  role: 'user' | 'assistant'
  content: string
  duration?: number
}

const messages = ref<Message[]>([])
const queryInput = ref('')
const selectedFiles = ref<File[]>([])
const uiFileList = ref<UploadUserFile[]>([])
const isUploading = ref(false)
const uploadPercent = ref(0)
const isLoading = ref(false)
const isWaiting = ref(false)
const currentThinkingTime = ref(0)
const chatContainer = ref<HTMLElement | null>(null)
let thinkingTimer: number | null = null
let abortController = new AbortController()

const exampleQuestions = [
  { icon: '📝', query: 'docker简介', color: '#3b82f6' },
  { icon: '🛡️', query: 'milvus简介', color: '#10b981' },
  { icon: '🚀', query: 'ollama简介', color: '#f59e0b' },
  { icon: '🔍', query: '如何在Linux上部署docker', color: '#8b5cf6' },
]

const handleFileChange = (file: UploadFile, fileList: UploadFile[]) => {
  selectedFiles.value = fileList.map((f) => f.raw as File)
  uiFileList.value = fileList as UploadUserFile[]
}

const handleFileRemove = (file: UploadFile, fileList: UploadFile[]) => {
  selectedFiles.value = fileList.map((f) => f.raw as File)
  uiFileList.value = fileList as UploadUserFile[]
}

const uploadFiles = async () => {
  if (selectedFiles.value.length === 0) return
  isUploading.value = true
  uploadPercent.value = 0
  const formData = new FormData()
  selectedFiles.value.forEach((file) => { formData.append('files', file) })

  try {
    await axios.post('/api/upload', formData, {
      onUploadProgress: (p) => {
        uploadPercent.value = Math.round((p.loaded * 100) / (p.total || 100))
      },
    })
    ElMessage.success('上传成功')
    selectedFiles.value = []; uiFileList.value = []
  } catch (e) {
    ElMessage.error('上传失败')
  } finally {
    setTimeout(() => { isUploading.value = false }, 800)
  }
}

const sendQuery = async () => {
  if (!queryInput.value.trim() || isLoading.value) return
  const userText = queryInput.value
  messages.value.push({ role: 'user', content: userText })
  queryInput.value = ''
  isLoading.value = true
  isWaiting.value = true

  startThinkingTimer()
  const lastIdx = messages.value.push({ role: 'assistant', content: '' }) - 1

  try {
    const chatId = window.localStorage.getItem('rag_chat_id') || crypto.randomUUID()
    window.localStorage.setItem('rag_chat_id', chatId)
    const response = await fetch(`/api/chat?query=${encodeURIComponent(userText)}&chatId=${chatId}`, { signal: abortController.signal })

    if (!response.body) return
    const reader = response.body.getReader()
    const decoder = new TextDecoder()

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      if (isWaiting.value) {
        stopThinkingTimer()
        messages.value[lastIdx].duration = currentThinkingTime.value
        isWaiting.value = false
      }

      const chunk = decoder.decode(value)
      const lines = chunk.split('\n')
      for (const line of lines) {
        if (line.trim().startsWith('data:')) {
          messages.value[lastIdx].content += line.trim().substring(5)
          scrollToBottom()
        }
      }
    }
  } catch (e) {
    console.error(e)
  } finally {
    isLoading.value = false
    isWaiting.value = false
    stopThinkingTimer()
  }
}

const handleExampleClick = (query: string) => { queryInput.value = query; sendQuery() }
const startThinkingTimer = () => {
  currentThinkingTime.value = 0
  thinkingTimer = window.setInterval(() => { currentThinkingTime.value += 0.1 }, 100)
}
const stopThinkingTimer = () => { if (thinkingTimer) clearInterval(thinkingTimer) }
const stopGeneration = () => {
  abortController.abort()
  abortController = new AbortController()
  isLoading.value = false
  isWaiting.value = false
  stopThinkingTimer()
}
const scrollToBottom = () => {
  nextTick(() => { if (chatContainer.value) chatContainer.value.scrollTop = chatContainer.value.scrollHeight })
}
onUnmounted(() => stopThinkingTimer())
</script>

<style scoped>
/* 核心布局修复 */
.page-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 60px);
  background: #f8fafc;
  overflow: hidden;
}

/* Header */
.chat-header {
  flex-shrink: 0;
  height: 56px;
  background: #fff;
  padding: 0 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e2e8f0;
}
.header-left { display: flex; align-items: center; gap: 12px; }
.title-text { font-size: 16px; font-weight: 700; color: #0f172a; margin: 0; }
.status-tag { font-size: 10px; color: #3b82f6; background: #eff6ff; padding: 1px 6px; border-radius: 4px; }

/* 文件列表按钮 */
.file-count-btn { font-weight: 600; font-size: 13px; display: flex; align-items: center; gap: 4px; }
.popover-file-container { padding: 4px; }
.popover-header { font-size: 12px; color: #94a3b8; font-weight: 700; margin-bottom: 8px; border-bottom: 1px solid #f1f5f9; padding-bottom: 4px; }
.popover-list { max-height: 200px; overflow-y: auto; }
.popover-item { display: flex; align-items: center; justify-content: space-between; padding: 6px 0; border-bottom: 1px dashed #f1f5f9; }
.file-info { display: flex; align-items: center; gap: 8px; overflow: hidden; }
.file-name { font-size: 13px; color: #475569; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 200px; }
.remove-icon { color: #94a3b8; cursor: pointer; transition: 0.2s; }
.remove-icon:hover { color: #f87171; }

.upload-actions { display: flex; align-items: center; gap: 12px; }

/* 消息区 */
.message-container { flex: 1; overflow-y: auto; padding: 20px 0; }
.message-list-inner { max-width: 880px; margin: 0 auto; padding: 0 20px; }

/* 欢迎页 */
.welcome-hero { text-align: center; margin-bottom: 24px; }
.hero-icon { font-size: 40px; margin-bottom: 8px; }
.welcome-title { font-size: 22px; font-weight: 800; color: #1e293b; margin: 0 0 8px 0; }
.welcome-desc { color: #64748b; font-size: 14px; margin: 0; }
.section-divider { display: flex; align-items: center; margin: 20px 0; color: #cbd5e1; font-size: 12px; }
.section-divider::before, .section-divider::after { content: ''; flex: 1; height: 1px; background: #e2e8f0; margin: 0 10px; }
.example-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.modern-card { background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; display: flex; cursor: pointer; transition: 0.2s; }
.modern-card:hover { transform: translateY(-2px); border-color: #3b82f6; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05); }
.card-left-line { width: 4px; flex-shrink: 0; }
.card-main { flex: 1; padding: 12px; overflow: hidden; }
.card-query { font-size: 13px; font-weight: 600; color: #334155; }

/* 气泡样式 */
.msg-wrapper { display: flex; margin-bottom: 20px; gap: 12px; }
.msg-wrapper.user { flex-direction: row-reverse; }
.msg-bubble { padding: 12px 16px; font-size: 14px; line-height: 1.6; }
.user-bubble { background: #3b82f6; color: #fff; border-radius: 16px 4px 16px 16px; }
.assistant-bubble { background: #fff; border: 1px solid #e2e8f0; border-radius: 4px 16px 16px 16px; }

/* 修复点：响应时长标签样式 */
.msg-meta { font-size: 11px; color: #94a3b8; margin-bottom: 4px; display: flex; align-items: center; gap: 8px; }
.time-tag { color: #3b82f6; background: #eff6ff; padding: 1px 6px; border-radius: 4px; display: flex; align-items: center; gap: 4px; font-weight: 600; }

/* 底部输入框 */
.footer-input { flex-shrink: 0; padding: 12px 20px 20px; background: #f8fafc; }
.input-card-modern { max-width: 880px; margin: 0 auto; background: #fff; border: 1px solid #cbd5e1; border-radius: 16px; padding: 8px 12px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04); }
:deep(.el-textarea__inner) { box-shadow: none !important; border: none !important; font-size: 14px; padding: 4px 0 !important; }
.input-toolbar { display: flex; justify-content: space-between; align-items: center; margin-top: 4px; border-top: 1px solid #f1f5f9; padding-top: 8px; }
.feature-tag { font-size: 10px; color: #94a3b8; display: flex; align-items: center; gap: 4px; }

/* 思考态打点动画 */
.thinking-bubble { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #94a3b8; }
.dot-typing { width: 4px; height: 4px; background: #3b82f6; border-radius: 50%; animation: typing 1s infinite; }
@keyframes typing { 0%, 100% { transform: scale(1); opacity: 0.5; } 50% { transform: scale(1.5); opacity: 1; } }
</style>
