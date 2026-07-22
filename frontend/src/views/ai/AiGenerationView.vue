<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  confirmAiTask,
  createAiTask,
  deleteAiTask,
  downloadAiPdf,
  downloadAiWord,
  getAiModules,
  getAiTask,
  listAiTasks,
  updateAiResult
} from '../../services/aiGeneration'
import {
  allowedWordTemplateAccept,
  deleteFile,
  downloadFile,
  listFiles,
  uploadFile
} from '../../services/files'

const loading = ref(false)
const generating = ref(false)
const savingDraft = ref(false)
const confirming = ref(false)
const pdfExporting = ref(false)
const templateUploading = ref(false)
const modules = ref([])
const tasks = ref([])
const wordTemplates = ref([])
const currentTask = ref(null)
const editedContent = ref('')

const form = reactive({
  templateFileId: null,
  templateRequirement: '',
  selectedModules: ['teacherProfile', 'papers', 'projects']
})

const currentResult = computed(() => currentTask.value?.result || null)
const canEdit = computed(() => currentResult.value?.status === 'DRAFT')
const canExport = computed(() => currentResult.value?.status === 'CONFIRMED')
const requirementLabel = computed(() => form.templateFileId ? '补充生成要求（可选）' : '文字生成要求')

async function loadPage() {
  loading.value = true
  try {
    const [moduleList, taskList, templateList] = await Promise.all([
      getAiModules(),
      listAiTasks(),
      listFiles('AI_WORD_TEMPLATE')
    ])
    modules.value = moduleList
    tasks.value = taskList
    wordTemplates.value = templateList
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载 AI 生成页面失败')
  } finally {
    loading.value = false
  }
}

async function refreshTasks() {
  tasks.value = await listAiTasks()
}

async function submitTask() {
  if (!form.templateFileId && !form.templateRequirement.trim()) {
    ElMessage.warning('未选择 Word 模板时，请输入文字生成要求')
    return
  }
  if (!form.selectedModules.length) {
    ElMessage.warning('请至少选择一个数据范围')
    return
  }
  generating.value = true
  try {
    const task = await createAiTask({
      templateFileId: form.templateFileId,
      templateRequirement: form.templateRequirement,
      selectedModules: form.selectedModules
    })
    await refreshTasks()
    await loadTask(task.id)
    if (task.status === 'FAILED') {
      ElMessage.warning(task.errorMessage || 'AI 生成失败')
    } else {
      ElMessage.success('AI 草稿已生成，请先预览并确认')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '创建 AI 任务失败')
  } finally {
    generating.value = false
  }
}

async function uploadWordTemplate(options) {
  templateUploading.value = true
  try {
    const uploaded = await uploadFile(options.file, 'AI_WORD_TEMPLATE')
    options.onSuccess?.(uploaded)
    wordTemplates.value = await listFiles('AI_WORD_TEMPLATE')
    form.templateFileId = uploaded.id
    ElMessage.success('Word 模板已上传并选中')
  } catch (error) {
    options.onError?.(error)
    ElMessage.error(error.response?.data?.message || 'Word 模板上传失败')
  } finally {
    templateUploading.value = false
  }
}

async function downloadWordTemplate(file) {
  try {
    await downloadFile(file)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || 'Word 模板下载失败')
  }
}

async function removeWordTemplate(file) {
  try {
    await ElMessageBox.confirm(
      `确认删除 Word 模板“${file.originalName}”？历史任务仍可在后台使用该模板导出。`,
      '删除确认',
      { type: 'warning' }
    )
    await deleteFile(file.id)
    if (form.templateFileId === file.id) {
      form.templateFileId = null
    }
    wordTemplates.value = await listFiles('AI_WORD_TEMPLATE')
    ElMessage.success('Word 模板已删除')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || 'Word 模板删除失败')
    }
  }
}

function formatFileSize(size) {
  return `${Math.max(1, Math.ceil(size / 1024))} KB`
}

function formatCreatedAt(value) {
  return value ? new Date(value).toLocaleString('zh-CN') : ''
}

async function loadTask(id) {
  loading.value = true
  try {
    currentTask.value = await getAiTask(id)
    editedContent.value = currentTask.value.result?.editedContent || currentTask.value.result?.draftContent || ''
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载 AI 历史失败')
  } finally {
    loading.value = false
  }
}

async function saveDraft() {
  if (!currentResult.value) {
    return
  }
  savingDraft.value = true
  try {
    await updateAiResult(currentResult.value.id, editedContent.value)
    await loadTask(currentTask.value.id)
    await refreshTasks()
    ElMessage.success('草稿已保存')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存草稿失败')
  } finally {
    savingDraft.value = false
  }
}

