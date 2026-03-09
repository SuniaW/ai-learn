<template>
  <div class="home-wrapper">
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card shadow="never" class="hero-card">
          <h1>欢迎使用 AI 智能平台</h1>
          <p>基于 RAG 技术，连接您的私有知识库，提供精准的 AI 问答体验。</p>
          <el-button type="primary" size="large" @click="$router.push('/chat/rag')"
            >立即开始</el-button
          >
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="8" v-for="item in features" :key="item.title">
        <el-card hoverable class="feature-item">
          <el-icon :size="40" :color="item.color"><component :is="item.icon" /></el-icon>
          <h3>{{ item.title }}</h3>
          <p>{{ item.desc }}</p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { Ship, Connection, Cpu } from '@element-plus/icons-vue'
const features = [
  { title: '知识检索', desc: '毫秒级检索百万级文档数据', icon: Ship, color: '#409eff' },
  {
    title: '多模态支持',
    desc: '支持 PDF、Word、Excel 等多种格式',
    icon: Connection,
    color: '#67c23a',
  },
  { title: '模型切换', desc: '自由切换 GPT-4, Claude3 等主流模型', icon: Cpu, color: '#e6a23c' },
]
</script>

<style scoped>
.hero-card {
  text-align: center;
  padding: 40px;
  background: linear-gradient(135deg, #ecf5ff 0%, #ffffff 100%);
}
.feature-item {
  text-align: center;
  height: 200px;
}
</style>
