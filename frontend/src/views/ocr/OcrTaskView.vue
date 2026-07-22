<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { listRecord } from '../../services/academicRecords'
import { listFiles } from '../../services/files'
import {
  confirmOcrTask,
  createOcrTask,
  getOcrTask,
  ignoreOcrResult,
  listOcrTasks,
  updateOcrResult
} from '../../services/ocrTasks'

const loading = ref(false)
const creating = ref(false)
const confirming = ref(false)
const tasks = ref([])
const records = ref([])
const files = ref([])
const currentTask = ref(null)
const selectedResultIds = ref([])
const editorVisible = ref(false)
const editingResultId = ref(null)

const createForm = reactive({
  targetType: 'PAPER',
  recordId: null,
  fileId: null
})

const resultForm = reactive({
  title: '',
  authors: '',
  publicationName: '',
  publicationType: '',
  publishYear: null,
  doi: '',
  volume: '',
  issue: '',
  pages: '',
  publisher: '',
  url: '',
  abstractText: '',
  keywords: '',
  patentName: '',
  patentNumber: '',
  patentType: '',
  patentStatus: '',
  applicationDate: '',
  authorizationDate: '',
  inventors: '',
  certificateName: '',
  certificateType: '',
  issuingAuthority: '',
  issueDate: '',
  description: ''
})

const targetOptions = [
  { value: 'PAPER', label: '论文/学术成果', recordType: 'papers', businessType: 'PAPER', titleKey: 'title' },
  { value: 'PATENT', label: '专利', recordType: 'patents', businessType: 'PATENT', titleKey: 'patentName' },
  { value: 'CERTIFICATE', label: '证书', recordType: 'certificates', businessType: 'CERTIFICATE', titleKey: 'certificateName' }
]

const currentTarget = computed(() =>
  targetOptions.find((item) => item.value === createForm.targetType) || targetOptions[0]
)

const activeResults = computed(() =>
  currentTask.value?.results?.filter((item) => item.status === 'DRAFT') || []
)

async function loadTasks() {
  loading.value = true
  try {
    tasks.value = await listOcrTasks()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载 OCR 任务失败')
  } finally {
    loading.value = false
  }
}

async function loadRecords() {
  createForm.recordId = null
  createForm.fileId = null
  files.value = []
  try {
    records.value = await listRecord(currentTarget.value.recordType)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载记录失败')
  }
}

async function loadFiles() {
  createForm.fileId = null
  files.value = []
  if (!createForm.recordId) {
    return
  }
  try {
    files.value = await listFiles(currentTarget.value.businessType, createForm.recordId)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载附件失败')
  }
}

async function submitTask() {
  if (!createForm.fileId) {
    ElMessage.warning('请先选择要识别的附件')
    return
  }
  creating.value = true
  try {
    const task = await createOcrTask(createForm.fileId, createForm.targetType)
    await loadTasks()
    await loadTask(task.id)
    if (task.status === 'FAILED') {
      ElMessage.warning(task.errorMessage || 'OCR 任务失败')
    } else {
      ElMessage.success('OCR 任务已生成候选结果')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '创建 OCR 任务失败')
  } finally {
    creating.value = false
  }
}

async function loadTask(id) {
  loading.value = true
  try {
    currentTask.value = await getOcrTask(id)
    selectedResultIds.value = activeResults.value.map((item) => item.id)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载 OCR 任务详情失败')
  } finally {
    loading.value = false
  }
}

function recordLabel(record) {
  return record[currentTarget.value.titleKey] || `#${record.id}`
}

function fileLabel(file) {
  return `${file.originalName} (${Math.ceil(file.fileSize / 1024)} KB)`
}

function openEditor(result) {
  editingResultId.value = result.id
  Object.keys(resultForm).forEach((key) => {
    resultForm[key] = result[key] ?? ''
  })
  editorVisible.value = true
}

async function saveResult() {
  try {
    await updateOcrResult(editingResultId.value, {
      ...resultForm,
      publishYear: resultForm.publishYear === '' ? null : resultForm.publishYear,
      applicationDate: resultForm.applicationDate || null,
      authorizationDate: resultForm.authorizationDate || null,
      issueDate: resultForm.issueDate || null
    })
    editorVisible.value = false
    await loadTask(currentTask.value.id)
    ElMessage.success('候选结果已更新')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存候选结果失败')
  }
}

