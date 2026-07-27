<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  deleteAiSettings,
  getAiSettings,
  saveAiSettings,
  testAiSettings
} from '../../services/aiSettings'

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const deleting = ref(false)
const configured = ref(false)
const maskedApiKey = ref('')
const settingsSaved = ref(false)

const form = reactive({
  providerName: '',
  baseUrl: '',
  modelName: '',
  apiKey: ''
})

async function loadSettings() {
  loading.value = true
  try {
    const settings = await getAiSettings()
    configured.value = settings.configured
    form.providerName = settings.providerName || ''
    form.baseUrl = settings.baseUrl || ''
    form.modelName = settings.modelName || ''
    form.apiKey = ''
    maskedApiKey.value = settings.maskedApiKey || ''
    settingsSaved.value = settings.configured
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载 AI 模型设置失败')
  } finally {
    loading.value = false
  }
}

function markUnsaved() {
  settingsSaved.value = false
}

async function saveSettings() {
  if (!form.providerName.trim() || !form.baseUrl.trim() || !form.modelName.trim()) {
    ElMessage.warning('请完整填写服务商名称、API Base URL 和模型名称')
    return
  }
  if (!configured.value && !form.apiKey.trim()) {
    ElMessage.warning('首次保存必须填写 API Key')
    return
  }
  saving.value = true
  try {
    const settings = await saveAiSettings({
      providerName: form.providerName,
      baseUrl: form.baseUrl,
      modelName: form.modelName,
      apiKey: form.apiKey
    })
    configured.value = settings.configured
    form.providerName = settings.providerName || ''
    form.baseUrl = settings.baseUrl || ''
    form.modelName = settings.modelName || ''
    form.apiKey = ''
    maskedApiKey.value = settings.maskedApiKey || ''
    settingsSaved.value = true
    ElMessage.success('AI 模型设置已保存')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存 AI 模型设置失败')
  } finally {
    saving.value = false
  }
}

async function testConnection() {
  if (!configured.value || !settingsSaved.value) {
    ElMessage.warning('请先保存当前配置，再测试连接')
    return
  }
  testing.value = true
  try {
    const result = await testAiSettings()
    if (result.success) {
      ElMessage.success(result.message)
    } else {
      ElMessage.error(result.message)
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '连接测试失败')
  } finally {
    testing.value = false
  }
}

async function removeSettings() {
  try {
    await ElMessageBox.confirm(
      '确认删除当前 AI 模型设置？历史 AI 任务仍会保留，但删除后不能创建新的 AI 任务。',
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
    deleting.value = true
    await deleteAiSettings()
    configured.value = false
    maskedApiKey.value = ''
    settingsSaved.value = false
    Object.assign(form, {
      providerName: '',
      baseUrl: '',
      modelName: '',
      apiKey: ''
    })
    ElMessage.success('AI 模型设置已删除')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '删除 AI 模型设置失败')
    }
  } finally {
    deleting.value = false
  }
}

onMounted(loadSettings)
</script>

<template>
  <section class="page-panel" v-loading="loading">
    <header class="page-header">
      <div>
        <h2>AI 模型设置</h2>
        <p>配置当前教师本人使用的 OpenAI-compatible 第三方模型。</p>
      </div>
      <el-tag :type="configured ? 'success' : 'info'">
        {{ configured ? '已配置' : '未配置' }}
      </el-tag>
    </header>

    <el-alert
      class="security-alert"
      type="info"
      show-icon
      :closable="false"
      title="API Key 只会加密保存在服务器端，保存或刷新后不会回显。请只使用可信的 HTTPS 服务地址。"
    />

    <el-form label-position="top" class="settings-form">
      <el-form-item label="服务商名称" required>
        <el-input v-model="form.providerName" maxlength="128" placeholder="例如：组织内部模型服务" @input="markUnsaved" />
      </el-form-item>

      <el-form-item label="API Base URL" required>
        <el-input v-model="form.baseUrl" maxlength="2048" placeholder="https://api.example.com/v1" @input="markUnsaved" />
        <div class="field-help">仅支持 HTTPS；系统会安全地追加 /chat/completions。</div>
      </el-form-item>

      <el-form-item label="模型名称" required>
        <el-input v-model="form.modelName" maxlength="255" placeholder="填写供应商账号中实际可用的模型名称" @input="markUnsaved" />
      </el-form-item>

      <el-form-item label="API Key" :required="!configured">
        <el-input
          v-model="form.apiKey"
          type="password"
          show-password
          maxlength="4096"
          autocomplete="new-password"
          placeholder="输入 API Key"
          @input="markUnsaved"
        />
        <div v-if="configured" class="field-help">
          当前密钥：{{ maskedApiKey }}。留空表示保留现有密钥。
        </div>
        <div v-else class="field-help">首次保存必须填写 API Key。</div>
      </el-form-item>

      <el-alert
        class="cost-alert"
        type="warning"
        show-icon
        :closable="false"
        title="连接测试会向已保存的模型发送一个极短请求，可能产生极少量调用费用。"
      />

      <div class="actions">
        <el-button type="primary" :loading="saving" @click="saveSettings">保存</el-button>
        <el-button :loading="testing" :disabled="!configured || !settingsSaved" @click="testConnection">
          测试连接
        </el-button>
        <el-button type="danger" plain :loading="deleting" :disabled="!configured" @click="removeSettings">
          删除配置
        </el-button>
      </div>
    </el-form>
  </section>
</template>

<style scoped>
.page-panel {
  padding: 28px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

h2 {
  margin: 0 0 8px;
}

p {
  margin: 0;
  color: #6b7280;
}

.security-alert {
  margin: 20px 0;
}

.settings-form {
  max-width: 760px;
}

.field-help {
  margin-top: 6px;
  color: #6b7280;
  font-size: 13px;
}

.cost-alert {
  margin-bottom: 20px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
</style>
