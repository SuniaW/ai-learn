<template>
  <div class="page-container">
    <div class="chat-wrapper">
      <!-- 顶部标题 -->
      <header class="app-header">
        <div class="logo-area">
          <span class="icon">✨</span>
          <h1>AI 天气智能助手</h1>
        </div>
        <p class="subtitle">实时气象分析 & 穿衣建议</p>
      </header>

      <!-- 搜索区域 -->
      <div class="search-section">
        <div class="input-group" :class="{ 'is-focus': isFocus }">
          <span class="search-icon">📍</span>
          <input
            v-model="city"
            @keyup.enter="startStream"
            @focus="isFocus = true"
            @blur="isFocus = false"
            placeholder="你想了解哪个城市的天气？"
            :disabled="loading"
          />
          <button @click="startStream" :disabled="loading || !city.trim()" class="send-btn">
            <span v-if="!loading">立即查询</span>
            <div v-else class="dot-loader"></div>
          </button>
        </div>
      </div>

      <!-- 结果展示区域 -->
      <transition name="slide-up">
        <div class="result-container" v-if="answer || loading">
          <!-- 思考状态 -->
          <div class="status-bar" v-if="loading && !answer">
            <div class="pulse-icon"></div>
            <span>深度思考中... 已耗时 {{ timer }}s</span>
          </div>

          <!-- 回复卡片 -->
          <div class="ai-response-card">
            <div class="card-header">
              <span class="bot-tag">AI 助手</span>
              <span class="time-tag" v-if="!loading && timer > 0">耗时 {{ timer }}s</span>
            </div>

            <div class="content-area">
              <!-- 实时渲染 Markdown -->
              <div v-html="renderedAnswer" class="markdown-body"></div>
              <!-- 打字机光标 -->
              <span class="cursor" v-if="loading"></span>
            </div>
          </div>
        </div>
      </transition>

      <!-- 占位提示 -->
      <div class="empty-state" v-if="!answer && !loading">
        <div class="empty-icon">☁️</div>
        <p>输入城市名称，开启智能气象对话</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true,
})

const city = ref('')
const answer = ref('')
const loading = ref(false)
const isFocus = ref(false)
const timer = ref(0)
let timerId: any = null

const renderedAnswer = computed(() => {
  if (!answer.value) return ''
  return md.render(answer.value)
})

const startStream = async () => {
  if (!city.value.trim() || loading.value) return

  answer.value = ''
  loading.value = true
  timer.value = 0
  timerId = setInterval(() => timer.value++, 1000)

  try {
    const response = await fetch(`/ai/weather/stream?city=${encodeURIComponent(city.value)}`)

    if (!response.body) throw new Error('ReadableStream not supported')

    const reader = response.body.getReader()
    const decoder = new TextDecoder()

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      const chunk = decoder.decode(value, { stream: true })
      // 处理 SSE 数据格式
      const lines = chunk.split('\n')
      lines.forEach((line) => {
        if (line.startsWith('data:')) {
          answer.value += line.replace('data:', '').trim()
        } else if (line.trim() !== '' && !line.startsWith('event:')) {
          answer.value += line
        }
      })
    }
  } catch (error) {
    console.error('Stream Error:', error)
    answer.value = '### ⚠️ 系统提示\n网络连接异常，请检查后端服务是否启动。'
  } finally {
    loading.value = false
    clearInterval(timerId)
  }
}

onUnmounted(() => clearInterval(timerId))
</script>

<style scoped>
/* 容器背景设置 */
.page-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  display: flex;
  justify-content: center;
  padding: 40px 20px;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.chat-wrapper {
  width: 100%;
  max-width: 760px;
}

/* 标题样式 */
.app-header {
  text-align: center;
  margin-bottom: 40px;
}

.logo-area {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.logo-area h1 {
  font-size: 28px;
  color: #2c3e50;
  margin: 0;
  font-weight: 700;
  letter-spacing: -0.5px;
}

.subtitle {
  color: #7f8c8d;
  margin-top: 8px;
  font-size: 15px;
}

/* 搜索框优化 */
.search-section {
  margin-bottom: 30px;
}

.input-group {
  display: flex;
  align-items: center;
  background: white;
  padding: 8px 8px 8px 20px;
  border-radius: 16px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.05);
  border: 2px solid transparent;
  transition: all 0.3s ease;
}

.input-group.is-focus {
  border-color: #3498db;
  box-shadow: 0 10px 30px rgba(52, 152, 219, 0.15);
  transform: translateY(-2px);
}

.search-icon {
  font-size: 20px;
  margin-right: 12px;
}

input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 16px;
  color: #34495e;
  padding: 10px 0;
}

.send-btn {
  background: #2c3e50;
  color: white;
  border: none;
  padding: 12px 24px;
  border-radius: 12px;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 110px;
}

.send-btn:hover:not(:disabled) {
  background: #34495e;
  transform: scale(1.02);
}

.send-btn:disabled {
  background: #bdc3c7;
  cursor: not-allowed;
}

/* 加载动画 */
.dot-loader {
  width: 20px;
  height: 20px;
  border: 3px solid rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  border-top-color: #fff;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 结果卡片样式 */
.ai-response-card {
  background: white;
  border-radius: 20px;
  padding: 30px;
  box-shadow: 0 15px 35px rgba(0, 0, 0, 0.08);
  position: relative;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  border-bottom: 1px solid #f0f2f5;
  padding-bottom: 15px;
}

.bot-tag {
  background: #ebf5ff;
  color: #3498db;
  padding: 4px 12px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
}

.time-tag {
  color: #95a5a6;
  font-size: 12px;
}

/* Markdown 内容样式自定义 */
.content-area {
  line-height: 1.8;
  color: #2c3e50;
  font-size: 16px;
}

:deep(.markdown-body) h3 {
  color: #2c3e50;
  margin: 20px 0 10px;
}

:deep(.markdown-body) p {
  margin-bottom: 12px;
}

:deep(.markdown-body) strong {
  color: #e67e22;
}

:deep(.markdown-body) ul {
  padding-left: 20px;
  margin-bottom: 15px;
}

/* 光标动画 */
.cursor {
  display: inline-block;
  width: 2px;
  height: 1.2em;
  background-color: #3498db;
  margin-left: 4px;
  vertical-align: middle;
  animation: blink 1s infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

/* 状态栏 */
.status-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 15px;
  color: #3498db;
  font-size: 14px;
  padding-left: 10px;
}

.pulse-icon {
  width: 8px;
  height: 8px;
  background: #3498db;
  border-radius: 50%;
  box-shadow: 0 0 0 0 rgba(52, 152, 219, 0.4);
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  70% {
    box-shadow: 0 0 0 10px rgba(52, 152, 219, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(52, 152, 219, 0);
  }
}

/* 空状态 */
.empty-state {
  text-align: center;
  margin-top: 80px;
  color: #95a5a6;
}

.empty-icon {
  font-size: 60px;
  margin-bottom: 20px;
  opacity: 0.5;
}

/* 动画效果 */
.slide-up-enter-active {
  transition: all 0.4s ease-out;
}
.slide-up-enter-from {
  opacity: 0;
  transform: translateY(20px);
}
</style>
