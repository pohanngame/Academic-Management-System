<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { useAuthStore } from '../../stores/auth'
import {
  normalizeRegistrationPayload,
  registrationFieldError,
  shouldSubmitRegistration
} from './registerRules'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const formRef = ref()
const form = ref({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  displayName: '',
  department: '',
  title: ''
})

function fieldValidator(field) {
  return (_rule, value, callback) => {
    const message = registrationFieldError(field, value, form.value)
    message ? callback(new Error(message)) : callback()
  }
}

const rules = {
  username: [{ validator: fieldValidator('username'), trigger: 'blur' }],
  email: [{ validator: fieldValidator('email'), trigger: 'blur' }],
  displayName: [{ validator: fieldValidator('displayName'), trigger: 'blur' }],
  department: [{ validator: fieldValidator('department'), trigger: 'blur' }],
  title: [{ validator: fieldValidator('title'), trigger: 'blur' }],
  password: [{ validator: fieldValidator('password'), trigger: 'blur' }],
  confirmPassword: [{ validator: fieldValidator('confirmPassword'), trigger: 'blur' }]
}

async function handleSubmit() {
  if (!shouldSubmitRegistration({ valid: true, loading: loading.value })) return
  loading.value = true
  try {
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid) return
    await authStore.register(normalizeRegistrationPayload(form.value))
    ElMessage.success('注册成功')
    router.push('/dashboard')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-panel">
      <h1>注册教师账号</h1>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleSubmit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" autocomplete="username" placeholder="请输入登录用户名" />
          <div class="field-hint">3–64 个字符，用于登录，注册后请谨慎修改。</div>
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" autocomplete="email" placeholder="name@example.com" />
          <div class="field-hint">请输入可正常使用的邮箱，也可以用于登录。</div>
        </el-form-item>
        <el-form-item label="姓名" prop="displayName">
          <el-input v-model="form.displayName" placeholder="请输入真实姓名" />
          <div class="field-hint">必填，请输入真实姓名，最多 128 个字符。</div>
        </el-form-item>
        <el-form-item label="部门（选填）" prop="department">
          <el-input v-model="form.department" placeholder="例如：计算机学院、科研管理部" />
          <div class="field-hint">选填，最多 128 个字符。</div>
        </el-form-item>
        <el-form-item label="职称（选填）" prop="title">
          <el-input v-model="form.title" placeholder="例如：讲师、副教授、教授" />
          <div class="field-hint">选填，最多 128 个字符。</div>
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" autocomplete="new-password" show-password />
          <div class="field-hint">8–64 个字符，请勿使用过于简单或与其他网站相同的密码。</div>
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" autocomplete="new-password" show-password />
          <div class="field-hint">请再次输入相同密码。</div>
        </el-form-item>
        <el-button native-type="submit" type="primary" :loading="loading" :disabled="loading" class="submit-button">
          注册
        </el-button>
      </el-form>
      <p class="switch">
        已有账号？
        <router-link to="/login">去登录</router-link>
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
  width: min(520px, 100%);
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

.field-hint {
  margin-top: 4px;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.5;
}

.switch {
  margin: 18px 0 0;
  color: #6b7280;
}
</style>
