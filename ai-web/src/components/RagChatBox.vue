<template>
  <div class="flex flex-col h-screen bg-gray-100 p-4">
    <!-- 顶部：文档上传区域 -->
    <div class="bg-white p-4 rounded-lg shadow-md mb-4">
      <h2 class="text-lg font-bold mb-2">📚 政策文档库</h2>
      <div class="flex items-center gap-4">
        <input
          type="file"
          @change="handleFileChange"
          class="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-full file:border-0 file:text-sm file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
        />
        <button
          @click="uploadFile"
          :disabled="!selectedFile || isUploading"
          class="px-6 py-2 bg-blue-600 text-white rounded-lg disabled:bg-gray-400 transition"
        >
          {{ isUploading ? '上传解析中...' : '上传文档' }}
        </button>
      </div>
      <p v-if="uploadStatus" class="mt-2 text-sm text-green-600">{{ uploadStatus }}</p>
    </div>

    <!-- 中间：聊天对话记录 -->
    <div class="flex-1 overflow-y-auto bg-white rounded-lg shadow-md p-4 mb-4" ref="chatContainer">
      <div
        v-for="(msg, index) in messages"
        :key="index"
        :class="['mb-4 flex', msg.role === 'user' ? 'justify-end' : 'justify-start']"
      >
        <div
          :class="[
            'max-w-[80%] px-4 py-2 rounded-lg',
            msg.role === 'user' ? 'bg-blue-600 text-white' : 'bg-gray-200 text-gray-800 shadow-sm',
          ]"
        >
          <div class="whitespace-pre-wrap">{{ msg.content }}</div>
        </div>
      </div>
      <!-- 加载状态显示 -->
      <div v-if="isLoading" class="text-gray-400 text-sm animate-pulse">
        AI 正在思考并检索政策库...
      </div>
    </div>

    <!-- 底部：输入框区域 -->
    <div class="bg-white p-4 rounded-lg shadow-md flex gap-2">
      <input
        v-model="queryInput"
        @keyup.enter="sendQuery"
        placeholder="请输入您想查询的政策问题..."
        class="flex-1 border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
      />
      <button
        v-if="isLoading"
        @click="stopGeneration"
        class="px-6 py-2 bg-red-500 text-white rounded-lg hover:bg-red-600"
      >
        停止
      </button>
      <button
        v-else
        @click="sendQuery"
        :disabled="!queryInput"
        class="px-6 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:bg-gray-400"
      >
        提问
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onUnmounted } from 'vue'
import { fetchEventSource } from '@microsoft/fetch-event-source'

// --- 类型定义 ---
interface Message {
  role: 'user' | 'assistant'
  content: string
}

// --- 状态变量 ---
const selectedFile = ref<File | null>(null)
const isUploading = ref(false)
const uploadStatus = ref('')
const queryInput = ref('')
const messages = ref<Message[]>([])
const isLoading = ref(false)
const chatContainer = ref<HTMLElement | null>(null)

// 后端 API 地址
const API_BASE = '/api'
// 用于控制/取消请求
let abortController = new AbortController()

// --- 核心逻辑：上传文档 ---
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
    const response = await fetch(`${API_BASE}/upload`, {
      method: 'POST',
      body: formData,
    })
    if (!response.ok) throw new Error('上传失败')
    const result = await response.text()
    uploadStatus.value = result
  } catch (error) {
    uploadStatus.value = '上传失败，请检查后端连接'
    console.error(error)
  } finally {
    isUploading.value = false
  }
}

// --- 核心逻辑：流式对话 ---
const sendQuery = async () => {
  if (!queryInput.value || isLoading.value) return

  const userText = queryInput.value
  messages.value.push({ role: 'user', content: userText })
  queryInput.value = ''
  isLoading.value = true

  // 1. 初始化回复框
  messages.value.push({ role: 'assistant', content: '' })
  const lastMsgIdx = messages.value.length - 1

  // 2. 准备 AbortController
  abortController = new AbortController()

  try {
    // 3. 使用 fetch-event-source 代替原生 fetch
    await fetchEventSource(`${API_BASE}/chat?query=${encodeURIComponent(userText)}`, {
      method: 'GET',
      signal: abortController.signal,
      openWhenHidden: true, // 标签页切到后台也不中断

      onmessage(msg: { data: string }) {
        // fetchEventSource 已经帮我们过滤了 "data:" 前缀
        if (msg.data) {
          messages.value[lastMsgIdx]!.content += msg.data
          scrollToBottom()
        }
      },

      onclose() {
        isLoading.value = false
      },

      onerror(err) {
        // 【核心优化】：如果发生错误（如后端连接超时），必须手动 throw 以停止自动重试
        // 否则它会每隔几秒重新发起请求，压垮后端的向量数据库
        isLoading.value = false
        console.error('SSE Error:', err)
        throw err
      },
    })
  } catch (error: unknown) {
    isLoading.value = false
    // 如果是因为手动点击“停止”触发的错误，不显示错误信息
    const message = error instanceof Error ? error.message : '未知错误'
    if (message !== 'AbortError') {
      if (!messages.value[lastMsgIdx]!.content) {
        messages.value[lastMsgIdx]!.content = '抱歉，系统响应异常，请检查网络后重试。'
      }
    }
  }
}

// 停止生成的逻辑
const stopGeneration = () => {
  abortController.abort()
  isLoading.value = false
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}

// 页面关闭时自动断开连接
onUnmounted(() => {
  abortController.abort()
})
</script>

<style scoped>
/* 自定义滚动条样式 */
::-webkit-scrollbar {
  width: 6px;
}
::-webkit-scrollbar-thumb {
  background-color: #cbd5e0;
  border-radius: 3px;
}
/* 加载动画效果 */
.animate-pulse {
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}
@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}
</style>
