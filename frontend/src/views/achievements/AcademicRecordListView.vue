<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { createRecord, deleteRecord, listRecord, updateRecord } from '../../services/academicRecords'
import {
  allowedFileAccept,
  deleteFile,
  downloadFile,
  listFiles,
  uploadFile
} from '../../services/files'

const route = useRoute()
const loading = ref(false)
const saving = ref(false)
const drawerVisible = ref(false)
const editingId = ref(null)
const records = ref([])
const attachments = ref([])
const attachmentLoading = ref(false)
const attachmentUploading = ref(false)

const configs = {
  projects: {
    title: '科研项目',
    description: '维护科研项目名称、来源、角色、周期和经费等信息。',
    fields: [
      { key: 'projectName', label: '项目名称', required: true },
      { key: 'source', label: '项目来源' },
      { key: 'role', label: '承担角色' },
      { key: 'startDate', label: '开始日期', type: 'date' },
      { key: 'endDate', label: '结束日期', type: 'date' },
      { key: 'fundingAmount', label: '经费金额', type: 'number', precision: 2, step: 1000 },
      { key: 'status', label: '项目状态' },
      { key: 'description', label: '项目说明', type: 'textarea' }
    ],
    columns: ['projectName', 'source', 'role', 'status']
  },
  teachingCourses: {
    title: '授课记录',
    description: '维护课程、学期、班级、授课对象和学时等信息。',
    fields: [
      { key: 'courseName', label: '课程名称', required: true },
      { key: 'semester', label: '学期' },
      { key: 'className', label: '班级' },
      { key: 'teachingTarget', label: '授课对象' },
      { key: 'hours', label: '学时', type: 'number', precision: 2, step: 1 },
      { key: 'description', label: '说明', type: 'textarea' }
    ],
    columns: ['courseName', 'semester', 'className', 'hours']
  },
  papers: {
    title: '论文/学术成果',
    description: '维护论文、会议论文、书籍章节等学术成果基础信息。',
    fields: [
      { key: 'title', label: '成果标题', required: true },
      { key: 'authors', label: '作者', type: 'textarea' },
      { key: 'publicationName', label: '期刊/会议/出版物名称' },
      { key: 'publicationType', label: '成果类型' },
      { key: 'publishYear', label: '发表年份', type: 'number', precision: 0, step: 1 },
      { key: 'doi', label: 'DOI' },
      { key: 'volume', label: '卷' },
      { key: 'issue', label: '期' },
      { key: 'pages', label: '页码' },
      { key: 'publisher', label: '出版社' },
      { key: 'url', label: 'URL' },
      { key: 'abstractText', label: '摘要', type: 'textarea', rows: 5 },
      { key: 'keywords', label: '关键词' }
    ],
    columns: ['title', 'authors', 'publicationName', 'publishYear']
  },
  patents: {
    title: '专利',
    description: '维护专利名称、专利号、类型、状态和发明人等信息。',
    fields: [
      { key: 'patentName', label: '专利名称', required: true },
      { key: 'patentNumber', label: '专利号' },
      { key: 'patentType', label: '专利类型' },
      { key: 'status', label: '状态' },
      { key: 'applicationDate', label: '申请日期', type: 'date' },
      { key: 'authorizationDate', label: '授权日期', type: 'date' },
      { key: 'inventors', label: '发明人', type: 'textarea' },
      { key: 'description', label: '说明', type: 'textarea' }
    ],
    columns: ['patentName', 'patentNumber', 'patentType', 'status']
  },
  certificates: {
    title: '证书',
    description: '维护证书名称、类型、颁发机构和颁发日期等信息。',
    fields: [
      { key: 'certificateName', label: '证书名称', required: true },
      { key: 'certificateType', label: '证书类型' },
      { key: 'issuingAuthority', label: '颁发机构' },
      { key: 'issueDate', label: '颁发日期', type: 'date' },
      { key: 'description', label: '说明', type: 'textarea' }
    ],
    columns: ['certificateName', 'certificateType', 'issuingAuthority', 'issueDate']
  }
}

