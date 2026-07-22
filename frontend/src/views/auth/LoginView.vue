<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const form = ref({
  usernameOrEmail: '',
  password: ''
})

async function handleSubmit() {
  loading.value = true
  try {
    await authStore.login(form.value)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/dashboard')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-panel">
      <h1>登录</h1>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="用户名或邮箱">
          <el-input v-model="form.usernameOrEmail" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" autocomplete="current-password" show-password />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="submit-button" @click="handleSubmit">
          登录
        </el-button>
      </el-form>
      <p class="switch">
        还没有账号？
        <router-link to="/register">去注册</router-link>
      </p>
    </section>
  </main>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
}

.auth-panel {
  width: min(420px, 100%);
  padding: 32px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

h1 {
  margin: 0 0 24px;
  font-size: 26px;
}

.submit-button {
  width: 100%;
}

.switch {
  margin: 18px 0 0;
  color: #6b7280;
}
</style>
