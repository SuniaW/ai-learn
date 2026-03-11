<template>
  <div class="page-container">
    <!-- 1. 顶部 Header - 更加精致的边框和背景 -->
    <div class="chat-header">
      <div class="header-left">
        <div class="logo-wrapper">
          <div class="logo-box">
            <el-icon size="20"><Cpu /></el-icon>
          </div>
          <div class="title-wrapper">
            <h2 class="title-text">技术站 · 智库问答</h2>
            <div class="status-badge">
              <span class="dot"></span>
              RAG Engine V2.0
            </div>
          </div>
        </div>
      </div>

      <div class="upload-actions">
        <div class="action-buttons">
          <el-popover
            v-if="uiFileList.length > 0"
            placement="bottom-end"
            :width="320"
            trigger="hover"
            popper-class="custom-file-popover"
          >
            <template #reference>
              <el-button link class="file-count-trigger">
                <div class="count-badge-wrapper">
                  <el-icon><Files /></el-icon>
                  <span>待处理文档</span>
                  <span class="count-tag">{{ uiFileList.length }}</span>
                </div>
              </el-button>
            </template>

            <div class="popover-file-container">
              <div class="popover-header">
                <span class="title">已选文件清单</span>
                <span class="sub-title">准备上传至知识库</span>
              </div>

              <div class="popover-list-wrapper">
                <div v-for="file in uiFileList" :key="file.uid" class="popover-item">
                  <div class="file-info">
                    <div class="file-icon-box">
                      <el-icon><Document /></el-icon>
                    </div>
                    <div class="file-details">
                      <span class="file-name" :title="file.name">{{ file.name }}</span>
                      <span class="file-size">{{ (file.size / 1024).toFixed(1) }} KB</span>
                    </div>
                  </div>
                  <div class="item-actions">
                    <el-button
                      link
                      type="danger"
                      class="remove-btn"
                      @click="handleFileRemove(file)"
                    >
                      <el-icon><CircleClose /></el-icon>
                    </el-button>
                  </div>
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
          >
            <template #trigger>
              <el-button class="secondary-btn" round :icon="Link">选择文档</el-button>
            </template>
          </el-upload>
          <el-button
            type="primary"
            class="primary-btn"
            round
            :disabled="selectedFiles.length === 0"
            :loading="isUploading"
            @click="uploadFiles"
          >
            上传入库
          </el-button>
        </div>
      </div>
    </div>

    <!-- 批量上传进度条 - 细长简约设计 -->
    <div v-if="isUploading" class="global-progress">
      <el-progress :percentage="uploadPercent" :stroke-width="3" :show-text="false" />
    </div>

    <!-- 2. 中间：消息显示区 -->
    <div class="message-container" ref="chatContainer">
      <div class="message-list-inner">
        <!-- 欢迎页 - 深度美化示例卡片 -->
        <div v-if="messages.length === 0 && !isLoading" class="welcome-section">
          <div class="welcome-hero">
            <div class="hero-icon-container">
              <div class="floating-icon">👩‍💻</div>
            </div>
            <h1 class="welcome-title">技术文档智能助手</h1>
            <p class="welcome-desc">上传规格书或代码，为您提供精准的关联分析。</p>
          </div>

          <div class="example-section">
            <div class="section-divider">
              <span class="divider-line"></span>
              <span class="divider-text">您可以这样问我</span>
              <span class="divider-line"></span>
            </div>
            <div class="example-grid">
              <div
                v-for="(item, index) in exampleQuestions"
                :key="index"
                class="modern-card"
                @click="handleExampleClick(item.query)"
              >
                <div class="card-accent" :style="{ background: item.color }"></div>
                <div class="card-content">
                  <div class="card-header">
                    <span class="card-icon">{{ item.icon }}</span>
                    <span class="card-title">{{ item.query }}</span>
                  </div>
                  <div class="card-action">
                    <span>点击提问</span>
                    <el-icon><ArrowRight /></el-icon>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-for="(msg, index) in messages" :key="index" :class="['msg-wrapper', msg.role]">
          <div class="avatar-container">
            <el-avatar :size="40" :src="msg.role === 'assistant' ? botAvatar : userAvatar" />
          </div>
          <div class="msg-content-area">
            <div class="msg-meta" v-if="msg.role === 'assistant'">
              <span class="sender-name">智库助手</span>
              <span v-if="msg.duration" class="stat-badge">
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
              <div v-else class="user-text">{{ msg.content }}</div>
            </div>
          </div>
        </div>

        <!-- 思考中状态 -->
        <div v-if="isLoading && isWaiting" class="msg-wrapper assistant">
          <div class="avatar-container">
            <el-avatar :size="40" :src="botAvatar" />
          </div>
          <div class="msg-content-area">
            <div class="thinking-card">
              <div class="loader-dots"><span></span><span></span><span></span></div>
              <span class="thinking-text"
                >AI 正在思考... ({{ currentThinkingTime.toFixed(1) }}s)</span
              >
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 3. 底部：输入区 - 悬浮式设计 -->
    <div class="footer-area">
      <div class="input-container-wrapper">
        <div class="input-card-refined">
          <el-input
            v-model="queryInput"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 6 }"
            placeholder="在此输入您的疑问..."
            @keydown.enter.prevent="sendQuery"
          />
          <div class="input-bottom-bar">
            <div class="bar-left">
              <div class="mode-tag">
                <el-icon><Files /></el-icon>
                <span>多文档分析模式</span>
              </div>
            </div>
            <div class="bar-right">
              <el-button
                v-if="isLoading"
                type="danger"
                circle
                :icon="CircleClose"
                class="stop-btn"
                @click="stopGeneration"
              />
              <el-button
                v-else
                type="primary"
                class="send-btn"
                :disabled="!queryInput.trim()"
                @click="sendQuery"
              >
                <span>发送</span>
                <el-icon><Promotion /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
        <div class="input-footer-tip">内容由 AI 生成，请核实重要信息</div>
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

