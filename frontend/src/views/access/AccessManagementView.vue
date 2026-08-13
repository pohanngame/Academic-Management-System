<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  cancelAccessRequest,
  createAccessRequest,
  decideAccessRequest,
  getAccessCapabilities,
  getAccessGrants,
  getApprovalCandidates,
  getMyAccessRequests,
  getPendingAccessRequests,
  getResourceAudits,
  getResourcePolicies,
  getSharedResources,
  getSharedResource,
  getTeacherDirectory,
  revokeAccessGrant,
  updateResourcePolicies
} from '../../services/access'
import { downloadFile, listSharedFiles, pageOwnedAttachments, previewFile } from '../../services/files'
import { useAuthStore } from '../../stores/auth'
import {
  canBatchRequest,
  canSubmitApproval,
  grantAction,
  isApprovalCandidateSelectable,
  mergeAttachmentsWithPolicies
} from './accessUiRules'

const authStore = useAuthStore()
const directory = ref([])
const mine = ref([])
const pending = ref([])
const grants = ref([])
const history = ref([])
const loading = ref(false)
const requestDialog = ref(false)
const approvalDialog = ref(false)
const approvalRequest = ref(null)
const approvalResources = ref([])
const approvedResourceIds = ref([])
const approvalComment = ref('')
const detailDialog = ref(false)
const sharedDetail = ref(null)
const sharedFiles = ref([])
const selectedGrant = ref(null)
const selectedOwners = ref([])
const submitting = ref(false)
const attachmentPolicies = ref([])
const selectedAttachmentPolicies = ref([])
const capabilities = ref(null)
const managedDirectory = ref([])
const managedResources = ref([])
const managedOwner = ref(null)
const managedDialog = ref(false)
const managedResourceType = ref('PAPER')
const managedResourcePage = ref(1)
const managedResourceTotal = ref(0)
const decisionSubmitting = ref(false)
const pageSize = 20
const pages = reactive({
  directory: 1,
  mine: 1,
  pending: 1,
  grants: 1,
  audits: 1,
  attachments: 1,
  managed: 1
})
const totals = reactive({
  directory: 0,
  mine: 0,
  pending: 0,
  grants: 0,
  audits: 0,
  attachments: 0,
  managed: 0
})
const form = reactive({
  resourceType: 'PAPER',
  permissionType: 'VIEW',
  purpose: '',
  days: 30
})
const resourceTypes = [
  ['PAPER', '论文成果'], ['PATENT', '专利'], ['CERTIFICATE', '证书'],
  ['PROJECT', '科研项目'], ['TEACHING_COURSE', '授课记录'],
  ['ACADEMIC_QUALIFICATION', '学历'], ['TEACHING_AREA', '教学方向'],
  ['RESEARCH_AREA', '研究方向'], ['PROFESSIONAL_SERVICE', '专业服务'],
  ['WORKING_EXPERIENCE', '工作经历']
]

async function load() {
  loading.value = true
  try {
    capabilities.value = await getAccessCapabilities()
    const [directoryPage, minePage, pendingPage, grantPage, auditPage, filesPage, policies, managedPage] = await Promise.all([
      getTeacherDirectory({ page: pages.directory, size: pageSize }),
      getMyAccessRequests({ page: pages.mine, size: pageSize }),
      getPendingAccessRequests({ page: pages.pending, size: pageSize }),
      getAccessGrants({ state: 'ALL', page: pages.grants, size: pageSize }),
      getResourceAudits({ page: pages.audits, size: pageSize }),
      pageOwnedAttachments({ page: pages.attachments, size: pageSize }),
      getResourcePolicies('FILE_ATTACHMENT'),
      capabilities.value.canBrowseManagedScope
        ? getTeacherDirectory({ scope: 'MANAGED', page: pages.managed, size: pageSize })
        : Promise.resolve({ items: [], total: 0 })
    ])
    directory.value = directoryPage.items
    mine.value = minePage.records
    pending.value = pendingPage.records
    grants.value = grantPage.records
    history.value = auditPage.records
    managedDirectory.value = managedPage.items
    attachmentPolicies.value = mergeAttachmentsWithPolicies(filesPage.records, policies)
    totals.directory = directoryPage.total
    totals.mine = minePage.total
    totals.pending = pendingPage.total
    totals.grants = grantPage.total
    totals.audits = auditPage.total
    totals.attachments = filesPage.total
    totals.managed = managedPage.total
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载资料授权信息失败')
  } finally {
    loading.value = false
  }
}