const recordType = computed(() => route.meta.recordType)
const config = computed(() => configs[recordType.value])
const attachmentBusinessTypes = {
  papers: 'PAPER',
  patents: 'PATENT',
  certificates: 'CERTIFICATE'
}
const supportsAttachments = computed(() => Boolean(attachmentBusinessTypes[recordType.value]))

const form = reactive({})

function resetForm(record = null) {
  Object.keys(form).forEach((key) => delete form[key])
  for (const field of config.value.fields) {
    form[field.key] = record?.[field.key] ?? (field.type === 'number' ? null : '')
  }
  form.sortOrder = record?.sortOrder ?? 0
  form.isPublic = record?.isPublic ?? true
  editingId.value = record?.id ?? null
}

function buildPayload() {
  const payload = {}
  for (const field of config.value.fields) {
    const value = form[field.key]
    payload[field.key] = value === '' ? null : value
  }
  payload.sortOrder = form.sortOrder ?? 0
  payload.isPublic = form.isPublic ?? true
  return payload
}

async function loadRecords() {
  loading.value = true
  try {
    records.value = await listRecord(recordType.value)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || `加载${config.value.title}失败`)
  } finally {
    loading.value = false
  }
}

function openCreateDrawer() {
  resetForm()
  attachments.value = []
  drawerVisible.value = true
}

function openEditDrawer(record) {
  resetForm(record)
  loadAttachments(record.id)
  drawerVisible.value = true
}

async function saveRecord() {
  saving.value = true
  try {
    const payload = buildPayload()
    if (editingId.value) {
      await updateRecord(recordType.value, editingId.value, payload)
    } else {
      await createRecord(recordType.value, payload)
    }
    ElMessage.success('保存成功')
    drawerVisible.value = false
    await loadRecords()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeRecord(record) {
  try {
    await ElMessageBox.confirm('确认删除这条记录？', '删除确认', { type: 'warning' })
    await deleteRecord(recordType.value, record.id)
    ElMessage.success('删除成功')
    await loadRecords()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '删除失败')
    }
  }
}

async function loadAttachments(recordId = editingId.value) {
  if (!supportsAttachments.value || !recordId) {
    attachments.value = []
    return
  }
  attachmentLoading.value = true
  try {
    attachments.value = await listFiles(attachmentBusinessTypes[recordType.value], recordId)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载附件失败')
  } finally {
    attachmentLoading.value = false
  }
}

async function uploadAttachmentFile(options) {
  if (!editingId.value) {
    ElMessage.warning('请先保存记录，再上传附件')
    return
  }
  attachmentUploading.value = true
  try {
    const uploaded = await uploadFile(
      options.file,
      attachmentBusinessTypes[recordType.value],
      editingId.value
    )
    options.onSuccess?.(uploaded)
    ElMessage.success('附件已上传')
    await loadAttachments()
  } catch (error) {
    options.onError?.(error)
    ElMessage.error(error.response?.data?.message || '附件上传失败')
  } finally {
    attachmentUploading.value = false
  }
}

async function downloadAttachment(file) {
  try {
    await downloadFile(file)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '附件下载失败')
  }
}

async function removeAttachment(file) {
  try {
    await ElMessageBox.confirm('确认删除这个附件？', '删除确认', { type: 'warning' })
    await deleteFile(file.id)
    ElMessage.success('附件已删除')
    await loadAttachments()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '附件删除失败')
    }
  }
}

function fieldLabel(key) {
  return config.value.fields.find((field) => field.key === key)?.label || key
}

watch(recordType, () => {
  resetForm()
  attachments.value = []
  loadRecords()
})

onMounted(() => {
  resetForm()
  loadRecords()
})
</script>

