<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  confirmBibtexTask,
  getBibtexTask,
  ignoreBibtexItem,
  importBibtexFile,
  importBibtexText,
  listBibtexTasks,
  updateBibtexItem
} from '../../services/bibtexImports'

const loading = ref(false)
const importing = ref(false)
const confirming = ref(false)
const tasks = ref([])
const currentTask = ref(null)
const selectedItemIds = ref([])
const textContent = ref('')
const editorVisible = ref(false)
const editingItemId = ref(null)

const itemForm = reactive({
  entryType: '',
  bibKey: '',
  title: '',
  authors: '',
  journal: '',
  booktitle: '',
  year: null,
  doi: '',
  volume: '',
  number: '',
  pages: '',
  publisher: '',
  url: '',
  abstractText: '',
  keywords: '',
  selected: true
})

const importableItems = computed(() =>
  currentTask.value?.items?.filter((item) => item.status === 'PARSED') || []
)

async function loadTasks() {
  loading.value = true
  try {
    tasks.value = await listBibtexTasks()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载 BibTeX 导入任务失败')
  } finally {
    loading.value = false
  }
}

async function loadTask(id) {
  loading.value = true
  try {
    currentTask.value = await getBibtexTask(id)
    selectedItemIds.value = currentTask.value.items
      .filter((item) => item.status === 'PARSED' && item.selected)
      .map((item) => item.id)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载导入候选失败')
  } finally {
    loading.value = false
  }
}

async function submitTextImport() {
  if (!textContent.value.trim()) {
    ElMessage.warning('请先粘贴 BibTeX 内容')
    return
  }
  importing.value = true
  try {
    const task = await importBibtexText(textContent.value)
    textContent.value = ''
    await loadTasks()
    await loadTask(task.id)
    ElMessage.success('BibTeX 文本已解析')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || 'BibTeX 文本解析失败')
  } finally {
    importing.value = false
  }
}

async function uploadBibFile(options) {
  importing.value = true
  try {
    const task = await importBibtexFile(options.file)
    options.onSuccess?.(task)
    await loadTasks()
    await loadTask(task.id)
    ElMessage.success('.bib 文件已解析')
  } catch (error) {
    options.onError?.(error)
    ElMessage.error(error.response?.data?.message || '.bib 文件解析失败')
  } finally {
    importing.value = false
  }
}

function openEditor(item) {
  editingItemId.value = item.id
  Object.assign(itemForm, {
    entryType: item.entryType || '',
    bibKey: item.bibKey || '',
    title: item.title || '',
    authors: item.authors || '',
    journal: item.journal || '',
    booktitle: item.booktitle || '',
    year: item.year,
    doi: item.doi || '',
    volume: item.volume || '',
    number: item.number || '',
    pages: item.pages || '',
    publisher: item.publisher || '',
    url: item.url || '',
    abstractText: item.abstractText || '',
    keywords: item.keywords || '',
    selected: item.selected !== false
  })
  editorVisible.value = true
}

async function saveItem() {
  try {
    await updateBibtexItem(editingItemId.value, {
      ...itemForm,
      year: itemForm.year === '' ? null : itemForm.year
    })
    editorVisible.value = false
    await loadTask(currentTask.value.id)
    await loadTasks()
    ElMessage.success('候选条目已更新')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存候选条目失败')
  }
}

async function ignoreItem(item) {
  try {
    await ElMessageBox.confirm('确认忽略这条候选记录？', '忽略确认', { type: 'warning' })
    await ignoreBibtexItem(item.id)
    await loadTask(currentTask.value.id)
    await loadTasks()
    ElMessage.success('候选条目已忽略')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '忽略候选条目失败')
    }
  }
}

async function confirmImport(forceDuplicates = false) {
  if (!currentTask.value) {
    return
  }
  if (!selectedItemIds.value.length) {
    ElMessage.warning('请至少选择一条候选记录')
    return
  }
  confirming.value = true
  try {
    const result = await confirmBibtexTask(currentTask.value.id, selectedItemIds.value, forceDuplicates)
    await loadTask(currentTask.value.id)
    await loadTasks()
    ElMessage.success(`已导入 ${result.importedCount} 条，跳过 ${result.skippedCount} 条`)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '确认导入失败')
  } finally {
    confirming.value = false
  }
}

function statusType(status) {
  if (status === 'PARSED') {
    return 'success'
  }
  if (status === 'FAILED') {
    return 'danger'
  }
  if (status === 'IMPORTED') {
    return 'primary'
  }
  return 'info'
}

function duplicateType(item) {
  return item.duplicateStatus === 'POSSIBLE' ? 'warning' : 'info'
}

onMounted(loadTasks)
</script>