async function ignoreResult(result) {
  try {
    await ElMessageBox.confirm('确认忽略这条候选结果？', '忽略确认', { type: 'warning' })
    await ignoreOcrResult(result.id)
    await loadTask(currentTask.value.id)
    await loadTasks()
    ElMessage.success('候选结果已忽略')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '忽略候选结果失败')
    }
  }
}

async function confirmTask() {
  if (!currentTask.value || !selectedResultIds.value.length) {
    ElMessage.warning('请至少选择一条候选结果')
    return
  }
  confirming.value = true
  try {
    const result = await confirmOcrTask(currentTask.value.id, selectedResultIds.value)
    await loadTask(currentTask.value.id)
    await loadTasks()
    ElMessage.success(`已确认 ${result.confirmedCount} 条，跳过 ${result.skippedCount} 条`)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '确认入库失败')
  } finally {
    confirming.value = false
  }
}

function statusType(status) {
  if (status === 'SUCCEEDED' || status === 'CONFIRMED' || status === 'DRAFT') {
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

function resultTitle(result) {
  return result.title || result.patentName || result.certificateName || '未识别标题'
}

function shouldShowField(key) {
  const targetType = currentTask.value?.targetType || createForm.targetType
  const fields = {
    PAPER: [
      'title',
      'authors',
      'publicationName',
      'publicationType',
      'publishYear',
      'doi',
      'volume',
      'issue',
      'pages',
      'publisher',
      'url',
      'abstractText',
      'keywords'
    ],
    PATENT: [
      'patentName',
      'patentNumber',
      'patentType',
      'patentStatus',
      'applicationDate',
      'authorizationDate',
      'inventors',
      'description'
    ],
    CERTIFICATE: ['certificateName', 'certificateType', 'issuingAuthority', 'issueDate', 'description']
  }
  return fields[targetType]?.includes(key)
}

onMounted(async () => {
  await Promise.all([loadTasks(), loadRecords()])
})
</script>

<template>
  <section class="page-panel">
    <header class="page-header">
      <div>
        <h2>OCR 识别</h2>
        <p>从已上传的 PDF 或图片附件创建识别任务，先预览并修改候选结果，确认后再写入正式数据。</p>
      </div>
      <el-button :loading="loading" @click="loadTasks">刷新任务</el-button>
    </header>

    <section class="create-panel">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="识别目标">
            <el-select v-model="createForm.targetType" @change="loadRecords">
              <el-option
                v-for="item in targetOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="选择已有记录">
            <el-select v-model="createForm.recordId" filterable placeholder="先选择带附件的记录" @change="loadFiles">
              <el-option
                v-for="record in records"
                :key="record.id"
                :label="recordLabel(record)"
                :value="record.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="选择附件">
            <el-select v-model="createForm.fileId" filterable placeholder="PDF / 图片附件">
              <el-option v-for="file in files" :key="file.id" :label="fileLabel(file)" :value="file.id" />
            </el-select>
          </el-form-item>
        </div>
        <el-button type="primary" :loading="creating" @click="submitTask">创建 OCR 任务</el-button>
      </el-form>
    </section>

    <section class="content-section">
      <h3>任务列表</h3>
      <el-table v-loading="loading" :data="tasks" border>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="targetType" label="目标" width="120" />
        <el-table-column prop="recognitionMode" label="识别方式" width="130" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误信息" min-width="220" show-overflow-tooltip />
        <el-table-column prop="resultCount" label="候选数" width="90" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="loadTask(row.id)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-if="currentTask" class="content-section">
      <div class="section-header">
        <div>
          <h3>任务 #{{ currentTask.id }} 候选结果</h3>
          <p v-if="currentTask.errorMessage">错误：{{ currentTask.errorMessage }}</p>
          <p v-else>请人工核对识别结果，修改无误后再确认入库。</p>
        </div>
        <el-button
          type="primary"
          :loading="confirming"
          :disabled="!activeResults.length"
          @click="confirmTask"
        >
          确认入库
        </el-button>
      </div>

      <el-table
        :data="currentTask.results || []"
        border
        row-key="id"
        @selection-change="(rows) => (selectedResultIds = rows.map((row) => row.id))"
      >
        <el-table-column type="selection" width="46" :selectable="(row) => row.status === 'DRAFT'" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标题/名称" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ resultTitle(row) }}</template>
        </el-table-column>
        <el-table-column prop="createdRecordId" label="正式记录 ID" width="120" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.status === 'CONFIRMED'" @click="openEditor(row)">
              编辑
            </el-button>
            <el-button link type="danger" :disabled="row.status === 'CONFIRMED'" @click="ignoreResult(row)">
              忽略
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-collapse v-if="currentTask.rawText" class="raw-text">
        <el-collapse-item title="查看原始识别文本">
          <pre>{{ currentTask.rawText }}</pre>
        </el-collapse-item>
      </el-collapse>
    </section>

    <el-dialog v-model="editorVisible" title="编辑 OCR 候选结果" width="760px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item v-if="shouldShowField('title')" label="标题">
            <el-input v-model="resultForm.title" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('authors')" label="作者">
            <el-input v-model="resultForm.authors" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('publicationName')" label="期刊/会议/出版物">
            <el-input v-model="resultForm.publicationName" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('publicationType')" label="成果类型">
            <el-input v-model="resultForm.publicationType" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('publishYear')" label="发表年份">
            <el-input-number v-model="resultForm.publishYear" :min="0" :max="9999" controls-position="right" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('doi')" label="DOI">
            <el-input v-model="resultForm.doi" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('volume')" label="卷">
            <el-input v-model="resultForm.volume" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('issue')" label="期">
            <el-input v-model="resultForm.issue" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('pages')" label="页码">
            <el-input v-model="resultForm.pages" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('publisher')" label="出版社">
            <el-input v-model="resultForm.publisher" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('url')" label="URL">
            <el-input v-model="resultForm.url" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('keywords')" label="关键词">
            <el-input v-model="resultForm.keywords" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('patentName')" label="专利名称">
            <el-input v-model="resultForm.patentName" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('patentNumber')" label="专利号">
            <el-input v-model="resultForm.patentNumber" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('patentType')" label="专利类型">
            <el-input v-model="resultForm.patentType" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('patentStatus')" label="状态">
            <el-input v-model="resultForm.patentStatus" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('applicationDate')" label="申请日期">
            <el-date-picker v-model="resultForm.applicationDate" type="date" value-format="YYYY-MM-DD" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('authorizationDate')" label="授权日期">
            <el-date-picker v-model="resultForm.authorizationDate" type="date" value-format="YYYY-MM-DD" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('inventors')" label="发明人">
            <el-input v-model="resultForm.inventors" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('certificateName')" label="证书名称">
            <el-input v-model="resultForm.certificateName" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('certificateType')" label="证书类型">
            <el-input v-model="resultForm.certificateType" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('issuingAuthority')" label="颁发机构">
            <el-input v-model="resultForm.issuingAuthority" />
          </el-form-item>
          <el-form-item v-if="shouldShowField('issueDate')" label="颁发日期">
            <el-date-picker v-model="resultForm.issueDate" type="date" value-format="YYYY-MM-DD" />
          </el-form-item>
        </div>
        <el-form-item v-if="shouldShowField('abstractText')" label="摘要">
          <el-input v-model="resultForm.abstractText" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item v-if="shouldShowField('description')" label="说明">
          <el-input v-model="resultForm.description" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" @click="saveResult">保存</el-button>
      </template>
    </el-dialog>
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
.section-header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
  margin-bottom: 20px;
}

h2,
h3 {
  margin: 0 0 8px;
}

p {
  margin: 0;
  color: #6b7280;
}

.create-panel,
.content-section {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 20px;
  background: #fbfcfe;
  margin-bottom: 20px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 16px;
}

.raw-text {
  margin-top: 18px;
}

pre {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  color: #374151;
}

@media (max-width: 900px) {
  .page-header,
  .section-header {
    flex-direction: column;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
