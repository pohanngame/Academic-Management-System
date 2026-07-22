<script setup>
import { Monitor } from '@element-plus/icons-vue'
import { useAppStore } from '../stores/app'

const appStore = useAppStore()
</script>

<template>
  <main class="home-page">
    <section class="shell">
      <div class="title-row">
        <el-icon class="title-icon" :size="28">
          <Monitor />
        </el-icon>
        <div>
          <h1>{{ appStore.appName }}</h1>
          <p>当前首页是公开入口占位，公开教师简介页将在后续阶段接入。</p>
        </div>
      </div>
      <el-alert
        title="后台已支持教师资料和简介基础分块管理"
        type="info"
        :closable="false"
        show-icon
      />
      <div class="actions">
        <el-button type="primary" @click="$router.push('/login')">登录</el-button>
        <el-button @click="$router.push('/register')">注册</el-button>
      </div>
    </section>
  </main>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  padding: 48px 20px;
}

.shell {
  max-width: 960px;
  margin: 0 auto;
  padding: 32px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.title-row {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 24px;
}

.title-icon {
  flex: 0 0 auto;
  margin-top: 4px;
  color: #2563eb;
}

h1 {
  margin: 0 0 10px;
  font-size: 28px;
  font-weight: 700;
}

p {
  margin: 0;
  color: #6b7280;
  line-height: 1.7;
}

.actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}
</style>
