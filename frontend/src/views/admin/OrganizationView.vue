<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createMajor,
  getMajors,
  getOrganizationTeachers,
  getTeacherOrganization,
  updateMajor,
  updateTeacherOrganization,
  getTimetableTeacherLinks,
  saveTimetableTeacherLink,
  deleteTimetableTeacherLink
} from '../../services/organization'

const majors = ref([])
const teachers = ref([])
const selectedTeacher = ref(null)
const teacherForm = reactive({ majorId: null, roles: ['FULL_TIME_TEACHER'], leaderMajorId: null })
const timetableLinks = ref([])
const timetableForm = reactive({ sourceName: '', teacherId: null })
const majorDialog = ref(false)
const editingMajor = ref(null)
const majorForm = reactive({ code: '', name: '', active: true })
const roles = [
  ['FULL_TIME_TEACHER', '专任教师'],
  ['MAJOR_LEADER', '专业负责人'],
  ['VICE_DEAN', '副院长'],
  ['DEAN', '院长'],
  ['PARTY_SECRETARY', '书记']
]

async function load() {
  ;[majors.value, teachers.value, timetableLinks.value] = await Promise.all([
    getMajors(true), getOrganizationTeachers(), getTimetableTeacherLinks()
  ])
}

async function saveTimetableLink() {
  try {
    await saveTimetableTeacherLink({
      sourceName: timetableForm.sourceName.trim(),
      teacherId: timetableForm.teacherId
    })
    timetableForm.sourceName = ''
    timetableForm.teacherId = null
    timetableLinks.value = await getTimetableTeacherLinks()
    ElMessage.success('课表姓名映射已保存')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '课表映射保存失败')
  }
}

async function removeTimetableLink(sourceName) {
  try {
    await deleteTimetableTeacherLink(sourceName)
    timetableLinks.value = await getTimetableTeacherLinks()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '课表映射删除失败')
  }
}

async function selectTeacher(id) {
  selectedTeacher.value = id
  const value = await getTeacherOrganization(id)
  teacherForm.majorId = value.majorId
  teacherForm.roles = value.roles.map((item) => item.role)
  teacherForm.leaderMajorId = value.roles.find((item) => item.role === 'MAJOR_LEADER')?.majorId ?? null
}

async function saveTeacher() {
  const assignments = teacherForm.roles.map((role) => ({
    role,
    majorId: role === 'MAJOR_LEADER' ? teacherForm.leaderMajorId : null
  }))
  try {
    await updateTeacherOrganization(selectedTeacher.value, {
      majorId: teacherForm.majorId,
      roles: assignments
    })
    ElMessage.success('专业与业务角色已更新，旧 JWT 权限立即失效')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '更新失败')
  }
}

function editMajor(major = null) {
  editingMajor.value = major?.id ?? null
  majorForm.code = major?.code ?? ''
  majorForm.name = major?.name ?? ''
  majorForm.active = major?.active ?? true
  majorDialog.value = true
}

async function saveMajor() {
  const payload = { ...majorForm }
  if (editingMajor.value) await updateMajor(editingMajor.value, payload)
  else await createMajor(payload)
  majorDialog.value = false
  await load()
}

onMounted(load)
</script>

<template>
  <section class="page-panel">
    <header><div><h2>专业与业务角色</h2><p>系统角色仍仅为 TEACHER/SUPER_ADMIN；业务角色可多选。SUPER_ADMIN 不因此获得私人资料权限。</p></div><el-button type="primary" @click="editMajor()">新增专业</el-button></header>
    <el-card>
      <template #header>专业管理</template>
      <el-table :data="majors">
        <el-table-column prop="code" label="编码" />
        <el-table-column prop="name" label="名称" />
        <el-table-column label="状态"><template #default="{ row }">{{ row.active ? '启用' : '停用' }}</template></el-table-column>
        <el-table-column label="操作"><template #default="{ row }"><el-button link type="primary" @click="editMajor(row)">修改</el-button></template></el-table-column>
      </el-table>
    </el-card>
    <el-card id="teacher-mapping" class="teacher-card">
      <template #header>课表教师映射</template>
      <p class="mapping-note">课表姓名必须与教务总表第一列完全一致。保存所属专业不会建立这个映射。</p>
      <el-form class="mapping-form" label-position="top">
        <el-form-item label="课表姓名"><el-input v-model="timetableForm.sourceName" maxlength="128" /></el-form-item>
        <el-form-item label="对应系统教师">
          <el-select v-model="timetableForm.teacherId" filterable placeholder="选择教师">
            <el-option v-for="teacher in teachers" :key="teacher.teacherId" :label="teacher.displayName" :value="teacher.teacherId" />
          </el-select>
        </el-form-item>
        <el-button type="primary" :disabled="!timetableForm.sourceName.trim() || !timetableForm.teacherId" @click="saveTimetableLink">保存映射</el-button>
      </el-form>
      <el-table :data="timetableLinks">
        <el-table-column prop="sourceName" label="课表姓名" />
        <el-table-column prop="teacherDisplayName" label="系统教师" />
        <el-table-column label="操作"><template #default="{ row }"><el-button link type="danger" @click="removeTimetableLink(row.sourceName)">删除</el-button></template></el-table-column>
      </el-table>
    </el-card>
    <el-card class="teacher-card">
      <template #header>教师专业与角色</template>
      <el-select v-model="selectedTeacher" filterable placeholder="选择教师" @change="selectTeacher">
        <el-option v-for="teacher in teachers" :key="teacher.teacherId" :label="teacher.displayName" :value="teacher.teacherId" />
      </el-select>
      <el-form v-if="selectedTeacher" label-position="top">
        <el-form-item label="所属专业"><el-select v-model="teacherForm.majorId" clearable><el-option v-for="major in majors.filter((item) => item.active)" :key="major.id" :label="major.name" :value="major.id" /></el-select></el-form-item>
        <el-form-item label="业务角色"><el-checkbox-group v-model="teacherForm.roles"><el-checkbox v-for="[value, label] in roles" :key="value" :value="value" :disabled="value === 'FULL_TIME_TEACHER'">{{ label }}</el-checkbox></el-checkbox-group></el-form-item>
        <el-form-item v-if="teacherForm.roles.includes('MAJOR_LEADER')" label="负责专业"><el-select v-model="teacherForm.leaderMajorId"><el-option v-for="major in majors.filter((item) => item.active)" :key="major.id" :label="major.name" :value="major.id" /></el-select></el-form-item>
        <el-button type="primary" @click="saveTeacher">保存设置</el-button>
      </el-form>
    </el-card>
    <el-dialog v-model="majorDialog" :title="editingMajor ? '修改专业' : '新增专业'">
      <el-form label-position="top"><el-form-item label="专业编码"><el-input v-model="majorForm.code" /></el-form-item><el-form-item label="专业名称"><el-input v-model="majorForm.name" /></el-form-item><el-form-item label="启用"><el-switch v-model="majorForm.active" /></el-form-item></el-form>
      <template #footer><el-button @click="majorDialog = false">取消</el-button><el-button type="primary" @click="saveMajor">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.page-panel { padding: 28px; background: #fff; border: 1px solid #e5e7eb; border-radius: 8px; }
header { display: flex; justify-content: space-between; margin-bottom: 20px; }
h2 { margin: 0 0 8px; }
p { margin: 0; color: #6b7280; }
.teacher-card { margin-top: 20px; }
.teacher-card .el-form { margin-top: 20px; }
.mapping-note { margin-bottom: 16px; }
.mapping-form { max-width: 480px; margin-bottom: 16px; }
</style>
