<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'

import {
  changeAdminUserRole,
  changeAdminUserStatus,
  exportSystemBackup,
  getAdminAuditLogs,
  getAdminOverview,
  getAdminUsers
} from '../../services/admin'

const loading = ref(false)
const overview = ref(null)
const users = ref({ items: [], page: 1, size: 20, total: 0 })
const audits = ref({ items: [], page: 1, size: 20, total: 0 })
const filters = reactive({ search: '', role: '', status: '', page: 1, size: 20 })
const backupPassword = ref('')
const backupRunning = ref(false)

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

onMounted(async () => {
  try {
    await Promise.all([loadOverview(), loadUsers(), loadAudits()])
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
        <el-card>
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
.el-button { margin-left: 12px; }
</style>