function selectAttachmentPolicies(rows) {
  selectedAttachmentPolicies.value = rows.slice(0, 100)
}

async function saveAttachmentPolicies() {
  if (selectedAttachmentPolicies.value.length === 0) return
  try {
    await updateResourcePolicies(selectedAttachmentPolicies.value.map(row => ({
      resourceType: 'FILE_ATTACHMENT',
      resourceId: row.id,
      viewAllowed: row.viewAllowed,
      downloadAllowed: row.downloadAllowed,
      aiUseAllowed: row.aiUseAllowed
    })))
    ElMessage.success('附件权限已更新')
    await load()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '更新附件权限失败')
  }
}

function openRequest(teacherId) {
  selectedOwners.value = [teacherId]
  form.purpose = ''
  requestDialog.value = true
}

function selectOwners(rows) {
  if (!canBatchRequest(capabilities.value)) return
  selectedOwners.value = rows.slice(0, 50).map(row => row.teacherId)
}

function openBatchRequest() {
  if (!canBatchRequest(capabilities.value) || selectedOwners.value.length === 0) return
  form.purpose = ''
  requestDialog.value = true
}

async function submitRequest() {
  if (submitting.value || selectedOwners.value.length === 0) return
  submitting.value = true
  const expires = new Date(Date.now() + form.days * 86400000).toISOString()
  try {
    const results = []
    for (const ownerTeacherId of selectedOwners.value) {
      const owner = directory.value.find(item => item.teacherId === ownerTeacherId)
      try {
        await createAccessRequest({
          ownerTeacherIds: [ownerTeacherId],
          resourceType: form.resourceType,
          permissionType: form.permissionType,
          purpose: form.purpose,
          requestedExpiresAt: expires,
          resources: []
        })
        results.push(`${owner?.displayName || `教师 #${ownerTeacherId}`}：成功`)
      } catch (error) {
        results.push(`${owner?.displayName || `教师 #${ownerTeacherId}`}：${error.response?.data?.message || '失败'}`)
      }
    }
    requestDialog.value = false
    await load()
    await ElMessageBox.alert(results.join('\n'), '逐位所有人申请结果', {
      confirmButtonText: '知道了',
      customStyle: { whiteSpace: 'pre-line' }
    })
  } finally {
    submitting.value = false
  }
}

async function decide(row, approve) {
  if (decisionSubmitting.value) return
  if (approve) {
    decisionSubmitting.value = true
    try {
      approvalRequest.value = row
      approvalResources.value = await getApprovalCandidates(row.id)
      approvedResourceIds.value = []
      approvalComment.value = ''
      approvalDialog.value = true
    } catch (error) {
      ElMessage.error(error.response?.data?.message || '加载可授权资料失败')
    } finally {
      decisionSubmitting.value = false
    }
    return
  }
  try {
    const comment = await ElMessageBox.prompt(
      '输入拒绝原因',
      '拒绝申请',
      { inputType: 'textarea' }
    )
    decisionSubmitting.value = true
    await decideAccessRequest(row.id, {
      approve: false,
      comment: comment.value,
      approvedResources: []
    })
    ElMessage.success('审批完成')
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error.response?.data?.message || '审批失败')
  } finally {
    decisionSubmitting.value = false
  }
}

async function approveSelected() {
  if (!canSubmitApproval(decisionSubmitting.value, approvedResourceIds.value.length)) return
  decisionSubmitting.value = true
  try {
    await decideAccessRequest(approvalRequest.value.id, {
      approve: true,
      comment: approvalComment.value,
      approvedResources: approvedResourceIds.value.map(key => {
        const [resourceType, resourceId] = key.split(':')
        return { resourceType, resourceId: Number(resourceId) }
      })
    })
    approvalDialog.value = false
    ElMessage.success('所选资料已授权')
    await load()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '批准失败')
  } finally {
    decisionSubmitting.value = false
  }
}