async function confirmDraft() {
  if (!currentTask.value) {
    return
  }
  confirming.value = true
  try {
    if (canEdit.value) {
      await updateAiResult(currentResult.value.id, editedContent.value)
    }
    await confirmAiTask(currentTask.value.id)
    await loadTask(currentTask.value.id)
    await refreshTasks()
    ElMessage.success('草稿已确认，可以导出 Word 或 PDF')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '确认草稿失败')
  } finally {
    confirming.value = false
  }
}

async function exportWord() {
  try {
    await downloadAiWord(currentTask.value)
    ElMessage.success('Word 已导出')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '导出 Word 失败')
  }
}

async function exportPdf() {
  pdfExporting.value = true
  try {
    await downloadAiPdf(currentTask.value)
    ElMessage.success('PDF 已导出')
  } catch (error) {
    ElMessage.error(await pdfErrorMessage(error))
  } finally {
    pdfExporting.value = false
  }
}

async function pdfErrorMessage(error) {
  const data = error.response?.data
  if (data instanceof Blob) {
    try {
      const payload = JSON.parse(await data.text())
      return payload.message || '导出 PDF 失败'
    } catch {
      return '导出 PDF 失败'
    }
  }
  return data?.message || '导出 PDF 失败'
}

async function removeTask(task) {
  try {
    await ElMessageBox.confirm('确认删除这条 AI 生成历史？删除后页面不再显示。', '删除确认', { type: 'warning' })
    await deleteAiTask(task.id)
    if (currentTask.value?.id === task.id) {
      currentTask.value = null
      editedContent.value = ''
    }
    await refreshTasks()
    ElMessage.success('AI 生成历史已删除')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '删除 AI 历史失败')
    }
  }
}

function statusType(status) {
  if (status === 'SUCCEEDED' || status === 'CONFIRMED') {
    return 'success'
  }
  if (status === 'FAILED') {
    return 'danger'
  }
  if (status === 'RUNNING') {
    return 'warning'
  }
  return 'info'
}

function moduleLabels(keys) {
  const map = new Map(modules.value.map((item) => [item.key, item.label]))
  return (keys || []).map((key) => map.get(key) || key).join('、')
}

function taskTemplateLabel(task) {
  if (!task.templateFileId) {
    return '无 Word 模板（文字要求模式）'
  }
  return `${task.templateFileName || `模板 #${task.templateFileId}`}${task.templateDeleted ? '（已删除）' : ''}`
}

onMounted(loadPage)
</script>

