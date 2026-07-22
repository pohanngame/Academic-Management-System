<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { allowedImageAccept, createFileObjectUrl, uploadAvatar } from '../../services/files'
import { getTeacherProfile, updateTeacherProfile } from '../../services/teacherProfile'

const visibilityOptions = [
  { key: 'avatarFileId', label: '显示头像' },
  { key: 'title', label: '显示职称' },
  { key: 'department', label: '显示部门' },
  { key: 'phone', label: '显示电话' },
  { key: 'office', label: '显示办公室' },
  { key: 'profileEmail', label: '显示邮箱' },
  { key: 'biography', label: '显示个人简介' }
]

const defaultVisibilityConfig = visibilityOptions.reduce((config, option) => {
  config[option.key] = true
  return config
}, {})

const loading = ref(false)
const saving = ref(false)
const avatarUploading = ref(false)
const avatarPreviewUrl = ref('')
const avatarPreviewLoading = ref(false)
const avatarPreviewError = ref('')
const fieldVisibility = ref({ ...defaultVisibilityConfig })
const form = ref({
  avatarFileId: null,
  displayName: '',
  title: '',
  department: '',
  phone: '',
  office: '',
  profileEmail: '',
  biography: '',
  publicEnabled: false,
  publicSlug: '',
  fieldVisibilityConfig: ''
})

const publicProfileLink = computed(() => {
  const slug = form.value.publicSlug?.trim()
  if (!form.value.publicEnabled || !slug) {
    return ''
  }
  return `/profiles/${encodeURIComponent(slug)}`
})

let avatarPreviewRequestId = 0