async function openManagedResources(row) {
  try {
    managedOwner.value = row
    managedResourcePage.value = 1
    await loadManagedResources()
    managedDialog.value = true
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '读取职责范围资料失败')
  }
}

async function loadManagedResources() {
  if (!managedOwner.value) return
  try {
    const result = await getSharedResources({
      ownerTeacherId: managedOwner.value.teacherId,
      resourceType: managedResourceType.value,
      page: managedResourcePage.value,
      size: pageSize
    })
    managedResources.value = result.items
    managedResourceTotal.value = result.total
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '读取职责范围资料失败')
  }
}

function changeManagedResourcePage(page) {
  managedResourcePage.value = page
  loadManagedResources()
}

async function openManagedDetail(row) {
  try {
    selectedGrant.value = { permissionType: 'VIEW' }
    sharedDetail.value = await getSharedResource(managedResourceType.value, row.id)
    const businessTypes = { PAPER: 'PAPER', PATENT: 'PATENT', CERTIFICATE: 'CERTIFICATE' }
    sharedFiles.value = businessTypes[managedResourceType.value]
      ? await listSharedFiles(businessTypes[managedResourceType.value], row.id, 'VIEW')
      : []
    detailDialog.value = true
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '读取职责范围资料失败')
  }
}

function changePage(key, page) {
  pages[key] = page
  load()
}

function operationLabel(value) {
  return {
    LIST: '列表展示', DETAIL: '详情查看', PREVIEW: '附件预览',
    DOWNLOAD: '附件下载', AI_USE: 'AI 使用', REQUEST_CREATE: '创建申请',
    REQUEST_APPROVE: '批准申请', REQUEST_REJECT: '拒绝申请',
    REQUEST_CANCEL: '取消申请', GRANT_REVOKE: '撤销授权',
    POLICY_UPDATE: '修改权限策略'
  }[value] || value
}

function resourceTypeLabel(value) {
  if (value === 'FILE_ATTACHMENT') return '附件'
  return resourceTypes.find(([type]) => type === value)?.[1] || value
}

function permissionLabel(value) {
  return { VIEW: '查看', DOWNLOAD: '下载', AI_USE: 'AI 使用' }[value] || value
}

function statusLabel(value) {
  return {
    PENDING: '待审批', APPROVED: '已批准', REJECTED: '已拒绝',
    CANCELLED: '已取消', AVAILABLE: '可用', UNAVAILABLE: '已删除或失效'
  }[value] || value
}

function roleLabel(value) {
  const labels = {
    FULL_TIME_TEACHER: '专任教师', MAJOR_LEADER: '专业负责人',
    VICE_DEAN: '副院长', DEAN: '院长', PARTY_SECRETARY: '书记'
  }
  return (value || '').split(',').filter(Boolean).map(role => labels[role] || role).join('、')
}

function formatTime(value) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'
}

async function cancel(row) {
  await ElMessageBox.confirm('确认取消该申请？', '确认', { type: 'warning' })
  await cancelAccessRequest(row.id)
  await load()
}

async function revoke(row) {
  await ElMessageBox.confirm(
    `仅撤销“${row.resourceName || `资料 #${row.resourceId}`}”的本条授权；同一教师的其他资料授权不受影响。确认继续？`,
    '撤销单项授权',
    { type: 'warning' }
  )
  await revokeAccessGrant(row.id)
  await load()
  ElMessage.success('本条资料授权已撤销；其他资料授权保持不变')
}

async function openGranted(row) {
  try {
    selectedGrant.value = row
    if (row.resourceType === 'FILE_ATTACHMENT') {
      sharedDetail.value = {
        resourceId: row.resourceId,
        permission: row.permissionType,
        purpose: row.purpose
      }
      sharedFiles.value = [{
        id: row.resourceId,
        originalName: row.resourceName || `附件 #${row.resourceId}`
      }]
      detailDialog.value = true
      return
    }
    sharedDetail.value = row.permissionType === 'VIEW'
      ? await getSharedResource(row.resourceType, row.resourceId)
      : { resourceId: row.resourceId, permission: row.permissionType, purpose: row.purpose }
    const businessTypes = { PAPER: 'PAPER', PATENT: 'PATENT', CERTIFICATE: 'CERTIFICATE' }
    sharedFiles.value = businessTypes[row.resourceType]
      ? await listSharedFiles(businessTypes[row.resourceType], row.resourceId, row.permissionType)
      : []
    detailDialog.value = true
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '读取授权资料失败')
  }
}

