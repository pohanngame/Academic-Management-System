<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'

import {
  changeAdminUserRole,
  changeAdminUserStatus,
  deleteStagedRestore,
  exportSystemBackup,
  getAdminAuditLogs,
  getAdminOverview,
  getCcfReferenceStatus,
  getStagedRestores,
  getAdminUsers,
  importCcfReferenceData,
  stageRestorePackage
} from '../../services/admin'
import { canSubmitCcfImport, sanitizeCcfImportError } from './ccfReferenceAdminRules'

const loading = ref(false)
const overview = ref(null)
const users = ref({ items: [], page: 1, size: 20, total: 0 })
const audits = ref({ items: [], page: 1, size: 20, total: 0 })
const filters = reactive({ search: '', role: '', status: '', page: 1, size: 20 })
const backupPassword = ref('')
const backupRunning = ref(false)
const restoreFile = ref(null)
const restoreFileInput = ref(null)
const restorePassword = ref('')
const restoreUploading = ref(false)
const stagedRestores = ref([])
const defaultRestoreLimit = 512 * 1024 * 1024
const ccfStatus = ref(null)
const ccfFile = ref(null)
const ccfFileInput = ref(null)
const ccfPassword = ref('')
const ccfImporting = ref(false)

async function loadOverview() {
  overview.value = await getAdminOverview()
}

async function loadUsers() {
  loading.value = true
  try {
    users.value = await getAdminUsers(filters)
  } finally {
    loading.value = false
  }
}

async function loadAudits(page = 1) {
  audits.value = await getAdminAuditLogs({ page, size: 20 })
}

async function loadStagedRestores() {
  stagedRestores.value = await getStagedRestores()
}

async function loadCcfStatus() {
  ccfStatus.value = await getCcfReferenceStatus()
}

function chooseCcfFile(event) {
  const [file] = event.target.files || []
  ccfFile.value = file || null
}

async function uploadCcfReference() {
  if (!canSubmitCcfImport(ccfFile.value, ccfPassword.value, ccfImporting.value)) {
    ElMessage.warning('请选择非空且不超过 5 MiB 的 JSON，并输入当前密码')
    return
  }
  try {
    await ElMessageBox.confirm(
      '请仅上传您有权使用的数据文件。系统不会自动从外部网站下载数据。确认继续？',
      '确认导入 CCF 参考数据',
      { type: 'warning', confirmButtonText: '上传并验证' }
    )
    ccfImporting.value = true
    const result = await importCcfReferenceData(ccfFile.value, ccfPassword.value)
    await Promise.all([loadCcfStatus(), loadAudits()])
    ElMessage.success(result.imported ? `已导入 ${result.journalCount + result.conferenceCount} 条参考数据` : '相同版本已存在，无需重复导入')
    ccfPassword.value = ''
    ccfFile.value = null
    if (ccfFileInput.value) ccfFileInput.value.value = ''
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(sanitizeCcfImportError(error.response?.data?.message))
    }
  } finally {
    ccfPassword.value = ''
    ccfImporting.value = false
  }
}

async function updateRole(user, role) {
  if (user.role === role) return
  try {
    await ElMessageBox.confirm(
      `确认将 ${user.username} 的角色改为 ${role}？现有登录令牌将立即失效。`,
      '确认角色变更',
      { type: 'warning' }
    )
    await changeAdminUserRole(user.id, role)
    ElMessage.success('角色已更新')
    await Promise.all([loadUsers(), loadOverview(), loadAudits()])
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error.response?.data?.message || '角色更新失败')
  }
}

async function updateStatus(user, status) {
  if (user.status === status) return
  try {
    await ElMessageBox.confirm(
      `确认将 ${user.username} 的状态改为 ${status}？禁用后其旧令牌立即失效。`,
      '确认状态变更',
      { type: 'warning' }
    )
    await changeAdminUserStatus(user.id, status)
    ElMessage.success('状态已更新')
    await Promise.all([loadUsers(), loadOverview(), loadAudits()])
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error.response?.data?.message || '状态更新失败')
  }
}

