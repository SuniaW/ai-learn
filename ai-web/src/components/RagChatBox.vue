<template>
  <div class="page-container">
    <!-- 1. 顶部：Element Plus 风格 Header -->
    <div class="chat-header">
      <div class="header-info">
        <span class="header-icon">📚</span>
        <h2>政策文档智库</h2>
      </div>

      <div class="upload-actions">
        <el-upload
          action="#"
          :auto-upload="false"
          :on-change="handleFileChange"
          :show-file-list="false"
        >
          <template #trigger>
            <el-button plain :icon="Link">
              {{ selectedFile ? selectedFile.name : '选择政策文件' }}
            </el-button>
          </template>
          <el-button
            type="primary"
            :disabled="!selectedFile"
            :loading="isUploading"
            @click="uploadFile"
            style="margin-left: 10px"
          >
            上传入库
          </el-button>
        </el-upload>
      </div>
    </div>

    <!-- 2. 中间：消息显示区 (唯一滚动区域) -->
    <div class="message-container" ref="chatContainer">
      <div class="message-list-inner">
        <!-- 欢迎卡片 -->
        <el-empty v-if="messages.length === 0" description=" ">
          <template #default>
            <div class="welcome-box">
              <div class="bot-icon">🤖</div>
              <h3>您好！我是您的政策助手</h3>
              <p>您可以上传政策文档，我会基于文档为您提供精准的咨询服务。</p>
              <div class="hint-tags">
                <el-tag
                  v-for="tag in hints"
                  :key="tag"
                  @click="queryInput = tag"
                  class="clickable-tag"
                >
                  {{ tag }}
                </el-tag>
              </div>
            </div>
          </template>
        </el-empty>

        <!-- 消息列表 -->
        <div v-for="(msg, index) in messages" :key="index" :class="['msg-row', msg.role]">
          <el-avatar :size="36" :src="msg.role === 'user' ? userAvatar : botAvatar" />
          <div class="msg-content">
            <div class="msg-meta">
              <span class="role-label">{{
                msg.role === 'user' ? '我的提问' : '政策智库助手'
              }}</span>
              <span v-if="msg.duration" class="time-label">⏱️ {{ msg.duration.toFixed(1) }}s</span>
            </div>
            <el-card shadow="never" class="msg-bubble">
              <div
                v-if="msg.role === 'assistant'"
                class="markdown-body"
                v-html="renderMarkdown(msg.content)"
              ></div>
              <div v-else class="text-raw">{{ msg.content }}</div>
            </el-card>
          </div>
        </div>

        <!-- 思考中 -->
        <div v-if="isLoading" class="msg-row assistant">
          <el-avatar :size="36" :src="botAvatar" />
          <div class="msg-content">
            <div class="thinking-box">
              <div class="dot-typing"></div>
              <span>AI 正在思考... ({{ currentThinkingTime.toFixed(1) }}s)</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 3. 底部：固定输入区 -->
    <div class="footer-input">
      <div class="input-card">
        <el-input
          v-model="queryInput"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 4 }"
          placeholder="请输入您想查询的政策问题..."
          @keydown.enter.prevent="sendQuery"
        />
        <div class="input-actions">
          <el-button v-if="isLoading" type="danger" @click="stopGeneration" plain
            >停止生成</el-button
          >
          <el-button v-else type="primary" :disabled="!queryInput.trim()" @click="sendQuery">
            发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onUnmounted } from 'vue'
import { Link } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'

// ... (逻辑代码保持不变，仅修改了少量 UI 适配变量)
const hints = ['咨询买房补贴', '了解人才落户政策', '创业扶持资金申请']
const userAvatar = 'https://api.dicebear.com/7.x/avataaars/svg?seed=user'
const botAvatar = 'https://api.dicebear.com/7.x/bottts/svg?seed=bot'

// Markdown 配置
const md = new MarkdownIt()
const renderMarkdown = (content: string) => (content ? md.render(content) : '')

// 状态变量
interface Message {
  role: 'user' | 'assistant'
  content: string
  duration?: number // 记录思考时长
}

const selectedFile = ref<File | null>(null)
const isUploading = ref(false)
const uploadStatus = ref('')
const queryInput = ref('')
const messages = ref<Message[]>([])
const isLoading = ref(false)
const chatContainer = ref<HTMLElement | null>(null)
const API_BASE = '/api'
let abortController = new AbortController()

// 计时器变量
const currentThinkingTime = ref(0)
let thinkingTimer: number | null = null

