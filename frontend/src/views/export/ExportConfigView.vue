<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  deleteExportTemplate,
  downloadExportFile,
  getExportFields,
  listExportTemplates,
  saveExportTemplate
} from '../../services/exports'

const loading = ref(false)
const exporting = ref(false)
const savingTemplate = ref(false)
const fields = ref([])
const templates = ref([])
const selectedTemplateId = ref(null)

const form = reactive({
  exportType: 'EXCEL',
  templateName: '',
  modules: []
})

const selectedModules = computed(() => form.modules.filter((module) => module.enabled))

function initializeModules(definitions) {
  form.modules = definitions.map((module) => ({
    key: module.key,
    label: module.label,
    enabled: module.key === 'teacherProfile',
    fields: module.fields.map((field) => ({
      key: field.key,
      label: field.label,
      enabled: true
    }))
  }))
}

async function loadPage() {
  loading.value = true
  try {
    const [fieldDefinitions, templateList] = await Promise.all([
      getExportFields(),
      listExportTemplates()
    ])
    fields.value = fieldDefinitions
    initializeModules(fieldDefinitions)
    templates.value = templateList
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载导出配置失败')
  } finally {
    loading.value = false
  }
}

function selectedFieldCount(module) {
  return module.fields.filter((field) => field.enabled).length
}

function moveField(module, index, direction) {
  const nextIndex = index + direction
  if (nextIndex < 0 || nextIndex >= module.fields.length) {
    return
  }
  const nextFields = [...module.fields]
  const current = nextFields[index]
  nextFields[index] = nextFields[nextIndex]
  nextFields[nextIndex] = current
  module.fields = nextFields
}

function buildPayload() {
  return {
    exportType: form.exportType,
    modules: selectedModules.value.map((module) => ({
      moduleKey: module.key,
      fields: module.fields
        .filter((field) => field.enabled)
        .map((field) => field.key)
    }))
  }
}

function validatePayload(payload) {
  if (!payload.modules.length) {
    ElMessage.warning('请至少选择一个导出模块')
    return false
  }
  const emptyModule = payload.modules.find((module) => !module.fields.length)
  if (emptyModule) {
    ElMessage.warning('每个导出模块至少选择一个字段')
    return false
  }
  return true
}

async function exportFile() {
  const payload = buildPayload()
  if (!validatePayload(payload)) {
    return
  }
  exporting.value = true
  try {
    await downloadExportFile(form.exportType, payload)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

async function saveTemplate() {
  if (!form.templateName.trim()) {
    ElMessage.warning('请输入模板名称')
    return
  }
  const payload = {
    templateName: form.templateName.trim(),
    ...buildPayload()
  }
  if (!validatePayload(payload)) {
    return
  }
  savingTemplate.value = true
  try {
    await saveExportTemplate(payload)
    ElMessage.success('模板已保存')
    templates.value = await listExportTemplates()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存模板失败')
  } finally {
    savingTemplate.value = false
  }
}

function applyTemplate(template) {
  form.exportType = template.exportType
  form.templateName = template.templateName
  selectedTemplateId.value = template.id
  const moduleMap = new Map(template.modules.map((module) => [module.moduleKey, module.fields]))
  form.modules = fields.value.map((module) => {
    const selectedFields = moduleMap.get(module.key)
    const orderedKeys = selectedFields || module.fields.map((field) => field.key)
    const fieldMap = new Map(module.fields.map((field) => [field.key, field]))
    const orderedFields = [
      ...orderedKeys.filter((key) => fieldMap.has(key)).map((key) => fieldMap.get(key)),
      ...module.fields.filter((field) => !orderedKeys.includes(field.key))
    ]
    return {
      key: module.key,
      label: module.label,
      enabled: Boolean(selectedFields),
      fields: orderedFields.map((field) => ({
        key: field.key,
        label: field.label,
        enabled: selectedFields ? selectedFields.includes(field.key) : true
      }))
    }
  })
}

async function removeTemplate(template) {
  try {
    await ElMessageBox.confirm(`确认删除模板“${template.templateName}”？`, '删除确认', { type: 'warning' })
    await deleteExportTemplate(template.id)
    ElMessage.success('模板已删除')
    templates.value = await listExportTemplates()
    if (selectedTemplateId.value === template.id) {
      selectedTemplateId.value = null
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '删除模板失败')
    }
  }
}

onMounted(loadPage)
</script>

<template>
  <section class="page-panel" v-loading="loading">
    <header class="page-header">
      <div>
        <h2>导出</h2>
        <p>选择模块、字段和顺序，导出当前登录教师自己的资料。</p>
      </div>
      <div class="header-actions">
        <el-button :loading="savingTemplate" @click="saveTemplate">保存为模板</el-button>
        <el-button type="primary" :loading="exporting" @click="exportFile">导出文件</el-button>
      </div>
    </header>

    <div class="layout-grid">
      <section class="config-panel">
        <div class="form-row">
          <el-form-item label="导出类型">
            <el-radio-group v-model="form.exportType">
              <el-radio-button label="EXCEL">Excel</el-radio-button>
              <el-radio-button label="WORD">Word</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="模板名称">
            <el-input v-model="form.templateName" placeholder="例如 简历全部字段" />
          </el-form-item>
        </div>

        <el-collapse>
          <el-collapse-item
            v-for="module in form.modules"
            :key="module.key"
            :name="module.key"
          >
            <template #title>
              <div class="module-title">
                <el-checkbox v-model="module.enabled" @click.stop />
                <span>{{ module.label }}</span>
                <el-tag size="small" type="info">{{ selectedFieldCount(module) }} 个字段</el-tag>
              </div>
            </template>
            <div class="field-list">
              <div v-for="(field, index) in module.fields" :key="field.key" class="field-row">
                <el-checkbox v-model="field.enabled">{{ field.label }}</el-checkbox>
                <div class="field-actions">
                  <el-button link :disabled="index === 0" @click="moveField(module, index, -1)">上移</el-button>
                  <el-button
                    link
                    :disabled="index === module.fields.length - 1"
                    @click="moveField(module, index, 1)"
                  >
                    下移
                  </el-button>
                </div>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </section>

      <aside class="template-panel">
        <h3>常用模板</h3>
        <el-empty v-if="!templates.length" description="暂无模板" />
        <div v-for="template in templates" :key="template.id" class="template-item">
          <div>
            <strong>{{ template.templateName }}</strong>
            <span>{{ template.exportType }}</span>
          </div>
          <div class="template-actions">
            <el-button link type="primary" @click="applyTemplate(template)">使用</el-button>
            <el-button link type="danger" @click="removeTemplate(template)">删除</el-button>
          </div>
        </div>
      </aside>
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

.page-header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
}

h2,
h3 {
  margin: 0 0 8px;
}

p {
  margin: 0;
  color: #6b7280;
}

.header-actions,
.form-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.layout-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 20px;
}

.config-panel,
.template-panel {
  min-width: 0;
}

.module-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.field-list {
  display: grid;
  gap: 8px;
}

.field-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.field-actions,
.template-actions {
  display: flex;
  gap: 8px;
}

.template-item {
  padding: 12px 0;
  border-bottom: 1px solid #e5e7eb;
}

.template-item strong,
.template-item span {
  display: block;
}

.template-item span {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
}

@media (max-width: 900px) {
  .page-header,
  .header-actions,
  .form-row {
    flex-direction: column;
    align-items: stretch;
  }

  .layout-grid {
    grid-template-columns: 1fr;
  }
}
</style>
