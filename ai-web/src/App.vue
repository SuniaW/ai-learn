<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside width="240px">
      <div class="logo-vertical">
        <el-icon color="#409efc" :size="24"><Cpu /></el-icon>
        <span>AI 平台系统</span>
      </div>

      <el-menu :default-active="$route.path" router class="el-menu-vertical">
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
            <span>编程辅助专家</span>
          </el-menu-item>
          <el-menu-item index="/chat/analyze">
            <el-icon><DataAnalysis /></el-icon>
            <span>数据智能分析</span>
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>
    </el-aside>

    <!-- 右侧内容区 -->
    <el-container>
      <el-header>
        <div class="header-content">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="$route.path !== '/'">AI 问答</el-breadcrumb-item>
          </el-breadcrumb>
          <el-avatar
            size="small"
            src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png"
          />
        </div>
      </el-header>

      <el-main>
        <router-view v-slot="{ Component }">
          <transition name="el-fade-in-linear">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { HomeFilled, ChatDotRound, MagicStick, DataAnalysis, Cpu } from '@element-plus/icons-vue'
</script>

<style scoped>
.layout-container {
  height: 100vh;
}
.el-aside {
  background-color: #fff;
  border-right: 1px solid #dcdfe6;
}
.logo-vertical {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 10px;
  font-weight: bold;
  font-size: 18px;
}
.el-header {
  background-color: #fff;
  border-bottom: 1px solid #dcdfe6;
  display: flex;
  align-items: center;
}
.header-content {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.el-main {
  background-color: #f5f7fa;
}
</style>