function filenameFromDisposition(disposition) {
  const fallback = 'academic-profile-backup.apbackup'
  const extended = disposition?.match(/(?:^|;)\s*filename\*\s*=\s*UTF-8''([^;]+)/i)
  const regular = disposition?.match(/(?:^|;)\s*filename\s*=\s*"?([^";]+)"?/i)
  try {
    const candidate = (extended ? decodeURIComponent(extended[1]) : regular?.[1])
      ?.trim()
      .replace(/^["']|["']$/g, '')
    return candidate && /^[A-Za-z0-9._-]+\.apbackup$/i.test(candidate) ? candidate : fallback
  } catch {
    return fallback
  }
}

async function createBackup() {
  if (!backupPassword.value) {
    ElMessage.warning('请输入当前超级管理员密码')
    return
  }
  try {
    await ElMessageBox.confirm(
      '完整备份包含全体用户数据、私人附件及 AI 配置解密主密钥。确认创建并下载？',
      '高敏感数据确认',
      { type: 'warning', confirmButtonText: '确认创建' }
    )
    backupRunning.value = true
    const response = await exportSystemBackup(backupPassword.value)
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = filenameFromDisposition(response.headers['content-disposition'])
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
    backupPassword.value = ''
    ElMessage.success('备份已创建，请立即移至受控位置')
    await Promise.all([loadOverview(), loadAudits()])
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('备份创建失败，请检查当前密码和服务器状态')
  } finally {
    backupPassword.value = ''
    backupRunning.value = false
  }
}

function chooseRestoreFile(event) {
  const [file] = event.target.files || []
  restoreFile.value = file || null
}

function restoreCommand(id) {
  return `bash scripts/restore-staged-backup.sh --id ${id}`
}

function formatBytes(size) {
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KiB`
  return `${(size / 1024 / 1024).toFixed(2)} MiB`
}

async function uploadRestorePackage() {
  const file = restoreFile.value
  if (!file) {
    ElMessage.warning('请选择 .apbackup 文件')
    return
  }
  if (!file.name.toLowerCase().endsWith('.apbackup')) {
    ElMessage.warning('文件扩展名必须为 .apbackup')
    return
  }
  if (file.size === 0 || file.size > defaultRestoreLimit) {
    ElMessage.warning('恢复包必须非空且不超过 512 MiB；服务端配置可能更严格')
    return
  }
  if (!restorePassword.value) {
    ElMessage.warning('请输入当前超级管理员密码')
    return
  }
  try {
    await ElMessageBox.confirm(
      '上传只会暂存恢复包，不会执行恢复。请确认文件来自可信系统和受控传输渠道。',
      '确认上传高敏感恢复包',
      { type: 'warning', confirmButtonText: '上传恢复包' }
    )
    restoreUploading.value = true
    await stageRestorePackage(file, restorePassword.value)
    restorePassword.value = ''
    restoreFile.value = null
    if (restoreFileInput.value) restoreFileInput.value.value = ''
    await Promise.all([loadStagedRestores(), loadAudits()])
    ElMessage.success('恢复包已暂存，请由服务器运维人员执行显示的命令')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '恢复包上传失败')
    }
  } finally {
    restorePassword.value = ''
    restoreUploading.value = false
  }
}

async function copyRestoreCommand(id) {
  try {
    await navigator.clipboard.writeText(restoreCommand(id))
    ElMessage.success('恢复命令已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择命令文本')
  }
}

async function removeStagedRestore(item) {
  try {
    const { value: currentPassword } = await ElMessageBox.prompt(
      `删除暂存任务 ${item.id}？此操作不会恢复任何数据。`,
      '删除暂存包',
      {
        type: 'warning',
        inputType: 'password',
        inputPlaceholder: '重新输入当前超级管理员密码',
        inputValidator: value => Boolean(value) || '请输入当前密码',
        confirmButtonText: '删除暂存包'
      }
    )
    await deleteStagedRestore(item.id, currentPassword)
    await Promise.all([loadStagedRestores(), loadAudits()])
    ElMessage.success('暂存包已删除')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '暂存包删除失败')
    }
  }
}

onMounted(async () => {
  try {
    await Promise.all([loadOverview(), loadUsers(), loadAudits(), loadStagedRestores(), loadCcfStatus()])
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '管理员数据加载失败')
  }
})
</script>

<template>
  <div class="admin-page">
    <h1>系统管理</h1>
    <el-alert
      title="完整备份包含全体用户数据、私人附件及 AI 配置解密所需主密钥，请仅保存到受控位置。"
      type="warning"
      :closable="false"
      show-icon
    />

    <el-tabs class="admin-tabs">
      <el-tab-pane label="系统概览">
        <el-row v-if="overview" :gutter="16">
          <el-col v-for="item in [
            ['用户总数', overview.totalUsers],
            ['教师', overview.teacherUsers],
            ['超级管理员', overview.superAdminUsers],
            ['活动账户', overview.activeUsers],
            ['禁用账户', overview.disabledUsers],
            ['上传文件', overview.uploadFiles]
          ]" :key="item[0]" :span="8">
            <el-card class="metric"><span>{{ item[0] }}</span><strong>{{ item[1] }}</strong></el-card>
          </el-col>
        </el-row>
        <el-card v-if="overview" class="summary-card">
          上传存储占用：{{ (overview.uploadBytes / 1024 / 1024).toFixed(2) }} MB；
          最近备份：{{ overview.latestBackup?.summary || '暂无记录' }}
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="用户管理">
        <el-form inline @submit.prevent="filters.page = 1; loadUsers()">
          <el-form-item><el-input v-model="filters.search" placeholder="用户名、邮箱或显示名称" clearable /></el-form-item>
          <el-form-item>
            <el-select v-model="filters.role" placeholder="全部角色" clearable style="width: 150px">
              <el-option label="TEACHER" value="TEACHER" />
              <el-option label="SUPER_ADMIN" value="SUPER_ADMIN" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 140px">
              <el-option label="ACTIVE" value="ACTIVE" />
              <el-option label="DISABLED" value="DISABLED" />
            </el-select>
          </el-form-item>
          <el-button type="primary" native-type="submit">查询</el-button>
        </el-form>
        <el-table :data="users.items" v-loading="loading">
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="email" label="邮箱" min-width="190" />
          <el-table-column prop="displayName" label="显示名称" />
          <el-table-column label="角色" width="170">
            <template #default="{ row }">
              <el-select :model-value="row.role" @change="updateRole(row, $event)">
                <el-option label="TEACHER" value="TEACHER" />
                <el-option label="SUPER_ADMIN" value="SUPER_ADMIN" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="140">
            <template #default="{ row }">
              <el-select :model-value="row.status" @change="updateStatus(row, $event)">
                <el-option label="ACTIVE" value="ACTIVE" />
                <el-option label="DISABLED" value="DISABLED" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="注册时间" min-width="170" />
        </el-table>
        <el-pagination
          v-model:current-page="filters.page"
          :page-size="filters.size"
          :total="users.total"
          layout="prev, pager, next, total"
          @current-change="loadUsers"
        />
      </el-tab-pane>

      <el-tab-pane label="数据备份">
        <el-card class="backup-card">
          <p>迁移包未额外加密。下载必须通过 HTTPS，并在完成后从普通下载目录移走。</p>
          <el-alert
            title="后端会流式输出备份，不会一次性载入 JVM；当前浏览器端使用 Blob，接近大小上限的备份可能占用较多浏览器内存。"
            type="info"
            :closable="false"
            show-icon
            class="backup-memory-warning"
          />
          <el-input
            v-model="backupPassword"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="重新输入当前超级管理员密码"
            style="max-width: 420px"
          />
          <el-button type="danger" :loading="backupRunning" @click="createBackup">创建并下载完整备份</el-button>
        </el-card>

        <el-card class="backup-card">
          <template #header><strong>备份恢复助手</strong></template>
          <el-alert
            title="上传不会立即恢复。完整恢复仍须由拥有服务器和 Docker 权限的运维人员在项目根目录执行本地脚本。"
            type="warning"
            :closable="false"
            show-icon
          />
          <ul class="restore-warnings">
            <li>SHA-256 只能发现损坏，不能证明来源；只允许使用可信来源的备份。</li>
            <li>恢复会替换用户、数据库、附件和 AI 主密钥，恢复期间页面可能短暂不可用。</li>
            <li>非空目标仍须向脚本传入 <code>--force</code>，并完成原脚本的人工确认。</li>
          </ul>
          <div class="restore-upload">
            <input
              ref="restoreFileInput"
              type="file"
              accept=".apbackup"
              :disabled="restoreUploading"
              @change="chooseRestoreFile"
            />
            <el-input
              v-model="restorePassword"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder="重新输入当前超级管理员密码"
              :disabled="restoreUploading"
            />
            <el-button
              type="danger"
              :loading="restoreUploading"
              :disabled="restoreUploading"
              @click="uploadRestorePackage"
            >
              上传恢复包
            </el-button>
          </div>

          <el-empty v-if="stagedRestores.length === 0" description="暂无已暂存的恢复包" />
          <el-table v-else :data="stagedRestores" class="restore-table">
            <el-table-column prop="id" label="任务 ID" min-width="290" />
            <el-table-column label="大小" width="120">
              <template #default="{ row }">{{ formatBytes(row.size) }}</template>
            </el-table-column>
            <el-table-column prop="sha256" label="SHA-256" min-width="300" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="上传时间" min-width="190" />
            <el-table-column prop="expiresAt" label="自动过期时间" min-width="190" />
            <el-table-column label="状态" width="180">
              <template #default>已暂存，等待服务器执行</template>
            </el-table-column>
            <el-table-column label="操作" fixed="right" width="220">
              <template #default="{ row }">
                <el-button type="primary" link @click="copyRestoreCommand(row.id)">复制恢复命令</el-button>
                <el-button type="danger" link @click="removeStagedRestore(row)">删除暂存包</el-button>
              </template>
            </el-table-column>
            <el-table-column label="服务器命令" min-width="520">
              <template #default="{ row }"><code>{{ restoreCommand(row.id) }}</code></template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="期刊/会议参考数据">
        <el-card class="backup-card">
          <template #header><strong>CCF 参考数据</strong></template>
          <el-alert
            title="系统不会自动下载 CCF 数据。请仅上传您有权使用的数据文件；第三方整理数据不是 CCF 官方实时接口。"
            type="warning"
            :closable="false"
            show-icon
          />
          <el-descriptions v-if="ccfStatus?.configured" :column="2" border class="ccf-status">
            <el-descriptions-item label="数据名称">{{ ccfStatus.dataName }}</el-descriptions-item>
            <el-descriptions-item label="数据年度">{{ ccfStatus.dataYear }}</el-descriptions-item>
            <el-descriptions-item label="领域/期刊/会议">
              {{ ccfStatus.categoryCount }} / {{ ccfStatus.journalCount }} / {{ ccfStatus.conferenceCount }}
            </el-descriptions-item>
            <el-descriptions-item label="导入管理员">用户 #{{ ccfStatus.importedByUserId || '未知' }}</el-descriptions-item>
            <el-descriptions-item label="导入时间">{{ ccfStatus.importedAt }}</el-descriptions-item>
            <el-descriptions-item label="SHA-256"><code>{{ ccfStatus.sha256 }}</code></el-descriptions-item>
            <el-descriptions-item label="来源" :span="2">{{ ccfStatus.source }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="管理员尚未配置 CCF 参考数据" />
          <div class="restore-upload">
            <input ref="ccfFileInput" type="file" accept=".json,application/json" :disabled="ccfImporting" @change="chooseCcfFile" />
            <el-input
              v-model="ccfPassword"
              type="password"
              autocomplete="current-password"
              placeholder="重新输入当前超级管理员密码"
              :disabled="ccfImporting"
            />
            <el-button type="primary" :loading="ccfImporting" :disabled="ccfImporting" @click="uploadCcfReference">
              上传并验证
            </el-button>
          </div>
          <p>导入新版本只替换自动匹配参考库；教师已经确认的论文历史快照不会改变。</p>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="管理审计日志">
        <el-table :data="audits.items">
          <el-table-column prop="createdAt" label="时间" min-width="170" />
          <el-table-column prop="operatorUserId" label="操作者 ID" />
          <el-table-column prop="actionType" label="动作" min-width="180" />
          <el-table-column prop="targetUserId" label="目标用户 ID" />
          <el-table-column prop="success" label="结果">
            <template #default="{ row }">{{ row.success ? '成功' : '失败' }}</template>
          </el-table-column>
          <el-table-column prop="summary" label="摘要" min-width="260" />
        </el-table>
        <el-pagination
          :current-page="audits.page"
          :page-size="audits.size"
          :total="audits.total"
          layout="prev, pager, next, total"
          @current-change="loadAudits"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.admin-page { max-width: 1200px; margin: 0 auto; }
.admin-tabs { margin-top: 18px; }
.metric { margin-bottom: 16px; }
.metric :deep(.el-card__body) { display: flex; justify-content: space-between; align-items: center; }
.metric strong { font-size: 26px; }
.summary-card, .el-pagination { margin-top: 16px; }
.backup-memory-warning { margin: 12px 0; }
.backup-card + .backup-card { margin-top: 18px; }
.restore-warnings { line-height: 1.8; }
.restore-upload { display: grid; grid-template-columns: minmax(260px, 1fr) minmax(280px, 420px) auto; gap: 12px; align-items: center; margin-top: 16px; }
.restore-table { margin-top: 16px; }
.ccf-status { margin-top: 16px; }
.el-button { margin-left: 12px; }
@media (max-width: 900px) {
  .restore-upload { grid-template-columns: 1fr; }
  .restore-upload .el-button { margin-left: 0; }
}
</style>
