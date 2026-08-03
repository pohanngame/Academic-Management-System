<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { createRecord, deleteRecord, listRecord, updateRecord } from '../../services/academicRecords'
import {
  confirmPaperRatings,
  getJournalMetricSuggestions,
  previewPaperRatings,
  searchPublicationVenues
} from '../../services/publicationRatings'
import {
  findBulkSelectedReference,
  filterBulkConfirmSelection,
  isValidImpactFactor,
  isValidMetricYear,
  sanitizePaperRatingPayload,
  shouldClearCcfSelection
} from './publicationRatingRules'
import { getResourcePolicies, updateResourcePolicies } from '../../services/access'
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
const policies = ref({})
const venueMatches = ref([])
const venueSearchMessage = ref('')
const venueSearching = ref(false)
const metricSuggestions = ref([])
const bulkDialogVisible = ref(false)
const bulkPreview = ref({ total: 0, page: 1, size: 20, items: [] })
const bulkSelection = ref([])
const selectedVenueName = ref('')
const selectedVenueType = ref('')
let venueSearchTimer

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
      {
        key: 'venueType', label: '发表载体', type: 'select', defaultValue: 'UNKNOWN',
        options: [
          { value: 'UNKNOWN', label: '待确认' },
          { value: 'JOURNAL', label: '期刊论文' },
          { value: 'CONFERENCE', label: '会议论文' }
        ]
      },
      { key: 'venueAbbreviation', label: '标准简称' },
      { key: 'issn', label: 'ISSN', visibleWhen: (value) => value.venueType === 'JOURNAL' },
      { key: 'eissn', label: 'eISSN', visibleWhen: (value) => value.venueType === 'JOURNAL' },
      { key: 'jcrDataYear', label: 'JCR 数据年度', type: 'number', precision: 0, step: 1, visibleWhen: (value) => value.venueType === 'JOURNAL' },
      { key: 'journalImpactFactor', label: 'Journal Impact Factor', type: 'number', precision: 6, step: 0.1, visibleWhen: (value) => value.venueType === 'JOURNAL' },
      {
        key: 'jcrQuartile', label: 'JCR 分区', type: 'select', visibleWhen: (value) => value.venueType === 'JOURNAL',
        options: ['Q1', 'Q2', 'Q3', 'Q4'].map((value) => ({ value, label: value }))
      },
      { key: 'jcrSubject', label: 'JCR 学科', visibleWhen: (value) => value.venueType === 'JOURNAL' },
      { key: 'metricSourceDescription', label: '指标来源说明', visibleWhen: (value) => value.venueType === 'JOURNAL' },
      { key: 'metricSourceUrl', label: '指标来源 URL（后端不会访问）', visibleWhen: (value) => value.venueType === 'JOURNAL' },
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
    columns: ['title', 'authors', 'publicationName', 'venueType', 'ccfRank', 'jcrQuartile', 'journalImpactFactor', 'publishYear']
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
      {
        key: 'competitionCategory',
        label: '赛事类别',
        type: 'select',
        options: [
          { value: 'FIRST_CLASS', label: '一类' },
          { value: 'SECOND_CLASS', label: '二类' }
        ],
        defaultValue: 'SECOND_CLASS'
      },
      { key: 'issuingAuthority', label: '颁发机构' },
      { key: 'issueDate', label: '颁发日期', type: 'date' },
      { key: 'description', label: '说明', type: 'textarea' }
    ],
    columns: ['certificateName', 'certificateType', 'competitionCategory', 'issuingAuthority', 'issueDate']
  }
}

const recordType = computed(() => route.meta.recordType)
const config = computed(() => configs[recordType.value])
const attachmentBusinessTypes = {
  papers: 'PAPER',
  patents: 'PATENT',
  certificates: 'CERTIFICATE'
}
const accessResourceTypes = {
  projects: 'PROJECT',
  teachingCourses: 'TEACHING_COURSE',
  papers: 'PAPER',
  patents: 'PATENT',
  certificates: 'CERTIFICATE'
}
const supportsAttachments = computed(() => Boolean(attachmentBusinessTypes[recordType.value]))

