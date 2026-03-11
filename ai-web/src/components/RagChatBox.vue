<template>
  <div class="page-container">
    <!-- 1. 顶部 Header -->
    <div class="chat-header">
      <div class="header-info">
        <span class="header-icon">🚀</span>
        <h2 class="title-text">技术站 · 智库问答</h2>
      </div>

      <div class="upload-actions">
        <!-- 多文件上传组件 -->
        <el-upload
          action="#"
          multiple
          :auto-upload="false"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          :file-list="uiFileList"
          :show-file-list="true"
          class="upload-component"
        >
          <template #trigger>
            <el-button :icon="Link" round size="default">
              {{
                selectedFiles.length > 0 ? `已选 ${selectedFiles.length} 个文件` : '选择文件(多选)'
              }}
            </el-button>
          </template>
        </el-upload>

        <el-button
          type="primary"
          round
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
      <span class="progress-text"
        >正在处理 {{ selectedFiles.length }} 个文档并构建索引: {{ uploadPercent }}%</span
      >
    </div>

    <!-- 2. 中间：消息显示区 -->
    <div class="message-container" ref="chatContainer">
      <div class="message-list-inner">
        <!-- 欢迎页 -->
        <div v-if="messages.length === 0 && !isLoading" class="welcome-wrapper">
          <div class="welcome-box">
            <div class="bot-icon">👨‍💻</div>
            <h3>技术文档智能助手</h3>
            <p>已支持多文档关联分析。请上传技术规格书或代码文档，我会为您精准解答。</p>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-for="(msg, index) in messages" :key="index" :class="['msg-wrapper', msg.role]">
          <!-- 助手头像在左 -->
          <el-avatar v-if="msg.role === 'assistant'" :size="36" :src="botAvatar" class="avatar" />

          <div class="msg-body">
            <div class="msg-meta" v-if="msg.role === 'assistant'">
              <span class="name">智库助手</span>
              <span v-if="msg.duration" class="time"
                >⏱️ 响应时长: {{ msg.duration.toFixed(1) }}s</span
              >
            </div>

            <!-- 气泡：仅在有内容时显示，防止等待时出现空气泡 -->
            <div
              v-if="msg.content || msg.role === 'user'"
              :class="['msg-bubble', msg.role === 'assistant' ? 'markdown-body' : 'user-text']"
            >
              <div v-if="msg.role === 'assistant'" v-html="renderMarkdown(msg.content)"></div>
              <div v-else class="user-raw-text">{{ msg.content }}</div>
            </div>
          </div>

          <!-- 用户头像在右 -->
          <el-avatar v-if="msg.role === 'user'" :size="36" :src="userAvatar" class="avatar" />
        </div>

        <!-- 思考中状态：仅在等待首个数据块时显示 -->
        <div v-if="isLoading && isWaiting" class="msg-wrapper assistant">
          <el-avatar :size="36" :src="botAvatar" class="avatar" />
          <div class="msg-body">
            <div class="thinking-status">
              <div class="typing-loader"></div>
              <span>AI 正在检索并思考... ({{ currentThinkingTime.toFixed(1) }}s)</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 3. 底部：输入区 -->
    <div class="footer-input">
      <div class="input-card">
        <el-input
          v-model="queryInput"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 6 }"
          placeholder="请输入您在技术实现上的疑问..."
          @keydown.enter.prevent="sendQuery"
        />
        <div class="input-actions">
          <span class="tip">✨ 支持代码高亮、多文档关联查询</span>
          <div class="btns">
            <el-button v-if="isLoading" type="danger" size="small" @click="stopGeneration" round
              >停止生成</el-button
            >
            <el-button
              v-else
              type="primary"
              size="small"
              :disabled="!queryInput.trim()"
              @click="sendQuery"
              round
              >发送</el-button
            >
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onUnmounted } from 'vue'
import { Link } from '@element-plus/icons-vue'
import axios from 'axios'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'github-markdown-css/github-markdown-light.css'
import 'highlight.js/styles/github-dark.css' // 使用 Github Dark 主题，更适合代码展示
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadUserFile } from 'element-plus'

// --- Markdown 配置 ---
const md = new MarkdownIt({
  html: true,
  linkify: true,
  breaks: true, // 重要：转换换行符，防止排版挤压
  highlight: (str, lang) => {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs"><code>${hljs.highlight(str, { language: lang, ignoreIllegals: true }).value}</code></pre>`
      } catch (__) {}
    }
    return `<pre class="hljs"><code>${md.utils.escapeHtml(str)}</code></pre>`
  },
})

