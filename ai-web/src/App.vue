<template>
  <el-container class="layout-container">
    <el-aside width="240px" class="aside-menu">
      <div class="logo-vertical">
        <el-icon color="#409efc" :size="24"><Cpu /></el-icon>
        <span>AI 平台系统</span>
      </div>

      <el-menu :default-active="$route.path" router class="custom-menu">
        <el-menu-item index="/">
          <el-icon><HomeFilled /></el-icon>
          <span>产品介绍</span>
        </el-menu-item>
        <el-menu-item-group title="AI 工作台">
          <el-menu-item index="/chat/rag">
            <el-icon><ChatDotRound /></el-icon>
            <span>RAG 知识库问答</span>
          </el-menu-item>
          <el-menu-item index="/chat/code">
            <el-icon><MagicStick /></el-icon>
            <span>AI 天气智能助手</span>
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>
    </el-aside>

    <el-container class="main-container">
      <el-header height="60px" class="global-header">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item v-if="route.path !== '/'">AI 问答</el-breadcrumb-item>
        </el-breadcrumb>
        <el-avatar
          size="small"
          src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png"
        />
      </el-header>

      <el-main class="content-body">
        <router-view v-slot="{ Component }">
          <transition name="el-fade-in-linear" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>
<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<style>
/* 全局样式强制去除外层滚动 */
html,
body,
#app {
  margin: 0;
  padding: 0;
  height: 100%;
  width: 100%;
  overflow: hidden; /* 核心：彻底切断外层滚动条 */
}
</style>

<style scoped>
.layout-container {
  height: 100vh;
  width: 100vw;
}
.aside-menu {
  background-color: #fff;
  border-right: 1px solid #e6e6e6;
  display: flex;
  flex-direction: column;
}
.custom-menu {
  border: none;
}
.logo-vertical {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 10px;
  font-weight: bold;
  font-size: 18px;
  border-bottom: 1px solid #f0f2f5;
}
.global-header {
  background: #fff;
  border-bottom: 1px solid #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}
.main-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
}
.content-body {
  padding: 0; /* 关键：移除默认 padding 让子页面自适应 */
  background-color: #f5f7fa;
  flex: 1;
  position: relative;
  overflow: hidden; /* 防止内部溢出影响外层 */
}
</style>