<template>
  <section class="page-panel" v-loading="loading">
    <header class="page-header">
      <div>
        <h2>AI 模板生成</h2>
        <p>可选择包含单一内容占位符的 Word 模板，或继续使用文字要求生成。草稿必须人工确认后才能导出。</p>
      </div>
      <el-button :loading="loading" @click="loadPage">刷新</el-button>
    </header>

    <el-alert
      class="privacy-alert"
      type="warning"
      show-icon
      :closable="false"
      title="模板中提取出的正文结构和所选教师资料会发送给 AI；Word 文件本身不会发送。请只选择确实需要的数据范围。"
    />

    <section class="create-panel">
      <el-form label-position="top">
        <el-alert
          class="template-protocol-alert"
          type="info"
          show-icon
          :closable="false"
        >
          <template #title>
            第一版只支持一个 <code v-text="'{{aiContent}}'" /> 占位符。请将它插入 Word 正文或普通表格单元格。
          </template>
          暂不支持页眉、页脚、文本框、批注、修订内容、复杂域、多占位符，也不会自动理解任意 Word 模板。
        </el-alert>

        <el-form-item label="Word 模板（可选）">
          <div class="template-selector">
            <el-select
              v-model="form.templateFileId"
              clearable
              placeholder="不选择时使用文字要求模式"
            >
              <el-option
                v-for="template in wordTemplates"
                :key="template.id"
                :label="template.originalName"
                :value="template.id"
              />
            </el-select>
            <el-upload
              :show-file-list="false"
              :http-request="uploadWordTemplate"
              :accept="allowedWordTemplateAccept"
            >
              <el-button :loading="templateUploading">上传 DOCX 模板</el-button>
            </el-upload>
          </div>
          <div v-if="wordTemplates.length" class="template-list">
            <div v-for="template in wordTemplates" :key="template.id" class="template-item">
              <div>
                <strong>{{ template.originalName }}</strong>
                <span>{{ formatFileSize(template.fileSize) }} · {{ formatCreatedAt(template.createdAt) }}</span>
              </div>
              <div class="template-actions">
                <el-button link type="primary" @click="downloadWordTemplate(template)">下载</el-button>
                <el-button link type="danger" @click="removeWordTemplate(template)">删除</el-button>
              </div>
            </div>
          </div>
        </el-form-item>

        <el-form-item :label="requirementLabel">
          <el-input
            v-model="form.templateRequirement"
            type="textarea"
            :rows="7"
            :placeholder="form.templateFileId
              ? '可补充语气、篇幅或重点要求；留空时按模板结构和教师资料生成。'
              : '例如：请生成一份项目申报用个人学术简介，突出研究方向、代表论文和科研项目。'"
          />
        </el-form-item>
        <el-form-item label="数据范围">
          <el-checkbox-group v-model="form.selectedModules" class="module-grid">
            <el-checkbox v-for="module in modules" :key="module.key" :label="module.key">
              {{ module.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-button type="primary" :loading="generating" @click="submitTask">
          创建 AI 任务
        </el-button>
      </el-form>
    </section>

    <div class="layout-grid">
      <section class="history-panel">
        <h3>生成历史</h3>
        <el-empty v-if="!tasks.length" description="暂无 AI 生成历史" />
        <div v-for="task in tasks" :key="task.id" class="task-item">
          <div class="task-main">
            <strong>#{{ task.id }} {{ task.modelName }}</strong>
            <el-tag :type="statusType(task.status)" size="small">{{ task.status }}</el-tag>
          </div>
          <p v-if="task.templateRequirement">{{ task.templateRequirement }}</p>
          <span>Word 模板：{{ taskTemplateLabel(task) }}</span>
          <span>{{ moduleLabels(task.selectedModules) }}</span>
          <span v-if="task.errorMessage" class="error-text">{{ task.errorMessage }}</span>
          <div class="task-actions">
            <el-button link type="primary" @click="loadTask(task.id)">查看</el-button>
            <el-button link type="danger" @click="removeTask(task)">删除</el-button>
          </div>
        </div>
      </section>

      <section class="draft-panel">
        <template v-if="currentTask">
          <div class="section-header">
            <div>
              <h3>草稿预览 #{{ currentTask.id }}</h3>
              <p>状态：{{ currentTask.status }} / {{ currentTask.result?.status || '无草稿' }}</p>
              <p>Word 模板：{{ taskTemplateLabel(currentTask) }}</p>
            </div>
            <div class="actions">
              <el-button :disabled="!canEdit" :loading="savingDraft" @click="saveDraft">保存草稿</el-button>
              <el-button type="primary" :disabled="!canEdit" :loading="confirming" @click="confirmDraft">
                确认草稿
              </el-button>
              <el-button :disabled="!canExport" @click="exportWord">导出 Word</el-button>
              <el-button :disabled="!canExport" :loading="pdfExporting" @click="exportPdf">导出 PDF</el-button>
            </div>
          </div>
          <el-alert
            v-if="currentTask.errorMessage"
            type="error"
            show-icon
            :closable="false"
            :title="currentTask.errorMessage"
          />
          <el-input
            v-else
            v-model="editedContent"
            type="textarea"
            :rows="22"
            :disabled="!canEdit"
          />
        </template>
        <el-empty v-else description="请选择一条生成历史查看草稿" />
      </section>
    </div>
  </section>
</template>

<style scoped>
.page-panel {
  padding: 28px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.page-header,
.section-header,
.task-main,
.task-actions,
.actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

h2,
h3 {
  margin: 0 0 8px;
}

p {
  margin: 0;
  color: #6b7280;
}

.privacy-alert {
  margin-bottom: 18px;
}

.template-protocol-alert {
  margin-bottom: 18px;
}

.template-protocol-alert code {
  font-weight: 700;
}

.create-panel,
.history-panel,
.draft-panel {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 20px;
  background: #fbfcfe;
}

.create-panel {
  margin-bottom: 20px;
}

.layout-grid {
  display: grid;
  grid-template-columns: minmax(280px, 0.85fr) minmax(0, 1.5fr);
  gap: 20px;
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px 14px;
}

.template-selector {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.template-selector .el-select {
  flex: 1;
}

.template-list {
  width: 100%;
  margin-top: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 0 12px;
}

.template-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #e5e7eb;
}

.template-item:last-child {
  border-bottom: 0;
}

.template-item strong,
.template-item span {
  display: block;
}

.template-item span {
  margin-top: 2px;
  font-size: 12px;
  color: #6b7280;
}

.template-actions {
  display: flex;
  gap: 8px;
}

.task-item {
  padding: 14px 0;
  border-bottom: 1px solid #e5e7eb;
}

.task-item p {
  margin: 6px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.task-item span {
  display: block;
  font-size: 12px;
  color: #6b7280;
}

.error-text {
  color: #b91c1c !important;
}

.task-actions {
  justify-content: flex-start;
  margin-top: 8px;
}

.actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

@media (max-width: 960px) {
  .page-header,
  .section-header,
  .layout-grid {
    display: block;
  }

  .history-panel {
    margin-bottom: 20px;
  }

  .module-grid {
    grid-template-columns: 1fr;
  }

  .template-selector {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