onMounted(load)
</script>

<template>
  <section class="page-panel" v-loading="loading">
    <header>
      <h2>资料访问与授权</h2>
      <p>查看、下载和 AI 使用是独立权限。在线预览属于权限与审计控制，并不能阻止截图或人工保存。</p>
      <p v-if="capabilities">
        当前业务角色：{{ roleLabel(capabilities.businessRoles.join(',')) || '专任教师' }}
        <span v-if="capabilities.currentMajorName">；所属专业：{{ capabilities.currentMajorName }}</span>
      </p>
    </header>
    <el-tabs>
      <el-tab-pane label="可申请人员">
        <el-button
          v-if="canBatchRequest(capabilities)"
          type="primary"
          :disabled="selectedOwners.length === 0"
          @click="openBatchRequest"
        >批量申请（{{ selectedOwners.length }}）</el-button>
        <el-table :data="directory" @selection-change="selectOwners">
          <el-table-column v-if="canBatchRequest(capabilities)" type="selection" width="48" />
          <el-table-column prop="displayName" label="教师" />
          <el-table-column prop="majorName" label="专业" />
          <el-table-column label="业务角色" min-width="180"><template #default="{ row }">{{ roleLabel(row.roleCodes) }}</template></el-table-column>
          <el-table-column prop="title" label="职称" />
          <el-table-column label="操作">
            <template #default="{ row }"><el-button link type="primary" @click="openRequest(row.teacherId)">申请权限</el-button></template>
          </el-table-column>
        </el-table>
        <el-pagination
          layout="prev, pager, next, total"
          :current-page="pages.directory"
          :page-size="pageSize"
          :total="totals.directory"
          @current-change="page => changePage('directory', page)"
        />
      </el-tab-pane>
      <el-tab-pane v-if="capabilities?.canBrowseManagedScope" label="职责范围资料">
        <el-form inline>
          <el-form-item label="资料类别">
            <el-select v-model="managedResourceType" style="width: 180px">
              <el-option
                v-for="[value, label] in resourceTypes"
                :key="value"
                :value="value"
                :label="label"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <el-table :data="managedDirectory">
          <el-table-column prop="displayName" label="教师" />
          <el-table-column prop="majorName" label="专业" />
          <el-table-column label="业务角色" min-width="180"><template #default="{ row }">{{ roleLabel(row.roleCodes) }}</template></el-table-column>
          <el-table-column prop="title" label="职称" />
          <el-table-column label="操作">
            <template #default="{ row }">
              <el-button link type="primary" @click="openManagedResources(row)">查看允许资料</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          layout="prev, pager, next, total"
          :current-page="pages.managed"
          :page-size="pageSize"
          :total="totals.managed"
          @current-change="page => changePage('managed', page)"
        />
      </el-tab-pane>
      <el-tab-pane label="我的申请">
        <el-table :data="mine">
          <el-table-column label="资料类别"><template #default="{ row }">{{ resourceTypeLabel(row.resourceType) }}</template></el-table-column>
          <el-table-column label="权限"><template #default="{ row }">{{ permissionLabel(row.permissionType) }}</template></el-table-column>
          <el-table-column label="状态"><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column>
          <el-table-column prop="requestedExpiresAt" label="希望到期时间" min-width="180" />
          <el-table-column label="操作"><template #default="{ row }"><el-button v-if="row.status === 'PENDING'" link type="danger" @click="cancel(row)">取消</el-button></template></el-table-column>
        </el-table>
        <el-pagination layout="prev, pager, next, total" :current-page="pages.mine" :page-size="pageSize" :total="totals.mine" @current-change="page => changePage('mine', page)" />
      </el-tab-pane>
      <el-tab-pane label="待我审批">
        <el-table :data="pending">
          <el-table-column prop="requesterTeacherId" label="申请教师 ID" />
          <el-table-column label="资料类别"><template #default="{ row }">{{ resourceTypeLabel(row.resourceType) }}</template></el-table-column>
          <el-table-column label="权限"><template #default="{ row }">{{ permissionLabel(row.permissionType) }}</template></el-table-column>
          <el-table-column prop="purpose" label="用途" min-width="220" />
          <el-table-column label="操作" width="150"><template #default="{ row }"><el-button link type="success" :loading="decisionSubmitting" :disabled="decisionSubmitting" @click="decide(row, true)">批准</el-button><el-button link type="danger" :disabled="decisionSubmitting" @click="decide(row, false)">拒绝</el-button></template></el-table-column>
        </el-table>
        <el-pagination layout="prev, pager, next, total" :current-page="pages.pending" :page-size="pageSize" :total="totals.pending" @current-change="page => changePage('pending', page)" />
      </el-tab-pane>
      <el-tab-pane label="授权记录">
        <el-table :data="grants">
          <el-table-column label="权限"><template #default="{ row }">{{ permissionLabel(row.permissionType) }}</template></el-table-column>
          <el-table-column label="资料类别"><template #default="{ row }">{{ resourceTypeLabel(row.resourceType) }}</template></el-table-column>
          <el-table-column prop="resourceName" label="资料/附件" min-width="220" />
          <el-table-column label="资源状态"><template #default="{ row }">{{ statusLabel(row.resourceStatus) }}</template></el-table-column>
          <el-table-column prop="expiresAt" label="到期时间" />
          <el-table-column label="操作" min-width="180"><template #default="{ row }"><el-button v-if="grantAction(row)" link type="primary" :disabled="row.resourceStatus !== 'AVAILABLE'" @click="openGranted(row)">{{ grantAction(row) === 'DOWNLOAD' ? '下载' : '预览' }}</el-button><el-button v-if="!row.revokedAt && row.ownerTeacherId === authStore.user?.teacherId" link type="danger" @click="revoke(row)">提前撤销</el-button></template></el-table-column>
        </el-table>
        <el-pagination layout="prev, pager, next, total" :current-page="pages.grants" :page-size="pageSize" :total="totals.grants" @current-change="page => changePage('grants', page)" />
      </el-tab-pane>
      <el-tab-pane label="我的资料访问记录">
        <el-table :data="history">
          <el-table-column label="时间" min-width="180"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
          <el-table-column label="操作"><template #default="{ row }">{{ operationLabel(row.operationType) }}</template></el-table-column>
          <el-table-column prop="actorDisplayName" label="使用人" />
          <el-table-column label="类别"><template #default="{ row }">{{ resourceTypeLabel(row.resourceType) }}</template></el-table-column>
          <el-table-column prop="resourceName" label="资料/附件" min-width="220" />
          <el-table-column prop="purpose" label="用途" min-width="180" />
          <el-table-column label="结果"><template #default="{ row }">{{ row.success ? '成功' : '失败' }}</template></el-table-column>
        </el-table>
        <el-pagination layout="prev, pager, next, total" :current-page="pages.audits" :page-size="pageSize" :total="totals.audits" @current-change="page => changePage('audits', page)" />
      </el-tab-pane>
      <el-tab-pane label="我的附件权限">
        <el-button type="primary" :disabled="selectedAttachmentPolicies.length === 0" @click="saveAttachmentPolicies">保存所选附件权限</el-button>
        <el-table :data="attachmentPolicies" @selection-change="selectAttachmentPolicies">
          <el-table-column type="selection" width="48" />
          <el-table-column prop="originalName" label="附件" min-width="220" />
          <el-table-column prop="businessType" label="资料类别" />
          <el-table-column label="查看"><template #default="{ row }"><el-switch v-model="row.viewAllowed" /></template></el-table-column>
          <el-table-column label="下载申请"><template #default="{ row }"><el-switch v-model="row.downloadAllowed" /></template></el-table-column>
          <el-table-column label="AI 使用申请"><template #default="{ row }"><el-switch v-model="row.aiUseAllowed" /></template></el-table-column>
        </el-table>
        <el-pagination layout="prev, pager, next, total" :current-page="pages.attachments" :page-size="pageSize" :total="totals.attachments" @current-change="page => changePage('attachments', page)" />
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="requestDialog" title="申请资料权限" width="520px">
      <el-form label-position="top">
        <el-form-item label="资料类别"><el-select v-model="form.resourceType"><el-option v-for="[value, label] in resourceTypes" :key="value" :value="value" :label="label" /></el-select></el-form-item>
        <el-form-item label="申请权限"><el-select v-model="form.permissionType"><el-option label="查看" value="VIEW" /><el-option label="下载" value="DOWNLOAD" /><el-option label="AI 使用" value="AI_USE" /></el-select></el-form-item>
        <el-form-item label="使用目的"><el-input v-model="form.purpose" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="有效期"><el-radio-group v-model="form.days"><el-radio-button v-for="days in [7, 30, 90, 365]" :key="days" :value="days">{{ days }} 天</el-radio-button></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="requestDialog = false">取消</el-button><el-button type="primary" :loading="submitting" :disabled="selectedOwners.length === 0" @click="submitRequest">提交申请</el-button></template>
    </el-dialog>
    <el-dialog v-model="approvalDialog" title="选择批准的具体资料" width="620px">
      <el-checkbox-group v-model="approvedResourceIds">
        <div v-for="resource in approvalResources" :key="`${resource.resourceType}:${resource.resourceId}`" class="resource-option">
          <el-checkbox
            :value="`${resource.resourceType}:${resource.resourceId}`"
            :disabled="!isApprovalCandidateSelectable(resource)"
          >
            {{ resource.displayName }}
            <span v-if="!resource.allowed">（{{ resource.unavailableReason }}）</span>
          </el-checkbox>
        </div>
      </el-checkbox-group>
      <el-input v-model="approvalComment" type="textarea" :rows="3" placeholder="审批意见（可选）" />
      <template #footer><el-button @click="approvalDialog = false">取消</el-button><el-button type="primary" :loading="decisionSubmitting" :disabled="!canSubmitApproval(decisionSubmitting, approvedResourceIds.length)" @click="approveSelected">批准所选资料</el-button></template>
    </el-dialog>
    <el-dialog v-model="managedDialog" :title="`${managedOwner?.displayName || ''}的职责范围资料`" width="720px">
      <el-table :data="managedResources">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column label="资料名称" min-width="260">
          <template #default="{ row }">{{ row.title || row.projectName || row.certificateName || row.patentName || row.courseName || row.name || row.institution || row.organization || `资料 #${row.id}` }}</template>
        </el-table-column>
        <el-table-column label="操作"><template #default="{ row }"><el-button link type="primary" @click="openManagedDetail(row)">查看详情</el-button></template></el-table-column>
      </el-table>
      <el-pagination
        layout="prev, pager, next, total"
        :current-page="managedResourcePage"
        :page-size="pageSize"
        :total="managedResourceTotal"
        @current-change="changeManagedResourcePage"
      />
      <p>职责范围只提供 viewAllowed=true 资料的查看资格；下载和 AI 使用仍需单独申请并获批。</p>
    </el-dialog>
    <el-dialog v-model="detailDialog" title="授权资料" width="680px">
      <el-descriptions v-if="sharedDetail" border :column="1">
        <el-descriptions-item v-for="(value, key) in sharedDetail" :key="key" :label="key">{{ value }}</el-descriptions-item>
      </el-descriptions>
      <h3>{{ selectedGrant?.permissionType === 'DOWNLOAD' ? '可下载附件' : '附件（inline 预览）' }}</h3>
      <el-button v-for="file in sharedFiles" :key="file.id" @click="selectedGrant?.permissionType === 'DOWNLOAD' ? downloadFile(file) : previewFile(file)">{{ selectedGrant?.permissionType === 'DOWNLOAD' ? '下载' : '预览' }} {{ file.originalName }}</el-button>
      <p>预览内容到达浏览器后无法绝对防止截图、保存或抄录。</p>
    </el-dialog>
  </section>
</template>

<style scoped>
.page-panel { padding: 28px; background: #fff; border: 1px solid #e5e7eb; border-radius: 8px; }
header { margin-bottom: 20px; }
h2 { margin: 0 0 8px; }
p { color: #6b7280; }
.resource-option { margin-bottom: 10px; }
</style>