const handleFileRemove = (file: UploadFile, fileList?: UploadFile[]) => {
  if (fileList) {
    selectedFiles.value = fileList.map((f) => f.raw as File)
    uiFileList.value = fileList as UploadUserFile[]
  } else {
    const index = uiFileList.value.indexOf(file as UploadUserFile)
    if (index !== -1) {
      uiFileList.value.splice(index, 1)
      selectedFiles.value.splice(index, 1)
    }
  }
}

const uploadFiles = async () => {
  if (selectedFiles.value.length === 0) return
  isUploading.value = true
  uploadPercent.value = 0
  const formData = new FormData()
  selectedFiles.value.forEach((file) => {
    formData.append('files', file)
  })
  try {
    await axios.post('/api/upload', formData, {
      onUploadProgress: (p) => {
        uploadPercent.value = Math.round((p.loaded * 100) / (p.total || 100))
      },
    })
    ElMessage.success('上传成功')
    selectedFiles.value = []
    uiFileList.value = []
  } catch (e) {
    ElMessage.error('上传失败')
  } finally {
    setTimeout(() => {
      isUploading.value = false
    }, 800)
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
    const response = await fetch(
      `/api/chat?query=${encodeURIComponent(userText)}&chatId=${chatId}`,
      { signal: abortController.signal },
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

const handleExampleClick = (query: string) => {
  queryInput.value = query
  sendQuery()
}
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
/* 全局基础样式 */
.page-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 60px);
  background-color: #f9fafb;
  color: #1f2937;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

/* Header 优化 */
.chat-header {
  flex-shrink: 0;
  height: 64px;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  padding: 0 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e5e7eb;
  z-index: 10;
}

.logo-wrapper {
  display: flex;
  align-items: center;
  gap: 14px;
}

.logo-box {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.title-text {
  font-size: 16px;
  font-weight: 700;
  color: #111827;
  margin: 0;
  letter-spacing: -0.01em;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #3b82f6;
  background: #eff6ff;
  padding: 2px 8px;
  border-radius: 20px;
  margin-top: 2px;
}

.status-badge .dot {
  width: 6px;
  height: 6px;
  background: #3b82f6;
  border-radius: 50%;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% {
    transform: scale(0.95);
    opacity: 0.5;
  }
  50% {
    transform: scale(1.1);
    opacity: 1;
  }
  100% {
    transform: scale(0.95);
    opacity: 0.5;
  }
}

/* 上传动作区 */
.action-buttons {
  display: flex;
  gap: 12px;
}

.file-count-trigger {
  color: #6b7280 !important;
  font-size: 13px;
  font-weight: 500;
  transition: all 0.2s;
}

.file-count-trigger:hover {
  color: #3b82f6 !important;
}

.secondary-btn {
  background: white !important;
  border: 1px solid #e5e7eb !important;
  color: #374151 !important;
  font-weight: 500;
}

.primary-btn {
  background: #3b82f6 !important;
  border: none !important;
  font-weight: 600;
  box-shadow: 0 2px 6px rgba(59, 130, 246, 0.2);
}

/* 消息容器 */
.message-container {
  flex: 1;
  max-height: 360px;
  padding: 10px 0;
}

.message-list-inner {
  max-width: 840px;
  margin: 0 auto;
  padding: 0 24px;
}

/* 欢迎界面 */
.welcome-hero {
  text-align: center;
  margin-bottom: 8px;
}

.hero-icon-container {
  margin-bottom: 20px;
}

.floating-icon {
  font-size: 48px;
  animation: float 3s ease-in-out infinite;
}

@keyframes float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

.welcome-title {
  font-size: 28px;
  font-weight: 800;
  color: #111827;
  margin-bottom: 12px;
}

.welcome-desc {
  color: #6b7280;
  font-size: 16px;
}

.section-divider {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24px;
  gap: 16px;
}

.divider-line {
  height: 1px;
  background: #e5e7eb;
  flex: 1;
  max-width: 100px;
}

.divider-text {
  font-size: 13px;
  color: #9ca3af;
  font-weight: 500;
}

/* 示例卡片网格 */
.example-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.modern-card {
  background: white;
  border: 1px solid #f3f4f6;
  border-radius: 16px;
  overflow: hidden;
  display: flex;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.modern-card:hover {
  transform: translateY(-4px);
  border-color: #3b82f6;
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.04);
}

.card-accent {
  width: 4px;
  flex-shrink: 0;
}

.card-content {
  padding: 4px;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-bottom: 2px;
}

.card-icon {
  font-size: 18px;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}

.card-action {
  font-size: 12px;
  color: #9ca3af;
  display: flex;
  align-items: center;
  gap: 4px;
}

.modern-card:hover .card-action {
  color: #3b82f6;
}

/* 聊天气泡样式 */
.msg-wrapper {
  display: flex;
  gap: 16px;
  margin-bottom: 32px;
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.msg-wrapper.user {
  flex-direction: row-reverse;
}

.msg-content-area {
  max-width: 85%;
}

.msg-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.sender-name {
  font-size: 13px;
  font-weight: 700;
  color: #374151;
}

.stat-badge {
  font-size: 11px;
  color: #3b82f6;
  background: #eff6ff;
  padding: 1px 6px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.msg-bubble {
  padding: 14px 18px;
  font-size: 15px;
  line-height: 1.6;
  border-radius: 18px;
  position: relative;
}

.user-bubble {
  background: #3b82f6;
  color: white;
  border-bottom-right-radius: 4px;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.2);
}

.assistant-bubble {
  background: white;
  border: 1px solid #e5e7eb;
  border-top-left-radius: 4px;
  color: #1f2937;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.02);
}

/* 思考中状态美化 */
.thinking-card {
  background: white;
  border: 1px solid #e5e7eb;
  padding: 12px 20px;
  border-radius: 18px;
  border-top-left-radius: 4px;
  display: flex;
  align-items: center;
  gap: 12px;
  width: fit-content;
}

.loader-dots {
  display: flex;
  gap: 4px;
}

.loader-dots span {
  width: 6px;
  height: 6px;
  background: #3b82f6;
  border-radius: 50%;
  animation: dot-pulse 1.4s infinite ease-in-out both;
}

.loader-dots span:nth-child(1) {
  animation-delay: -0.32s;
}
.loader-dots span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes dot-pulse {
  0%,
  80%,
  100% {
    transform: scale(0);
    opacity: 0.3;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.thinking-text {
  font-size: 13px;
  color: #6b7280;
}

/* 底部输入框优化 */
.footer-area {
  flex-shrink: 0;
  padding: 20px 0 32px;
  background: linear-gradient(transparent, #f9fafb 40%);
}

.input-container-wrapper {
  max-width: 840px;
  margin: 0 auto;
  padding: 0 24px;
}

.input-card-refined {
  background: white;
  border: 1px solid #d1d5db;
  border-radius: 20px;
  padding: 12px 16px;
  transition: all 0.3s;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
}

.input-card-refined:focus-within {
  border-color: #3b82f6;
  box-shadow: 0 4px 24px rgba(59, 130, 246, 0.12);
}

:deep(.el-textarea__inner) {
  border: none !important;
  box-shadow: none !important;
  font-size: 15px;
  padding: 4px 0 !important;
  color: #1f2937;
  resize: none !important;
}

.input-bottom-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #f3f4f6;
}

.mode-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #9ca3af;
  font-weight: 500;
}

.send-btn {
  height: 36px !important;
  padding: 0 20px !important;
  font-weight: 600 !important;
  border-radius: 12px !important;
}

.input-footer-tip {
  text-align: center;
  font-size: 11px;
  color: #9ca3af;
  margin-top: 8px;
}

/* 进度条 */
.global-progress {
  position: absolute;
  top: 63px;
  left: 0;
  right: 0;
  z-index: 100;
}

/* Markdown 样式微调 */
:deep(.markdown-body) {
  font-size: 15px;
  color: #1f2937;
}

:deep(.markdown-body pre) {
  background-color: #1e293b;
  border-radius: 8px;
  margin: 12px 0;
}

/* --- 触发按钮美化 --- */
.file-count-trigger {
  border-radius: 8px !important;
  transition: all 0.2s !important;
}

.file-count-trigger:hover {
  background: #f3f4f6 !important;
}

.count-badge-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #4b5563;
}

.count-tag {
  background: #3b82f6;
  color: white;
  font-size: 11px;
  padding: 0 6px;
  border-radius: 10px;
  min-width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

/* --- Popover 内容区 --- */
.popover-file-container {
  padding: 4px;
}

.popover-header {
  padding: 8px 12px 12px;
  border-bottom: 1px solid #f3f4f6;
  margin-bottom: 8px;
}

.popover-header .title {
  display: block;
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}

.popover-header .sub-title {
  font-size: 11px;
  color: #9ca3af;
}

/* 列表滚动条优化 */
.popover-list-wrapper {
  max-height: 280px;
  overflow-y: auto;
  padding-right: 4px;
}

.popover-list-wrapper::-webkit-scrollbar {
  width: 4px;
}

.popover-list-wrapper::-webkit-scrollbar-thumb {
  background: #e5e7eb;
  border-radius: 4px;
}

/* 单个文件条目 */
.popover-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px;
  border-radius: 10px;
  transition: all 0.2s;
  margin-bottom: 2px;
  group: hover; /* 逻辑上配合 hover */
}

.popover-item:hover {
  background-color: #f8fafc;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.file-icon-box {
  width: 32px;
  height: 32px;
  background: #eff6ff;
  color: #3b82f6;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  flex-shrink: 0;
}

.file-details {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.file-name {
  font-size: 13px;
  color: #374151;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-size {
  font-size: 11px;
  color: #9ca3af;
}

/* 删除按钮交互 */
.remove-btn {
  opacity: 0; /* 默认隐藏 */
  padding: 4px !important;
  transition: all 0.2s !important;
}

.popover-item:hover .remove-btn {
  opacity: 1; /* 鼠标移入条目时显示 */
}

.remove-btn:hover {
  background: #fee2e2 !important;
  color: #ef4444 !important;
  border-radius: 50%;
}

/* 全局覆盖 Element Plus Popover 样式 (可选) */
:global(.el-popover.custom-file-popover) {
  padding: 12px !important;
  border-radius: 16px !important;
  box-shadow:
    0 10px 25px -5px rgba(0, 0, 0, 0.1),
    0 8px 10px -6px rgba(0, 0, 0, 0.1) !important;
  border: 1px solid #e5e7eb !important;
}
</style>