const form = reactive({})

function resetForm(record = null) {
  Object.keys(form).forEach((key) => delete form[key])
  for (const field of config.value.fields) {
    form[field.key] = record?.[field.key] ?? field.defaultValue ?? (field.type === 'number' ? null : '')
  }
  form.sortOrder = record?.sortOrder ?? 0
  form.isPublic = record?.isPublic ?? true
  form.ccfReferenceId = record?.ccfReferenceId ?? null
  form.ccfConfirmed = Boolean(record?.ccfConfirmedAt)
  form.manualCcfRank = null
  form.manualCcfCategoryId = null
  form.manualCcfCategoryName = null
  form.journalMetricReferenceId = null
  form.metricConfirmed = false
  editingId.value = record?.id ?? null
  venueMatches.value = []
  metricSuggestions.value = []
  selectedVenueName.value = record?.ccfReferenceId ? (record.publicationName || '') : ''
  selectedVenueType.value = record?.ccfReferenceId ? (record.venueType || '') : ''
}

function buildPayload() {
  const payload = {}
  for (const field of config.value.fields) {
    const value = form[field.key]
    payload[field.key] = value === '' ? null : value
  }
  payload.sortOrder = form.sortOrder ?? 0
  payload.isPublic = form.isPublic ?? true
  payload.ccfReferenceId = form.ccfReferenceId
  payload.ccfConfirmed = Boolean(form.ccfConfirmed)
  payload.manualCcfRank = form.manualCcfRank
  payload.manualCcfCategoryId = form.manualCcfCategoryId
  payload.manualCcfCategoryName = form.manualCcfCategoryName
  payload.journalMetricReferenceId = form.journalMetricReferenceId
  payload.metricConfirmed = Boolean(form.metricConfirmed)
  return recordType.value === 'papers' ? sanitizePaperRatingPayload(payload, selectedVenueType.value) : payload
}

async function loadVenueMatches() {
  if (recordType.value !== 'papers' || !['JOURNAL', 'CONFERENCE'].includes(form.venueType)
      || !form.publicationName || form.publicationName.trim().length < 2) {
    venueMatches.value = []
    return
  }
  venueSearching.value = true
  try {
    const response = await searchPublicationVenues(form.publicationName.trim(), form.venueType)
    venueMatches.value = response.items || []
    venueSearchMessage.value = response.message || ''
  } catch {
    venueMatches.value = []
    venueSearchMessage.value = 'CCF 参考数据查询失败'
  } finally {
    venueSearching.value = false
  }
}

function selectVenueMatch(match) {
  selectedVenueName.value = match.standardName
  selectedVenueType.value = match.venueType || form.venueType
  form.ccfReferenceId = match.referenceId
  form.ccfConfirmed = true
  form.manualCcfRank = null
  form.manualCcfCategoryId = null
  form.manualCcfCategoryName = null
  form.publicationName = match.standardName
  form.venueAbbreviation = match.abbreviation || ''
}

async function loadMetricSuggestions() {
  try {
    metricSuggestions.value = await getJournalMetricSuggestions({
      issn: form.issn || undefined,
      eissn: form.eissn || undefined,
      journalName: form.publicationName || undefined,
      year: form.jcrDataYear || undefined
    })
    if (!metricSuggestions.value.length) {
      ElMessage.info('当前未内置符合许可要求的非官方 JCR 建议数据，请手工填写并自行核实')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || 'JCR 建议查询失败')
  }
}

function selectMetricSuggestion(suggestion) {
  form.journalMetricReferenceId = suggestion.referenceId
  form.jcrDataYear = suggestion.dataYear
  form.journalImpactFactor = suggestion.impactFactor
  form.jcrQuartile = suggestion.jcrQuartile
  form.jcrSubject = suggestion.subject || ''
  form.metricSourceDescription = suggestion.sourceName
  form.metricSourceUrl = suggestion.sourceUrl
  form.metricConfirmed = false
}

