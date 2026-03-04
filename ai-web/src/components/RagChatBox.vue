<template>
  <div class="chat-container">
    <div class="messages" ref="msgBox">
      <div v-for="(msg, index) in messages" :key="index" :class="['message', msg.role]">
        <div class="content" v-html="renderMarkdown(msg.content)"></div>
      </div>
    </div>

    <div class="input-area">
      <input
        v-model="userInput"
        @keyup.enter="sendMessage"
        placeholder="请输入政策问题..."
        :disabled="loading"
      />
      <button @click="sendMessage" :disabled="loading">发送</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt()
const userInput = ref('')
const messages = ref<{ role: string; content: string }[]>([])
const loading = ref(false)

const renderMarkdown = (text: string) => {
  return md.render(text)
}

const sendMessage = async () => {
  if (!userInput.value.trim()) return

  const query = userInput.value
  messages.value.push({ role: 'user', content: query })
  userInput.value = ''
  loading.value = true

  // 预占位 Assistant 的回复
  const assistantMsg = { role: 'assistant', content: '' }
  messages.value.push(assistantMsg)

  try {
    const response = await fetch(`/api/chat?query=${encodeURIComponent(query)}`)
    const reader = response.body!.getReader()
    const decoder = new TextDecoder()

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      const chunk = decoder.decode(value, { stream: true })
      // 实时追加内容
      assistantMsg.content += chunk
    }
  } catch (error) {
    assistantMsg.content += `[系统错误: 响应中断${error}`
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* 简单的 CSS 样式 */
.chat-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}
.message {
  margin-bottom: 15px;
  padding: 10px;
  border-radius: 8px;
}
.user {
  background-color: #e3f2fd;
  text-align: right;
}
.assistant {
  background-color: #f5f5f5;
}
</style>