async function loadProfile() {
  loading.value = true
  try {
    form.value = {
      ...form.value,
      ...(await getTeacherProfile())
    }
    applyVisibilityConfig(form.value.fieldVisibilityConfig)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载教师资料失败')
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  saving.value = true
  try {
    const payload = {
      ...form.value,
      fieldVisibilityConfig: JSON.stringify(fieldVisibility.value)
    }
    form.value = await updateTeacherProfile(payload)
    applyVisibilityConfig(form.value.fieldVisibilityConfig)
    if (publicProfileLink.value) {
      ElMessage.success('教师资料已保存，公开主页地址已更新')
    } else {
      ElMessage.success('教师资料已保存')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存教师资料失败')
  } finally {
    saving.value = false
  }
}

async function uploadAvatarFile(options) {
  avatarUploading.value = true
  try {
    const uploaded = await uploadAvatar(options.file)
    form.value.avatarFileId = uploaded.id
    await loadProfile()
    options.onSuccess?.(uploaded)
    ElMessage.success('头像已上传')
  } catch (error) {
    options.onError?.(error)
    ElMessage.error(error.response?.data?.message || '头像上传失败')
  } finally {
    avatarUploading.value = false
  }
}

function applyVisibilityConfig(rawConfig) {
  const nextConfig = { ...defaultVisibilityConfig }
  if (rawConfig) {
    try {
      const parsed = typeof rawConfig === 'string' ? JSON.parse(rawConfig) : rawConfig
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        visibilityOptions.forEach((option) => {
          if (typeof parsed[option.key] === 'boolean') {
            nextConfig[option.key] = parsed[option.key]
          }
        })
      }
    } catch {
      // Invalid legacy config falls back to showing all supported public fields.
    }
  }
  fieldVisibility.value = nextConfig
}

function revokeAvatarPreviewUrl() {
  if (avatarPreviewUrl.value) {
    window.URL.revokeObjectURL(avatarPreviewUrl.value)
    avatarPreviewUrl.value = ''
  }
}

async function loadAvatarPreview(fileId) {
  const requestId = ++avatarPreviewRequestId
  revokeAvatarPreviewUrl()
  avatarPreviewError.value = ''
  if (!fileId) {
    avatarPreviewLoading.value = false
    return
  }

  avatarPreviewLoading.value = true
  try {
    const url = await createFileObjectUrl(fileId, 'image/jpeg')
    if (requestId !== avatarPreviewRequestId) {
      window.URL.revokeObjectURL(url)
      return
    }
    avatarPreviewUrl.value = url
  } catch (error) {
    if (requestId === avatarPreviewRequestId) {
      avatarPreviewError.value = error.response?.data?.message || '头像预览加载失败'
    }
  } finally {
    if (requestId === avatarPreviewRequestId) {
      avatarPreviewLoading.value = false
    }
  }
}

function handleAvatarPreviewError() {
  avatarPreviewError.value = '头像图片加载失败，请重新上传或检查文件格式'
  revokeAvatarPreviewUrl()
}

watch(
  () => form.value.avatarFileId,
  (fileId) => {
    loadAvatarPreview(fileId)
  }
)

onMounted(loadProfile)

onBeforeUnmount(() => {
  avatarPreviewRequestId += 1
  revokeAvatarPreviewUrl()
})
</script>

<template>
  <section class="page-panel" v-loading="loading">
    <header class="page-header">
      <div>
        <h2>教师资料</h2>
        <p>维护个人简介页会使用的基础资料。公开展示开关和字段开关会影响公开主页。</p>
      </div>
      <div class="header-actions">
        <el-button
          v-if="publicProfileLink"
          tag="a"
          :href="publicProfileLink"
          target="_blank"
          rel="noopener noreferrer"
        >
          查看公开主页
        </el-button>
        <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
      </div>
    </header>

    <el-form class="profile-form" label-position="top">
      <div class="form-grid">
        <el-form-item label="姓名">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item label="职称">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="form.department" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="办公室">
          <el-input v-model="form.office" />
        </el-form-item>
        <el-form-item label="展示邮箱">
          <el-input v-model="form.profileEmail" />
        </el-form-item>
        <el-form-item label="头像">
          <div class="avatar-upload">
            <div class="avatar-preview" :class="{ 'is-empty': !avatarPreviewUrl }">
              <img
                v-if="avatarPreviewUrl"
                :src="avatarPreviewUrl"
                alt="教师头像预览"
                @error="handleAvatarPreviewError"
              />
              <span v-else-if="avatarPreviewLoading">加载中</span>
              <span v-else>暂无头像</span>
            </div>
            <el-upload
              :show-file-list="false"
              :http-request="uploadAvatarFile"
              :accept="allowedImageAccept"
            >
              <el-button :loading="avatarUploading">上传头像</el-button>
            </el-upload>
            <div class="avatar-meta">
              <span v-if="form.avatarFileId">当前头像文件 ID：{{ form.avatarFileId }}</span>
              <span v-else>尚未上传头像</span>
              <span v-if="avatarPreviewError" class="error-text">{{ avatarPreviewError }}</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="公开路径 slug">
          <el-input v-model="form.publicSlug" placeholder="例如 zhang-san" />
        </el-form-item>
      </div>

      <el-form-item label="个人简介">
        <el-input v-model="form.biography" type="textarea" :rows="6" />
      </el-form-item>

      <el-form-item label="公开字段">
        <div class="visibility-config">
          <p class="form-help">控制公开主页基础信息中哪些字段对外展示。</p>
          <div class="visibility-grid">
            <el-checkbox
              v-for="option in visibilityOptions"
              :key="option.key"
              v-model="fieldVisibility[option.key]"
            >
              {{ option.label }}
            </el-checkbox>
          </div>
        </div>
      </el-form-item>

      <el-form-item>
        <div class="public-setting">
          <el-checkbox v-model="form.publicEnabled">允许公开展示</el-checkbox>
          <span v-if="publicProfileLink">
            公开主页：
            <a :href="publicProfileLink" target="_blank" rel="noopener noreferrer">
              {{ publicProfileLink }}
            </a>
          </span>
          <span v-else>开启公开展示并填写 slug 后，会显示公开主页链接。</span>
        </div>
      </el-form-item>
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
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
}

.header-actions {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  flex-wrap: wrap;
}

h2 {
  margin: 0 0 8px;
  font-size: 22px;
}

p {
  margin: 0;
  color: #6b7280;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 18px;
}

.avatar-upload {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.avatar-preview {
  width: 86px;
  height: 86px;
  overflow: hidden;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #f3f4f6;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  font-size: 13px;
  flex: 0 0 auto;
}

.avatar-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-preview.is-empty {
  border-style: dashed;
}

.avatar-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.avatar-meta span {
  color: #6b7280;
  font-size: 13px;
}

.avatar-meta .error-text {
  color: #dc2626;
}

.visibility-config {
  width: 100%;
}

.visibility-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 18px;
}

.form-help {
  margin-bottom: 10px;
  font-size: 13px;
}

.public-setting {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  color: #6b7280;
  font-size: 13px;
}

.public-setting a {
  color: #2563eb;
  text-decoration: none;
}

@media (max-width: 720px) {
  .page-header {
    flex-direction: column;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .visibility-grid {
    grid-template-columns: 1fr;
  }
}
</style>
