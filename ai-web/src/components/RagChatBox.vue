<template>
  <div class="page-container">
    <!-- 1. 顶部 Header -->
    <div class="chat-header">
      <div class="header-info">
        <span class="header-icon">🚀</span>
        <h2 class="title-text">技术站 · 智库问答</h2>
      </div>

      <div class="upload-actions">
        <el-upload
          action="#"
          :auto-upload="false"
          :on-change="handleFileChange"
          :show-file-list="false"
          class="upload-component"
        >
          <template #trigger>
            <el-button :icon="Link" round size="default">
              {{ selectedFile ? truncateFileName(selectedFile.name) : '选择文件' }}
            </el-button>
          </template>
        </el-upload>

        <el-button
          type="primary"
          round
          :disabled="!selectedFile"
          :loading="isUploading"
          @click="uploadFile"
        >
          上传入库
        </el-button>
      </div>
    </div>

    <!-- 进度条 -->
    <div v-if="isUploading" class="progress-bar-wrapper">
      <el-progress :percentage="uploadPercent" :stroke-width="2" :show-text="false" />
      <span class="progress-text">正在解析文档: {{ uploadPercent }}%</span>
    </div>

    <!-- 2. 中间：消息显示区 -->
    <div class="message-container" ref="chatContainer">
      <div class="message-list-inner">
        <!-- 欢迎页 -->
        <div v-if="messages.length === 0 && !isLoading" class="welcome-wrapper">
          <div class="welcome-box">
            <div class="bot-icon">👨‍💻</div>
            <h3>技术文档智能助手</h3>
            <p>基于 RAG 技术，为您解析本地文档中的核心架构与实现细节。</p>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-for="(msg, index) in messages" :key="index" :class="['msg-wrapper', msg.role]">
          <!-- 助手头像在左 -->
          <el-avatar v-if="msg.role === 'assistant'" :size="36" :src="botAvatar" class="avatar" />

          <div class="msg-body">
            <div class="msg-meta" v-if="msg.role === 'assistant'">
              <span class="name">智库助手</span>
              <span v-if="msg.duration" class="time">⏱️ {{ msg.duration.toFixed(1) }}s</span>
            </div>

            <!-- 气泡内容：如果是 assistant 且内容为空，则不显示气泡（等待思考状态） -->
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

        <!-- 思考中状态：仅在 isLoading 为真且最后一条消息还没开始吐字时显示 -->
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
          placeholder="请输入技术问题，按 Enter 发送..."
          @keydown.enter.prevent="sendQuery"
        />
        <div class="input-actions">
          <span class="tip">支持 Markdown & 代码高亮渲染</span>
          <div class="btns">
            <el-button v-if="isLoading" type="danger" size="small" @click="stopGeneration" round>停止生成</el-button>
            <el-button v-else type="primary" size="small" :disabled="!queryInput.trim()" @click="sendQuery" round>发送</el-button>
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
import 'highlight.js/styles/github-dark.css'
import { ElMessage } from 'element-plus'

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
  return text.replace(/([^\n])(#+\s)/g, '$1\n\n$2') // 修复标题挤占问题
}

const renderMarkdown = (content: string) => content ? md.render(preprocessMarkdown(content)) : ''

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
const selectedFile = ref<File | null>(null)
const isUploading = ref(false)
const uploadPercent = ref(0)
const isLoading = ref(false)
const isWaiting = ref(false) // 是否在等待 AI 响应第一块数据
const currentThinkingTime = ref(0)
const chatContainer = ref<HTMLElement | null>(null)
let thinkingTimer: number | null = null
let abortController = new AbortController()

const truncateFileName = (name: string) => name.length > 12 ? name.substring(0, 12) + '...' : name
const handleFileChange = (file: any) => { selectedFile.value = file.raw }

const uploadFile = async () => {
  if (!selectedFile.value) return
  isUploading.value = true
  const formData = new FormData()
  formData.append('file', selectedFile.value)
  try {
    await axios.post('/api/upload', formData, {
      onUploadProgress: (p) => { uploadPercent.value = Math.round((p.loaded * 100) / (p.total || 100)) }
    })
    ElMessage.success('文档上传并解析成功')
    selectedFile.value = null
  } catch (e) { ElMessage.error('上传失败') }
  finally { setTimeout(() => isUploading.value = false, 800) }
}

const sendQuery = async () => {
  if (!queryInput.value.trim() || isLoading.value) return

  const userText = queryInput.value
  messages.value.push({ role: 'user', content: userText })
  queryInput.value = ''
  isLoading.value = true
  isWaiting.value = true // 进入等待状态，显示思考动画

  startThinkingTimer()
  const lastIdx = messages.value.push({ role: 'assistant', content: '' }) - 1

  try {
    const response = await fetch(`/api/chat?query=${encodeURIComponent(userText)}&chatId=user_1`, {
      signal: abortController.signal,
    })

    if (!response.body) return
    const reader = response.body.getReader()
    const decoder = new TextDecoder()

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      if (isWaiting.value) {
        stopThinkingTimer()
        messages.value[lastIdx].duration = currentThinkingTime.value
        isWaiting.value = false // 收到数据，隐藏思考动画，显示内容气泡
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

/* Header */
.chat-header {
  flex-shrink: 0;
  background: #fff;
  padding: 0 24px;
  height: 64px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e2e8f0;
  z-index: 10;
}
.header-info { display: flex; align-items: center; gap: 10px; }
.title-text { font-size: 18px; font-weight: 600; color: #1e293b; }
.upload-actions { display: flex; align-items: center; gap: 12px; }

/* 消息区域 */
.message-container {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0;
  scroll-behavior: smooth;
}
.message-list-inner {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 20px;
}

.msg-wrapper {
  display: flex;
  margin-bottom: 24px;
  gap: 14px;
  align-items: flex-start;
}
.msg-wrapper.user { justify-content: flex-end; }
.msg-body { max-width: 80%; }

.msg-meta {
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #64748b;
}

/* 气泡美化 */
.msg-bubble {
  padding: 12px 18px;
  font-size: 15px;
  line-height: 1.6;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
}
.user .msg-bubble {
  background: #3b82f6;
  color: #fff;
  border-radius: 18px 4px 18px 18px;
}
.assistant .msg-bubble {
  background: #fff;
  border: 1px solid #e2e8f0;
  color: #1e293b;
  border-radius: 4px 18px 18px 18px;
}

/* Markdown 排版细化 */
:deep(.markdown-body) {
  font-size: 15px;
  background: transparent !important;
}
:deep(.markdown-body pre) {
  background-color: #1e1e1e !important;
  border-radius: 8px;
  padding: 14px;
  margin: 10px 0;
}

/* 思考中动画容器 */
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
  animation: typing 1s infinite alternate;
}
@keyframes typing { from { transform: scale(1); opacity: 1; } to { transform: scale(1.5); opacity: 0.3; } }

/* 底部输入框 */
.footer-input {
  padding: 16px 24px 24px;
  background: #fff;
  border-top: 1px solid #e2e8f0;
}
.input-card {
  max-width: 900px;
  margin: 0 auto;
  border: 1px solid #cbd5e1;
  border-radius: 12px;
  padding: 8px;
  transition: border-color 0.2s;
}
.input-card:focus-within { border-color: #3b82f6; }

:deep(.el-textarea__inner) {
  box-shadow: none !important;
  border: none !important;
  resize: none;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  padding: 0 4px;
}
.tip { font-size: 12px; color: #94a3b8; }

.welcome-wrapper {
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: #64748b;
}
.bot-icon { font-size: 50px; margin-bottom: 12px; }
</style>