async function openBulkPreview(page = 1) {
  try {
    bulkPreview.value = await previewPaperRatings(page, 20)
    bulkSelection.value = []
    bulkDialogVisible.value = true
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '历史论文扫描失败')
  }
}

function chooseBulkCandidate(item, referenceId) {
  const existing = bulkSelection.value.find((selection) => selection.paperId === item.paperId)
  if (existing) existing.referenceId = referenceId
  else bulkSelection.value.push({ paperId: item.paperId, referenceId, status: item.status })
}

async function submitBulkConfirmation() {
  const selected = filterBulkConfirmSelection(bulkSelection.value)
  if (!selected.length) return ElMessage.warning('请先选择需要确认的匹配结果')
  try {
    await confirmPaperRatings(selected.map(({ paperId, referenceId }) => ({ paperId, referenceId })))
    ElMessage.success(`已确认 ${selected.length} 条 CCF 匹配结果`)
    bulkDialogVisible.value = false
    await loadRecords()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '批量确认失败')
  }
}

async function loadRecords() {
  loading.value = true
  try {
    const [recordList, policyList] = await Promise.all([
      listRecord(recordType.value),
      getResourcePolicies(accessResourceTypes[recordType.value])
    ])
    records.value = recordList
    policies.value = Object.fromEntries(policyList.map((item) => [item.resourceId, item]))
  } catch (error) {
    ElMessage.error(error.response?.data?.message || `加载${config.value.title}失败`)
  } finally {
    loading.value = false
  }
}