const startThinkingTimer = () => {
  currentThinkingTime.value = 0
  thinkingTimer = window.setInterval(() => {
    currentThinkingTime.value += 0.1
  }, 100)
}

const stopThinkingTimer = () => {
  if (thinkingTimer) {
    clearInterval(thinkingTimer)
    thinkingTimer = null
  }
}

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

  abortController.abort()
  abortController = new AbortController()

  const userText = queryInput.value
  messages.value.push({ role: 'user', content: userText })
  queryInput.value = ''
  isLoading.value = true

  // 开始计时
  startThinkingTimer()

  messages.value.push({ role: 'assistant', content: '' })
  const lastIdx = messages.value.length - 1
  let isFirstChunk = true

  try {
    const response = await fetch(`${API_BASE}/chat?query=${encodeURIComponent(userText)}`, {
      method: 'GET',
      signal: abortController.signal,
    })

    if (!response.ok) throw new Error(`HTTP Error: ${response.status}`)
    if (!response.body) throw new Error('ReadableStream not supported')

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      // 核心：当收到第一块数据时，停止计时并记录时长
      if (isFirstChunk) {
        stopThinkingTimer()
        messages.value[lastIdx].duration = currentThinkingTime.value
        isFirstChunk = false
      }

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
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
    stopThinkingTimer()
    if (error instanceof Error && error.name === 'AbortError') {
      console.log('User stopped')
    } else {
      messages.value[lastIdx].content += '\n\n⚠️ [已停止] 服务响应超时或异常。'
    }
  } finally {
    isLoading.value = false
    stopThinkingTimer()
  }
}

const stopGeneration = () => {
  abortController.abort()
  isLoading.value = false
  stopThinkingTimer()
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatContainer.value) chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  })
}

onUnmounted(() => {
  abortController.abort()
  stopThinkingTimer()
})
</script>

<style scoped>
.el-empty{
  max-height: 200px;
}
/* 容器全屏化适配 */
.page-container {
  height: 100%; /* 继承 el-main 的高度 */
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 顶部工具栏 */
.chat-header {
  background: #fff;
  padding: 10px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.02);
  z-index: 10;
}
.header-info {
  display: flex;
  align-items: center;
  gap: 12px;
}
.header-info h2 {
  font-size: 16px;
  margin: 0;
  color: #303133;
}

/* 核心：消息区域自适应滚动 */
.message-container {
  flex: 1; /* 自动撑满剩余空间 */
  overflow-y: auto; /* 只有这里产生滚动条 */
  padding: 30px 20px;
  scroll-behavior: smooth;
}
.message-list-inner {
  max-width: 900px; /* 居中限制宽度，在大屏幕下更美观 */
  margin: 0 auto;
}

/* 消息气泡美化 */
.msg-row {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
  transition: all 0.3s ease;
}
.msg-row.user {
  flex-direction: row-reverse;
}

.msg-bubble {
  border-radius: 12px;
  margin-top: 4px;
  max-width: fit-content;
}
.user .msg-bubble {
  background-color: #409eff;
  color: #fff;
  border: none;
}
.assistant .msg-bubble {
  background-color: #fff;
  border: 1px solid #eef0f2;
}

.msg-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
  font-size: 12px;
  color: #909399;
}
.user .msg-meta {
  flex-direction: row-reverse;
}

/* 底部输入框美化 */
.footer-input {
  background-color: #fff;
  padding: 16px 20px 30px;
  border-top: 1px solid #f0f2f5;
}
.input-card {
  max-width: 900px;
  margin: 0 auto;
  border: 1px solid #dcdfe6;
  border-radius: 12px;
  padding: 8px;
  background: #fff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
}
.input-card :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  padding: 8px;
  font-size: 15px;
}
.input-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 8px;
}

/* 欢迎卡片美化 */
.welcome-box {
  text-align: center;
}
.bot-icon {
  font-size: 48px;
  margin-bottom: 16px;
}
.hint-tags {
  display: flex;
  gap: 8px;
  justify-content: center;
  margin-top: 16px;
}
.clickable-tag {
  cursor: pointer;
}
.clickable-tag:hover {
  border-color: #409eff;
}

/* 思考动画 */
.thinking-box {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #909399;
  font-size: 13px;
}
.dot-typing {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background-color: #409eff;
  animation: dot-beat 1s infinite alternate;
}
@keyframes dot-beat {
  to {
    transform: scale(1.5);
    opacity: 0.4;
  }
}
</style>
