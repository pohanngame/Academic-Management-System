<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { createBlock, deleteBlock, listBlock, updateBlock } from '../../services/profileBlocks'

const route = useRoute()
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const records = ref([])

const configs = {
  academicQualifications: {
    title: '学历',
    description: '维护学历、学位、学校和专业等信息。',
    fields: [
      { key: 'degree', label: '学位/学历', required: true },
      { key: 'institution', label: '学校/机构' },
      { key: 'major', label: '专业' },
      { key: 'startDate', label: '开始日期', type: 'date' },
      { key: 'endDate', label: '结束日期', type: 'date' },
      { key: 'description', label: '说明', type: 'textarea' }
    ],
    columns: ['degree', 'institution', 'major']
  },
  teachingAreas: {
    title: '教学方向',
    description: '维护公开简介中的教学方向。',
    fields: [
      { key: 'name', label: '方向名称', required: true },
      { key: 'description', label: '说明', type: 'textarea' }
    ],
    columns: ['name', 'description']
  },
  researchAreas: {
    title: '研究方向',
    description: '维护公开简介中的研究方向。',
    fields: [
      { key: 'name', label: '方向名称', required: true },
      { key: 'description', label: '说明', type: 'textarea' }
    ],
    columns: ['name', 'description']
  },
  professionalServices: {
    title: '专业服务',
    description: '维护学术组织、评审、委员会等专业服务信息。',
    fields: [
      { key: 'title', label: '服务名称', required: true },
      { key: 'organization', label: '机构/组织' },
      { key: 'role', label: '角色' },
      { key: 'startDate', label: '开始日期', type: 'date' },
      { key: 'endDate', label: '结束日期', type: 'date' },
      { key: 'description', label: '说明', type: 'textarea' }
    ],
    columns: ['title', 'organization', 'role']
  },
  workingExperiences: {
    title: '工作经历',
    description: '维护任职单位、职位和经历说明。',
    fields: [
      { key: 'organization', label: '单位', required: true },
      { key: 'position', label: '职位' },
      { key: 'startDate', label: '开始日期', type: 'date' },
      { key: 'endDate', label: '结束日期', type: 'date' },
      { key: 'description', label: '说明', type: 'textarea' }
    ],
    columns: ['organization', 'position', 'description']
  }
}

const blockType = computed(() => route.meta.blockType)
const config = computed(() => configs[blockType.value])

const form = reactive({})

function resetForm(record = null) {
  Object.keys(form).forEach((key) => delete form[key])
  for (const field of config.value.fields) {
    form[field.key] = record?.[field.key] ?? ''
  }
  form.sortOrder = record?.sortOrder ?? 0
  form.isPublic = record?.isPublic ?? true
  editingId.value = record?.id ?? null
}

async function loadRecords() {
  loading.value = true
  try {
    records.value = await listBlock(blockType.value)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || `加载${config.value.title}失败`)
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(record) {
  resetForm(record)
  dialogVisible.value = true
}

async function saveRecord() {
  saving.value = true
  try {
    const payload = { ...form }
    if (editingId.value) {
      await updateBlock(blockType.value, editingId.value, payload)
    } else {
      await createBlock(blockType.value, payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
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
    await deleteBlock(blockType.value, record.id)
    ElMessage.success('删除成功')
    await loadRecords()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '删除失败')
    }
  }
}

function fieldLabel(key) {
  return config.value.fields.find((field) => field.key === key)?.label || key
}

watch(blockType, () => {
  resetForm()
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
      <el-button type="primary" @click="openCreateDialog">新增</el-button>
    </header>

    <el-table v-loading="loading" :data="records" border>
      <el-table-column prop="sortOrder" label="排序" width="90" />
      <el-table-column
        v-for="column in config.columns"
        :key="column"
        :prop="column"
        :label="fieldLabel(column)"
        min-width="160"
        show-overflow-tooltip
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
          <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
          <el-button link type="danger" @click="removeRecord(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑记录' : '新增记录'" width="640px">
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
          <el-input
            v-else-if="field.type === 'textarea'"
            v-model="form[field.key]"
            type="textarea"
            :rows="4"
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
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRecord">保存</el-button>
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

.page-header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
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

@media (max-width: 720px) {
  .page-header,
  .form-row {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