async function savePolicies() {
  try {
    await updateResourcePolicies(records.value.map((record) => ({
      resourceType: accessResourceTypes[recordType.value],
      resourceId: record.id,
      viewAllowed: Boolean(policies.value[record.id]?.viewAllowed),
      downloadAllowed: Boolean(policies.value[record.id]?.downloadAllowed),
      aiUseAllowed: Boolean(policies.value[record.id]?.aiUseAllowed)
    })))
    ElMessage.success('内部访问权限已保存；关闭权限会撤销对应有效授权')
    await loadRecords()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存内部访问权限失败')
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
    if (recordType.value === 'papers') {
      if (!isValidImpactFactor(form.journalImpactFactor)) throw new Error('影响因子必须为非负有限数且最多 6 位小数')
      if (!isValidMetricYear(form.jcrDataYear)) throw new Error('JCR 数据年度不合法')
    }
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
    ElMessage.error(error.response?.data?.message || error.message || '保存失败')
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
    ElMessage.success(
      '附件已加密保存；查看默认开启，下载和 AI 使用申请默认关闭，可在“我的附件权限”中调整。')
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
  const ratingLabels = {
    ccfRank: 'CCF 等级',
    jcrQuartile: 'JCR 分区',
    journalImpactFactor: '影响因子'
  }
  return config.value.fields.find((field) => field.key === key)?.label || ratingLabels[key] || key
}

function fieldValue(row, key) {
  const field = config.value.fields.find((item) => item.key === key)
  if (key === 'venueType') return { JOURNAL: '期刊', CONFERENCE: '会议', UNKNOWN: '待确认' }[row[key]] || '待确认'
  return field?.options?.find((option) => option.value === row[key])?.label ?? row[key]
}

watch(recordType, () => {
  resetForm()
  attachments.value = []
  loadRecords()
})

watch(() => [recordType.value, form.publicationName, form.venueType], () => {
  if (form.ccfReferenceId && (
    (selectedVenueName.value && form.publicationName !== selectedVenueName.value)
    || shouldClearCcfSelection(selectedVenueType.value, form.venueType)
  )) {
    form.ccfReferenceId = null
    form.ccfConfirmed = false
    form.venueAbbreviation = ''
    selectedVenueName.value = ''
    selectedVenueType.value = ''
  }
  clearTimeout(venueSearchTimer)
  venueSearchTimer = setTimeout(loadVenueMatches, 350)
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
      <div>
        <el-button v-if="recordType === 'papers'" @click="openBulkPreview()">历史论文智能补全</el-button>
        <el-button @click="savePolicies">保存内部权限</el-button>
        <el-button type="primary" @click="openCreateDrawer">新增</el-button>
      </div>
    </header>

    <el-table class="achievement-table" v-loading="loading" :data="records" border>
      <el-table-column prop="sortOrder" label="排序" width="90" />
      <el-table-column
        v-for="column in config.columns"
        :key="column"
        :label="fieldLabel(column)"
        min-width="160"
      >
        <template #default="{ row }">{{ fieldValue(row, column) }}</template>
      </el-table-column>
      <el-table-column v-if="recordType === 'papers'" label="评级状态" min-width="150">
        <template #default="{ row }">
          <el-tag v-if="row.ccfConfirmedAt" type="success">
            {{ row.ccfSource === 'MANUAL_USER_ENTRY' ? 'CCF 人工填写' : 'CCF 用户确认' }}
          </el-tag>
          <el-tag v-if="row.metricStatus === 'MANUAL'" type="warning">JCR 人工填写</el-tag>
          <el-tag v-else-if="row.metricStatus === 'USER_CONFIRMED'" type="success">非官方参考已确认</el-tag>
          <el-tag v-else-if="row.metricStatus === 'SUGGESTED'" type="info">非官方参考待确认</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="公开" width="90">
        <template #default="{ row }">
          <el-tag :type="row.isPublic ? 'success' : 'info'">
            {{ row.isPublic ? '公开' : '隐藏' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="内部访问" min-width="250">
        <template #default="{ row }">
          <div v-if="policies[row.id]" class="policy-switches">
            <el-switch v-model="policies[row.id].viewAllowed" active-text="查看" />
            <el-switch v-model="policies[row.id].downloadAllowed" active-text="可申请下载" />
            <el-switch v-model="policies[row.id].aiUseAllowed" active-text="可申请 AI" />
          </div>
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
        <template v-for="field in config.fields" :key="field.key">
        <el-form-item
          v-if="!field.visibleWhen || field.visibleWhen(form)"
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
          <el-select
            v-else-if="field.type === 'select'"
            v-model="form[field.key]"
            placeholder="请选择"
          >
            <el-option
              v-for="option in field.options"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-input
            v-else-if="field.type === 'textarea'"
            v-model="form[field.key]"
            type="textarea"
            :rows="field.rows ?? 4"
          />
          <el-input v-else v-model="form[field.key]" />
        </el-form-item>
        </template>
        <section v-if="recordType === 'papers'" class="rating-panel">
          <el-alert
            title="CCF 数据来自甲方指定的 2026 年第三方整理快照，不是 CCF 官网实时接口。"
            type="info"
            :closable="false"
            show-icon
          />
          <div v-loading="venueSearching" class="venue-matches">
            <div v-for="match in venueMatches" :key="match.referenceId" class="venue-match">
              <div>
                <strong>{{ match.standardName }}</strong>
                <span>{{ match.abbreviation || '无简称' }} · CCF {{ match.ccfRank }} · {{ match.categoryName }}</span>
              </div>
              <el-button size="small" :type="form.ccfReferenceId === match.referenceId ? 'success' : 'primary'" @click="selectVenueMatch(match)">
                {{ form.ccfReferenceId === match.referenceId ? '已选择并确认' : (match.confirmationRequired ? '选择并确认' : '采用匹配') }}
              </el-button>
            </div>
            <p v-if="form.publicationName && !venueSearching && !venueMatches.length">
              {{ venueSearchMessage || '未找到 CCF 匹配，可继续保存基本信息或手工填写。' }}
            </p>
          </div>
          <el-collapse>
            <el-collapse-item title="未找到：手工填写 CCF 快照" name="manual-ccf">
              <div class="manual-ccf-grid">
                <el-select v-model="form.manualCcfRank" clearable placeholder="CCF 等级">
                  <el-option v-for="rank in ['A', 'B', 'C']" :key="rank" :label="rank" :value="rank" />
                </el-select>
                <el-input-number v-model="form.manualCcfCategoryId" :min="0" :max="999" placeholder="领域编号" />
                <el-input v-model="form.manualCcfCategoryName" maxlength="255" placeholder="领域名称" />
                <el-checkbox v-model="form.ccfConfirmed">我已核实以上手工 CCF 信息</el-checkbox>
              </div>
            </el-collapse-item>
          </el-collapse>
          <template v-if="form.venueType === 'JOURNAL'">
            <el-alert
              title="JCR 分区和影响因子由用户手工填写并自行核实；SJR、中科院分区不等同于 JCR。"
              type="warning"
              :closable="false"
              show-icon
            />
            <el-button class="metric-suggestion-button" @click="loadMetricSuggestions">查询非官方参考建议</el-button>
            <div v-for="suggestion in metricSuggestions" :key="suggestion.referenceId" class="venue-match">
              <div>
                <strong>{{ suggestion.journalName }}</strong>
                <span>{{ suggestion.dataYear }} · {{ suggestion.jcrQuartile }} · IF {{ suggestion.impactFactor }} · 非官方整理</span>
              </div>
              <el-button size="small" @click="selectMetricSuggestion(suggestion)">采用为待确认值</el-button>
            </div>
            <el-checkbox v-if="form.journalMetricReferenceId" v-model="form.metricConfirmed">
              我已结合当年度权威数据核实该非官方建议
            </el-checkbox>
          </template>
        </section>
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

    <el-dialog v-model="bulkDialogVisible" title="历史论文 CCF 智能补全" width="860px">
      <el-alert title="扫描只生成建议；只有明确选择并确认的论文才会更新，已有确认结果默认不会覆盖。" type="warning" :closable="false" />
      <el-table :data="bulkPreview.items" border class="bulk-preview-table">
        <el-table-column prop="title" label="论文" min-width="180" />
        <el-table-column prop="venueName" label="期刊/会议" min-width="180" />
        <el-table-column prop="status" label="状态" width="130" />
        <el-table-column label="候选" min-width="260">
          <template #default="{ row }">
            <el-select
              v-if="row.candidates.length"
              :model-value="findBulkSelectedReference(bulkSelection, row.paperId)"
              placeholder="选择并确认候选"
              @change="(value) => chooseBulkCandidate(row, value)"
            >
              <el-option
                v-for="candidate in row.candidates"
                :key="candidate.referenceId"
                :value="candidate.referenceId"
                :label="`${candidate.standardName} · CCF ${candidate.ccfRank}`"
              />
            </el-select>
            <span v-else>无候选</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="bulkPreview.page"
        :page-size="bulkPreview.size"
        :total="bulkPreview.total"
        layout="prev, pager, next, total"
        @current-change="openBulkPreview"
      />
      <template #footer>
        <el-button @click="bulkDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitBulkConfirmation">确认所选结果</el-button>
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
.policy-switches { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; }
.rating-panel { display: grid; gap: 12px; margin: 12px 0 20px; }
.venue-matches { display: grid; gap: 8px; min-height: 24px; }
.venue-match { display: flex; justify-content: space-between; gap: 12px; padding: 10px; border: 1px solid #e5e7eb; border-radius: 6px; }
.venue-match span { display: block; margin-top: 4px; color: #6b7280; font-size: 13px; }
.metric-suggestion-button { justify-self: start; }
.manual-ccf-grid { display: grid; grid-template-columns: 130px 130px 1fr; gap: 10px; align-items: center; }
.manual-ccf-grid .el-checkbox { grid-column: 1 / -1; }
.bulk-preview-table { margin: 14px 0; }

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