<template>
  <section class="page-panel">
    <header class="page-header">
      <div>
        <h2>{{ config.title }}</h2>
        <p>{{ config.description }}</p>
      </div>
      <el-button type="primary" @click="openCreateDrawer">新增</el-button>
    </header>

    <el-table class="achievement-table" v-loading="loading" :data="records" border>
      <el-table-column prop="sortOrder" label="排序" width="90" />
      <el-table-column
        v-for="column in config.columns"
        :key="column"
        :prop="column"
        :label="fieldLabel(column)"
        min-width="160"
      />
      <el-table-column label="公开" width="90">
        <template #default="{ row }">
          <el-tag :type="row.isPublic ? 'success' : 'info'">
            {{ row.isPublic ? '公开' : '隐藏' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEditDrawer(row)">编辑</el-button>
          <el-button link type="danger" @click="removeRecord(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer
      v-model="drawerVisible"
      :title="editingId ? `编辑${config.title}` : `新增${config.title}`"
      size="680px"
    >
      <el-form label-position="top">
        <el-form-item
          v-for="field in config.fields"
          :key="field.key"
          :label="field.label"
          :required="field.required"
        >
          <el-date-picker
            v-if="field.type === 'date'"
            v-model="form[field.key]"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
          />
          <el-input-number
            v-else-if="field.type === 'number'"
            v-model="form[field.key]"
            :min="0"
            :precision="field.precision ?? 0"
            :step="field.step ?? 1"
            controls-position="right"
          />
          <el-input
            v-else-if="field.type === 'textarea'"
            v-model="form[field.key]"
            type="textarea"
            :rows="field.rows ?? 4"
          />
          <el-input v-else v-model="form[field.key]" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="排序">
            <el-input-number v-model="form.sortOrder" :min="0" controls-position="right" />
          </el-form-item>
          <el-form-item label="公开状态">
            <el-switch v-model="form.isPublic" active-text="公开" inactive-text="隐藏" />
          </el-form-item>
        </div>
      </el-form>

      <section v-if="supportsAttachments" class="attachment-panel">
        <el-divider />
        <div class="attachment-header">
          <div>
            <h3>附件</h3>
            <p>支持 PDF、JPG、PNG、WEBP。附件仅后台本人可访问。</p>
          </div>
          <el-upload
            v-if="editingId"
            :show-file-list="false"
            :http-request="uploadAttachmentFile"
            :accept="allowedFileAccept"
          >
            <el-button :loading="attachmentUploading">上传附件</el-button>
          </el-upload>
        </div>
        <el-alert
          v-if="!editingId"
          title="保存记录后可以上传附件。"
          type="info"
          show-icon
          :closable="false"
        />
        <el-table v-else v-loading="attachmentLoading" :data="attachments" border>
          <el-table-column prop="originalName" label="文件名" min-width="220" show-overflow-tooltip />
          <el-table-column prop="fileExt" label="类型" width="90" />
          <el-table-column prop="fileSize" label="大小" width="120">
            <template #default="{ row }">
              {{ Math.ceil(row.fileSize / 1024) }} KB
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="downloadAttachment(row)">下载</el-button>
              <el-button link type="danger" @click="removeAttachment(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRecord">保存</el-button>
      </template>
    </el-drawer>
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

.achievement-table :deep(.cell) {
  white-space: normal;
  overflow: visible;
  text-overflow: clip;
  overflow-wrap: anywhere;
  word-break: break-word;
  line-height: 1.5;
}

.achievement-table :deep(.el-table__cell) {
  vertical-align: top;
}

h2 {
  margin: 0 0 8px;
  font-size: 22px;
}

p {
  margin: 0;
  color: #6b7280;
}

.form-row {
  display: flex;
  gap: 24px;
  align-items: center;
}

.attachment-panel {
  margin-top: 10px;
}

.attachment-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 14px;
}

.attachment-header h3 {
  margin: 0 0 6px;
  font-size: 16px;
}

@media (max-width: 720px) {
  .page-header,
  .form-row,
  .attachment-header {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