<template>
  <section class="page-panel">
    <header class="page-header">
      <div>
        <h2>BibTeX 导入</h2>
        <p>粘贴 BibTeX 或上传 .bib 文件，先预览候选成果，确认后再写入论文/学术成果。</p>
      </div>
    </header>

    <div class="import-grid">
      <section class="import-box">
        <h3>粘贴导入</h3>
        <el-input
          v-model="textContent"
          type="textarea"
          :rows="9"
          placeholder="@article{key, title={...}, author={...}}"
        />
        <el-button type="primary" :loading="importing" @click="submitTextImport">
          解析文本
        </el-button>
      </section>

      <section class="import-box">
        <h3>文件导入</h3>
        <el-upload
          drag
          :show-file-list="false"
          :http-request="uploadBibFile"
          accept=".bib"
        >
          <div class="upload-text">拖拽或点击上传 .bib 文件</div>
        </el-upload>
      </section>
    </div>

    <section class="content-section">
      <div class="section-header">
        <h3>导入任务</h3>
        <el-button :loading="loading" @click="loadTasks">刷新</el-button>
      </div>
      <el-table v-loading="loading" :data="tasks" border>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="sourceType" label="来源" width="100" />
        <el-table-column prop="fileName" label="文件名" min-width="160" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="totalCount" label="总数" width="80" />
        <el-table-column prop="successCount" label="可导入" width="90" />
        <el-table-column prop="failedCount" label="失败" width="80" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="loadTask(row.id)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-if="currentTask" class="content-section">
      <div class="section-header">
        <div>
          <h3>候选条目</h3>
          <p>任务 #{{ currentTask.id }}，可修改候选字段；重复记录会提示但不会覆盖旧数据。</p>
        </div>
        <div class="actions">
          <el-button
            type="primary"
            :loading="confirming"
            :disabled="!importableItems.length"
            @click="confirmImport(false)"
          >
            确认导入
          </el-button>
          <el-button
            :loading="confirming"
            :disabled="!importableItems.length"
            @click="confirmImport(true)"
          >
            强制导入重复项
          </el-button>
        </div>
      </div>

      <el-table
        :data="currentTask.items"
        border
        row-key="id"
        @selection-change="(rows) => (selectedItemIds = rows.map((row) => row.id))"
      >
        <el-table-column type="selection" width="46" :selectable="(row) => row.status === 'PARSED'" />
        <el-table-column prop="status" label="状态" width="105">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duplicateStatus" label="重复" width="105">
          <template #default="{ row }">
            <el-tag :type="duplicateType(row)">{{ row.duplicateStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="authors" label="作者" min-width="160" show-overflow-tooltip />
        <el-table-column prop="year" label="年份" width="90" />
        <el-table-column prop="doi" label="DOI" min-width="150" show-overflow-tooltip />
        <el-table-column prop="errorMessage" label="错误原因" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.status === 'IMPORTED'" @click="openEditor(row)">
              编辑
            </el-button>
            <el-button link type="danger" :disabled="row.status === 'IMPORTED'" @click="ignoreItem(row)">
              忽略
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="editorVisible" title="编辑候选条目" width="720px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="entryType">
            <el-input v-model="itemForm.entryType" />
          </el-form-item>
          <el-form-item label="bibKey">
            <el-input v-model="itemForm.bibKey" />
          </el-form-item>
          <el-form-item label="标题">
            <el-input v-model="itemForm.title" />
          </el-form-item>
          <el-form-item label="年份">
            <el-input-number v-model="itemForm.year" :min="0" :max="9999" controls-position="right" />
          </el-form-item>
          <el-form-item label="作者">
            <el-input v-model="itemForm.authors" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="DOI">
            <el-input v-model="itemForm.doi" />
          </el-form-item>
          <el-form-item label="journal">
            <el-input v-model="itemForm.journal" />
          </el-form-item>
          <el-form-item label="booktitle">
            <el-input v-model="itemForm.booktitle" />
          </el-form-item>
          <el-form-item label="volume">
            <el-input v-model="itemForm.volume" />
          </el-form-item>
          <el-form-item label="number/issue">
            <el-input v-model="itemForm.number" />
          </el-form-item>
          <el-form-item label="pages">
            <el-input v-model="itemForm.pages" />
          </el-form-item>
          <el-form-item label="publisher">
            <el-input v-model="itemForm.publisher" />
          </el-form-item>
          <el-form-item label="url">
            <el-input v-model="itemForm.url" />
          </el-form-item>
          <el-form-item label="keywords">
            <el-input v-model="itemForm.keywords" />
          </el-form-item>
        </div>
        <el-form-item label="abstract">
          <el-input v-model="itemForm.abstractText" type="textarea" :rows="4" />
        </el-form-item>
        <el-checkbox v-model="itemForm.selected">默认勾选导入</el-checkbox>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" @click="saveItem">保存</el-button>
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

.import-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(280px, 0.8fr);
  gap: 18px;
  margin-bottom: 22px;
}

.import-box,
.content-section {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 20px;
  background: #fbfcfe;
}

.import-box .el-button {
  margin-top: 14px;
}

.upload-text {
  color: #4b5563;
  padding: 26px 0;
}

.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

@media (max-width: 860px) {
  .import-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .page-header,
  .section-header {
    flex-direction: column;
  }
}
</style>
