<script setup>
import {
  Briefcase,
  Collection,
  DataAnalysis,
  Download,
  Files,
  Notebook,
  OfficeBuilding,
  Reading,
  SwitchButton,
  User
} from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const activeMenu = computed(() => route.path)

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="dashboard-layout">
    <el-aside class="sidebar" width="248px">
      <div class="brand">学术档案</div>
      <el-menu router :default-active="activeMenu">
        <el-menu-item index="/dashboard">
          <el-icon><User /></el-icon>
          <span>工作台</span>
        </el-menu-item>
        <el-menu-item-group title="简介资料">
          <el-menu-item index="/dashboard/profile">
            <el-icon><Notebook /></el-icon>
            <span>教师资料</span>
          </el-menu-item>
          <el-menu-item index="/dashboard/academic-qualifications">
            <el-icon><Collection /></el-icon>
            <span>学历</span>
          </el-menu-item>
          <el-menu-item index="/dashboard/teaching-areas">
            <el-icon><Reading /></el-icon>
            <span>教学方向</span>
          </el-menu-item>
          <el-menu-item index="/dashboard/research-areas">
            <el-icon><DataAnalysis /></el-icon>
            <span>研究方向</span>
          </el-menu-item>
          <el-menu-item index="/dashboard/professional-services">
            <el-icon><Briefcase /></el-icon>
            <span>专业服务</span>
          </el-menu-item>
          <el-menu-item index="/dashboard/working-experiences">
            <el-icon><OfficeBuilding /></el-icon>
            <span>工作经历</span>
          </el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group title="学术成果">
          <el-menu-item index="/dashboard/projects">
            <el-icon><Briefcase /></el-icon>
            <span>科研项目</span>
          </el-menu-item>
          <el-menu-item index="/dashboard/teaching-courses">
            <el-icon><Reading /></el-icon>
            <span>授课记录</span>
          </el-menu-item>
          <el-menu-item index="/dashboard/papers">
            <el-icon><Notebook /></el-icon>
            <span>论文成果</span>
          </el-menu-item>
          <el-menu-item index="/dashboard/bibtex-import">
            <el-icon><Notebook /></el-icon>
            <span>BibTeX 导入</span>
          </el-menu-item>
          <el-menu-item index="/dashboard/ocr">
            <el-icon><Files /></el-icon>
            <span>OCR 识别</span>
          </el-menu-item>
          <el-menu-item index="/dashboard/patents">
            <el-icon><DataAnalysis /></el-icon>
            <span>专利</span>
          </el-menu-item>
          <el-menu-item index="/dashboard/certificates">
            <el-icon><Collection /></el-icon>
            <span>证书</span>
          </el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group title="工具">
          <el-menu-item index="/dashboard/export">
            <el-icon><Download /></el-icon>
            <span>导出</span>
          </el-menu-item>
          <el-menu-item index="/dashboard/ai-generation">
            <el-icon><DataAnalysis /></el-icon>
            <span>AI 生成</span>
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div class="current-user">
          <strong>{{ authStore.user?.displayName || authStore.user?.username }}</strong>
          <span>{{ authStore.user?.email }}</span>
        </div>
        <el-button :icon="SwitchButton" @click="handleLogout">退出</el-button>
      </el-header>
      <el-main class="content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.dashboard-layout {
  min-height: 100vh;
}

.sidebar {
  background: #ffffff;
  border-right: 1px solid #e5e7eb;
}

.brand {
  height: 64px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  font-size: 18px;
  font-weight: 700;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
}

.current-user {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.current-user span {
  font-size: 13px;
  color: #6b7280;
}

.content {
  background: #f5f7fb;
}
</style>
