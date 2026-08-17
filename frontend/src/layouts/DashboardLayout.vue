<script setup>
import {
  Briefcase,
  Collection,
  DataAnalysis,
  Download,
  Expand,
  Files,
  Fold,
  Notebook,
  OfficeBuilding,
  Reading,
  Monitor,
  Setting,
  SwitchButton,
  User
} from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '../stores/auth'
import { homeRouteForRole } from '../router/roleNavigation'
import {
  readSidebarCollapsed,
  saveSidebarCollapsed,
  sidebarWidth,
  SIDEBAR_COLLAPSED_KEY
} from './dashboardLayoutRules'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const collapsed = ref(readSidebarCollapsed(localStorage.getItem(SIDEBAR_COLLAPSED_KEY)))
const smallScreen = ref(false)
let mediaQuery

const activeMenu = computed(() => route.path)
const homeRoute = computed(() => homeRouteForRole(authStore.user?.role))
const menuCollapsed = computed(() => collapsed.value || smallScreen.value)
const asideWidth = computed(() => sidebarWidth(menuCollapsed.value))

function toggleSidebar() {
  collapsed.value = !collapsed.value
  saveSidebarCollapsed(collapsed.value)
}

function updateSmallScreen(event) {
  smallScreen.value = event.matches
}

onMounted(() => {
  mediaQuery = window.matchMedia('(max-width: 720px)')
  updateSmallScreen(mediaQuery)
  mediaQuery.addEventListener('change', updateSmallScreen)
})

onBeforeUnmount(() => mediaQuery?.removeEventListener('change', updateSmallScreen))

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="dashboard-layout">
    <el-aside class="sidebar" :class="{ 'is-collapsed': menuCollapsed }" :width="asideWidth">
      <div class="sidebar-header">
        <div class="brand" :title="menuCollapsed ? '学术档案' : undefined">
          <span class="brand-mark">档</span>
          <span v-show="!menuCollapsed" class="brand-text">学术档案</span>
        </div>
        <el-tooltip v-if="!smallScreen" :content="collapsed ? '展开菜单' : '收起菜单'" placement="right">
          <el-button class="collapse-button" text :icon="collapsed ? Expand : Fold" :aria-label="collapsed ? '展开菜单' : '收起菜单'" @click="toggleSidebar" />
        </el-tooltip>
      </div>
      <div class="sidebar-menu">
      <el-menu router :default-active="activeMenu" :collapse="menuCollapsed" :collapse-transition="false">
        <el-menu-item :index="homeRoute">
          <el-icon><User /></el-icon>
          <span>{{ authStore.user?.role === 'SUPER_ADMIN' ? '管理首页' : '工作台' }}</span>
        </el-menu-item>
        <el-menu-item-group v-if="authStore.user?.role !== 'SUPER_ADMIN'" title="简介资料">
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
        <el-menu-item-group v-if="authStore.user?.role !== 'SUPER_ADMIN'" title="学术成果">
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
        <el-menu-item-group v-if="authStore.user?.role !== 'SUPER_ADMIN'" title="工具">
          <el-menu-item index="/dashboard/export">
            <el-icon><Download /></el-icon>
            <span>导出</span>
          </el-menu-item>
          <el-menu-item index="/dashboard/ai-generation">
            <el-icon><DataAnalysis /></el-icon>
            <span>AI 生成</span>
          </el-menu-item>
          <el-menu-item index="/dashboard/ai-settings">
            <el-icon><Setting /></el-icon>
            <span>AI 模型设置</span>
          </el-menu-item>
          <el-menu-item v-if="authStore.user?.role !== 'SUPER_ADMIN'" index="/dashboard/access">
            <el-icon><Files /></el-icon>
            <span>资料访问与授权</span>
          </el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group v-if="authStore.user?.role === 'SUPER_ADMIN'" title="系统管理">
          <el-menu-item index="/dashboard/admin">
            <el-icon><Monitor /></el-icon>
            <span>系统管理</span>
          </el-menu-item>
          <el-menu-item index="/dashboard/admin/organization">
            <el-icon><OfficeBuilding /></el-icon>
            <span>专业与业务角色</span>
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>
      </div>
    </el-aside>

    <el-container class="main-shell">
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
  width: 100%;
  height: 100vh;
  height: 100dvh;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  background: #ffffff;
  border-right: 1px solid #e5e7eb;
  transition: width 0.2s ease;
}

.sidebar-header {
  height: 64px;
  flex: 0 0 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 10px 0 16px;
}

.brand {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 700;
  white-space: nowrap;
}

.brand-mark {
  flex: 0 0 auto;
}

.collapse-button {
  flex: 0 0 auto;
}

.sidebar.is-collapsed .sidebar-header {
  padding: 0 4px 0 8px;
}

.sidebar-menu {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-gutter: stable;
}

.sidebar-menu :deep(.el-menu) {
  border-right: 0;
}

.sidebar.is-collapsed :deep(.el-menu-item-group__title) {
  display: none;
}

.main-shell {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.topbar {
  flex: 0 0 60px;
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
  min-width: 0;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  background: #f5f7fb;
}

@media (max-width: 720px) {
  .topbar {
    padding: 0 12px;
  }

  .current-user {
    min-width: 0;
  }

  .current-user strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .current-user span {
    display: none;
  }

  .content {
    padding: 12px;
  }
}
</style>
