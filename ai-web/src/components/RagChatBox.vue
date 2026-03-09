<template>
  <div class="app-container">
    <!-- 1. 顶部导航与文档上传 -->
    <header class="glass-header">
      <div class="header-content">
        <div class="logo">
          <span class="icon">📚</span>
          <h1>政策文档智库</h1>
        </div>

        <div class="upload-section">
          <div class="file-input-wrapper">
            <input type="file" id="file" @change="handleFileChange" class="hidden-input" />
            <label for="file" class="file-label">
              {{ selectedFile ? selectedFile.name : '选择政策文件...' }}
            </label>
          </div>
          <button @click="uploadFile" :disabled="!selectedFile || isUploading" class="btn-upload">
            {{ isUploading ? '解析中...' : '上传入库' }}
          </button>
        </div>
      </div>
      <div
        v-if="uploadStatus"
        class="status-bar"
        :class="{ 'status-success': !uploadStatus.includes('失败') }"
      >
        {{ uploadStatus }}
      </div>
    </header>

    <!-- 2. 聊天区域 -->
    <main class="chat-main" ref="chatContainer">
      <div class="chat-wrapper">
        <!-- 初始欢迎状态 -->
        <div v-if="messages.length === 0" class="welcome-card">
          <div class="bot-avatar-large">🤖</div>
          <h2>您好！我是您的政策助手</h2>
          <p>您可以上传政策文档，我会基于文档为您提供精准的咨询服务。</p>
          <div class="hints">
            <span>💡 咨询买房补贴</span>
            <span>💡 了解人才落户政策</span>
            <span>💡 创业扶持资金申请</span>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-for="(msg, index) in messages" :key="index" :class="['message-row', msg.role]">
          <div class="avatar">{{ msg.role === 'user' ? '👤' : '🤖' }}</div>
          <div class="bubble">
            <div class="role-name">{{ msg.role === 'user' ? '我的提问' : '政策智库助手' }}</div>
            <!-- Markdown 渲染内容 -->
            <div
              v-if="msg.role === 'assistant'"
              class="markdown-body"
              v-html="renderMarkdown(msg.content)"
            ></div>
            <div v-else class="text-content">{{ msg.content }}</div>
          </div>
        </div>

        <!-- 思考中状态 -->
        <div v-if="isLoading" class="loading-row">
          <div class="typing-indicator"><span></span><span></span><span></span></div>
          <p>AI 正在检索政策库并思考中...</p>
        </div>
      </div>
    </main>

    <!-- 3. 底部输入区 -->
    <footer class="input-footer">
      <div class="input-container">
        <textarea
          v-model="queryInput"
          @keydown.enter.prevent="sendQuery"
          placeholder="请输入您想查询的政策问题..."
          rows="1"
        ></textarea>
        <button v-if="isLoading" @click="stopGeneration" class="btn-stop">停止</button>
        <button v-else @click="sendQuery" :disabled="!queryInput.trim()" class="btn-send">
          发送
        </button>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onUnmounted } from 'vue'
import MarkdownIt from 'markdown-it'

// Markdown 配置
const md = new MarkdownIt()
const renderMarkdown = (content: string) => (content ? md.render(content) : '')

// 状态变量
const selectedFile = ref<File | null>(null)
const isUploading = ref(false)
const uploadStatus = ref('')
const queryInput = ref('')
const messages = ref<{ role: 'user' | 'assistant'; content: string }[]>([])
const isLoading = ref(false)
const chatContainer = ref<HTMLElement | null>(null)
const API_BASE = '/api'
let abortController = new AbortController()

// 上传文档逻辑
const handleFileChange = (e: Event) => {
  const target = e.target as HTMLInputElement
  selectedFile.value = target.files?.[0] ?? null
}

const uploadFile = async () => {
  if (!selectedFile.value) return
  isUploading.value = true
  uploadStatus.value = ''
  const formData = new FormData()
  formData.append('file', selectedFile.value)

  try {
    const response = await fetch(`${API_BASE}/upload`, { method: 'POST', body: formData })
    uploadStatus.value = response.ok ? '✅ 文档上传并解析成功' : '❌ 上传失败'
    if (response.ok) selectedFile.value = null
  } catch (err) {
    uploadStatus.value = '❌ 网络连接失败'
  } finally {
    isUploading.value = false
  }
}