// 预处理：修复 AI 返回不规范（如标题前忘加换行）的问题
const preprocessMarkdown = (text: string) => {
  if (!text) return ''
  return text
    .replace(/([^\n])(#+\s)/g, '$1\n\n$2') // 给紧贴文字的标题加换行
    .replace(/([^\n])(\d\.\s)/g, '$1\n$2') // 给紧贴文字的数字列表加换行
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
const selectedFiles = ref<File[]>([]) // 存储原始文件对象
const uiFileList = ref<UploadUserFile[]>([]) // UI 展示列表
const isUploading = ref(false)
const uploadPercent = ref(0)
const isLoading = ref(false)
const isWaiting = ref(false)
const currentThinkingTime = ref(0)
const chatContainer = ref<HTMLElement | null>(null)
let thinkingTimer: number | null = null
let abortController = new AbortController()

// --- 文件处理 ---
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
  selectedFiles.value.forEach((file) => {
    formData.append('files', file) // 必须与后端 @RequestParam("files") 一致
  })

  try {
    await axios.post('/api/upload', formData, {
      onUploadProgress: (p) => {
        uploadPercent.value = Math.round((p.loaded * 100) / (p.total || 100))
      },
    })
    ElMessage.success(`成功解析并入库 ${selectedFiles.value.length} 个文档`)
    selectedFiles.value = []
    uiFileList.value = []
  } catch (e) {
    ElMessage.error('入库失败，请检查文件格式')
  } finally {
    setTimeout(() => {
      isUploading.value = false
    }, 800)
  }
}

// --- 对话处理 ---
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
    // 建议从 localStorage 获取或生成持久的 chatId
    const chatId = window.localStorage.getItem('rag_chat_id') || crypto.randomUUID()
    window.localStorage.setItem('rag_chat_id', chatId)

    const response = await fetch(
      `/api/chat?query=${encodeURIComponent(userText)}&chatId=${chatId}`,
      {
        signal: abortController.signal,
      },
    )

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

// --- 工具函数 ---
const startThinkingTimer = () => {
  currentThinkingTime.value = 0
  thinkingTimer = window.setInterval(() => {
    currentThinkingTime.value += 0.1
  }, 100)
}
const stopThinkingTimer = () => {
  if (thinkingTimer) clearInterval(thinkingTimer)
}
const stopGeneration = () => {
  abortController.abort()
  abortController = new AbortController()
  isLoading.value = false
  isWaiting.value = false
  stopThinkingTimer()
}
const scrollToBottom = () => {
  nextTick(() => {
    if (chatContainer.value) chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  })
}
onUnmounted(() => stopThinkingTimer())
</script>

<style scoped>
.page-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: #f8fafc;
  overflow: hidden;
}

/* Header 布局优化 */
.chat-header {
  flex-shrink: 0;
  background: #fff;
  padding: 0 24px;
  height: 64px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e2e8f0;
  z-index: 100;
}
.header-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.title-text {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
  white-space: nowrap;
}
.upload-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 批量上传文件列表悬浮显示，不占用 Header 高度 */
.upload-component :deep(.el-upload-list) {
  position: absolute;
  top: 55px;
  right: 130px;
  width: 260px;
  background: white;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  max-height: 200px;
  overflow-y: auto;
  z-index: 1000;
  padding: 8px;
}

/* 进度条 */
.progress-bar-wrapper {
  background: #fff;
  padding: 8px 24px;
  border-bottom: 1px solid #f1f5f9;
}
.progress-text {
  font-size: 11px;
  color: #64748b;
  margin-top: 4px;
  display: block;
}

/* 消息容器 */
.message-container {
  flex: 1;
  height: 0;
  overflow-y: auto;
  padding: 24px 0;
}
.message-list-inner {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 20px;
}

.msg-wrapper {
  display: flex;
  margin-bottom: 28px;
  gap: 14px;
  align-items: flex-start;
}
.msg-wrapper.user {
  justify-content: flex-end;
}
.msg-body {
  max-width: 82%;
}

.msg-meta {
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #94a3b8;
}

/* 气泡美化 */
.msg-bubble {
  padding: 14px 18px;
  font-size: 15px;
  line-height: 1.7;
}
.user .msg-bubble {
  background: #3b82f6;
  color: #fff;
  border-radius: 18px 4px 18px 18px;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.2);
}
.assistant .msg-bubble {
  background: #fff;
  border: 1px solid #e2e8f0;
  color: #334155;
  border-radius: 4px 18px 18px 18px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.03);
}

/* Markdown 深度美化 */
:deep(.markdown-body) {
  font-size: 15px;
  background: transparent !important;
  color: inherit;
}
:deep(.markdown-body pre) {
  background-color: #1e1e1e !important;
  border-radius: 10px;
  padding: 16px;
  margin: 14px 0;
  border: 1px solid #334155;
}
:deep(.markdown-body h1),
:deep(.markdown-body h2),
:deep(.markdown-body h3) {
  margin: 1.4em 0 0.8em;
  color: #0f172a;
}
:deep(.markdown-body p) {
  margin-bottom: 0.8em;
}
:deep(.markdown-body code) {
  font-family: 'Fira Code', 'Consolas', monospace;
  background-color: #f1f5f9;
  color: #ef4444;
  padding: 2px 4px;
  border-radius: 4px;
}
:deep(.markdown-body pre code) {
  background: transparent;
  color: #e2e8f0;
}

/* 思考动画 */
.thinking-status {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #f1f5f9;
  padding: 12px 20px;
  border-radius: 12px;
  color: #64748b;
  font-size: 14px;
}
.typing-loader {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #3b82f6;
  animation: typing 0.8s infinite alternate;
}
@keyframes typing {
  from {
    transform: scale(1);
    opacity: 1;
  }
  to {
    transform: scale(1.6);
    opacity: 0.3;
  }
}

/* 底部输入框 */
.footer-input {
  flex-shrink: 0;
  padding: 16px 24px 30px;
  background: #fff;
  border-top: 1px solid #e2e8f0;
}
.input-card {
  max-width: 900px;
  margin: 0 auto;
  border: 1px solid #cbd5e1;
  border-radius: 14px;
  padding: 10px;
  transition: all 0.2s;
}
.input-card:focus-within {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

:deep(.el-textarea__inner) {
  box-shadow: none !important;
  border: none !important;
  resize: none;
  font-size: 15px;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  padding: 0 4px;
}
.tip {
  font-size: 12px;
  color: #94a3b8;
}

.welcome-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}
.welcome-box {
  text-align: center;
  color: #64748b;
}
.bot-icon {
  font-size: 60px;
  margin-bottom: 16px;
}
</style>