// 对话逻辑
const sendQuery = async () => {
  if (!queryInput.value.trim() || isLoading.value) return

  // 1. 强制终止之前的请求并清理状态
  abortController.abort()
  abortController = new AbortController()

  const userText = queryInput.value
  messages.value.push({ role: 'user', content: userText })
  queryInput.value = ''
  isLoading.value = true

  messages.value.push({ role: 'assistant', content: '' })
  const lastIdx = messages.value.length - 1

  try {
    // 2. 使用原生 fetch，它绝对不会自动重试
    const response = await fetch(`${API_BASE}/chat?query=${encodeURIComponent(userText)}`, {
      method: 'GET',
      signal: abortController.signal,
    })

    if (!response.ok) {
      throw new Error(`HTTP Error: ${response.status}`)
    }

    if (!response.body) throw new Error('ReadableStream not supported')

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = '' // 处理 SSE 数据分片

    // 3. 手动读取流
    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      // 解码当前片段并拼接到 buffer
      buffer += decoder.decode(value, { stream: true })

      // 按 SSE 规范解析行
      const lines = buffer.split('\n')
      // 最后一行可能不完整，留到下个 chunk
      buffer = lines.pop() || ''

      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('data:')) {
          const content = trimmed.substring(5).replace(/^\s/, '')
          if (content) {
            messages.value[lastIdx].content += content
            scrollToBottom()
          }
        }
      }
    }
  } catch (error: unknown) {
    console.error('请求终结:', error)
    if (error instanceof Error && error.name === 'AbortError') {
      console.log('用户手动停止了请求')
    } else {
      messages.value[lastIdx].content += '\n\n⚠️ [已停止] 连接异常或服务器响应超时。'
    }
  } finally {
    isLoading.value = false
  }
}

const stopGeneration = () => {
  abortController.abort()
  isLoading.value = false
}
const scrollToBottom = () => {
  nextTick(() => {
    if (chatContainer.value) chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  })
}
onUnmounted(() => abortController.abort())
</script>

<style scoped>
/* 全局基础样式 */
.app-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f8fafc;
  color: #1e293b;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
}

/* 顶部样式 */
.glass-header {
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid #e2e8f0;
  padding: 12px 24px;
  position: sticky;
  top: 0;
  z-index: 100;
}
.header-content {
  max-width: 1000px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
}
.logo h1 {
  font-size: 1.2rem;
  font-weight: 700;
  margin: 0;
  background: linear-gradient(90deg, #2563eb, #7c3aed);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}
.upload-section {
  display: flex;
  gap: 10px;
}

/* 按钮与输入美化 */
.file-label {
  padding: 8px 16px;
  background: #f1f5f9;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
}
.btn-upload {
  background: #2563eb;
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 8px;
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-upload:disabled {
  background: #94a3b8;
}

/* 聊天主体 */
.chat-main {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}
.chat-wrapper {
  max-width: 800px;
  margin: 0 auto;
}

/* 消息气泡 */
.message-row {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}
.message-row.user {
  flex-direction: row-reverse;
}
.avatar {
  width: 36px;
  height: 36px;
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}
.bubble {
  max-width: 80%;
  padding: 12px 16px;
  border-radius: 16px;
  position: relative;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}
.user .bubble {
  background: #2563eb;
  color: white;
  border-bottom-right-radius: 2px;
}
.assistant .bubble {
  background: white;
  border: 1px solid #e2e8f0;
  border-bottom-left-radius: 2px;
}
.role-name {
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  margin-bottom: 4px;
  opacity: 0.7;
}

/* 欢迎卡片 */
.welcome-card {
  text-align: center;
  padding: 60px 20px;
  color: #64748b;
}
.bot-avatar-large {
  font-size: 50px;
  margin-bottom: 20px;
}
.hints {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 20px;
}
.hints span {
  background: white;
  border: 1px solid #e2e8f0;
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 12px;
}

/* 底部输入框 */
.input-footer {
  padding: 20px;
  background: white;
  border-top: 1px solid #e2e8f0;
}
.input-container {
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  gap: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  padding: 8px;
  border-radius: 12px;
}
textarea {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  padding: 8px;
  resize: none;
  font-size: 14px;
}
.btn-send {
  background: #2563eb;
  color: white;
  border: none;
  padding: 0 20px;
  border-radius: 8px;
  font-weight: 600;
  cursor: pointer;
}
.btn-send:disabled {
  background: #cbd5e1;
}
.btn-stop {
  background: #ef4444;
  color: white;
  border: none;
  padding: 0 20px;
  border-radius: 8px;
  cursor: pointer;
}

/* Markdown 样式 */
.markdown-body :deep(p) {
  margin-bottom: 10px;
  line-height: 1.6;
}
.markdown-body :deep(ul) {
  padding-left: 20px;
  margin-bottom: 10px;
}
.markdown-body :deep(li) {
  list-style: disc;
  margin-bottom: 4px;
}
.markdown-body :deep(strong) {
  font-weight: bold;
}

/* 思考动画 */
.typing-indicator {
  display: flex;
  gap: 4px;
  margin-bottom: 8px;
}
.typing-indicator span {
  width: 6px;
  height: 6px;
  background: #2563eb;
  border-radius: 50%;
  animation: blink 1.4s infinite both;
}
.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}
.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes blink {
  0%,
  80%,
  100% {
    opacity: 0;
  }
  40% {
    opacity: 1;
  }
}

.hidden-input {
  display: none;
}
</style>
