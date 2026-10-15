<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { downloadCommonFreeExcel, getCommonFreeOptions, queryCommonFree } from '../../services/timetable'
import {
  buildSlotGrid,
  cellSummary,
  cellTone,
  excludedTeacherRows,
  missingImportNotice,
  slotLabel,
  TIMETABLE_DAY_LABELS
} from './timetableUiRules'

const majors = ref([])
const teachers = ref([])
const loading = ref(false)
const exporting = ref(false)
const result = ref(null)
const form = reactive({ semesterCode: '', week: 1, scope: 'major', majorId: null, teacherIds: [] })

const teacherOptions = computed(() =>
  teachers.value.map((item) => ({
    id: item.teacherId,
    label: item.displayName || item.majorName || `教师 ${item.teacherId}`
  })))

const grid = computed(() => buildSlotGrid(result.value?.slots || [], TIMETABLE_DAY_LABELS))
const notice = computed(() => missingImportNotice(result.value))
const excludedRows = computed(() => excludedTeacherRows(result.value?.excludedTeachers || []))

async function load() {
  try {
    const options = await getCommonFreeOptions()
    majors.value = options.majors || []
    teachers.value = options.teachers || []
    if (!majors.value.some((item) => item.id === form.majorId)) {
      form.majorId = majors.value[0]?.id ?? null
    }
  } catch (error) {
    majors.value = []
    teachers.value = []
    ElMessage.error(error.response?.data?.message || '专业和教师列表加载失败')
  }
}

async function submit() {
  if (!form.semesterCode) {
    ElMessage.warning('请填写学期，例如 2026-2027-1')
    return
  }
  if (form.scope === 'major' && !form.majorId) {
    ElMessage.warning('请选择专业')
    return
  }
  if (form.scope === 'teachers' && form.teacherIds.length === 0) {
    ElMessage.warning('请至少选择一位教师')
    return
  }
  loading.value = true
  try {
    result.value = await queryCommonFree({
      semesterCode: form.semesterCode,
      week: form.week,
      majorId: form.scope === 'major' ? form.majorId : null,
      teacherIds: form.scope === 'teachers' ? form.teacherIds : []
    })
  } catch (error) {
    result.value = null
    ElMessage.error(error.response?.data?.message || '查询失败')
  } finally {
    loading.value = false
  }
}

async function exportExcel() {
  exporting.value = true
  try {
    // 与查询完全相同的筛选参数。
    await downloadCommonFreeExcel({
      semesterCode: form.semesterCode,
      week: form.week,
      majorId: form.scope === 'major' ? form.majorId : null,
      teacherIds: form.scope === 'teachers' ? form.teacherIds : []
    })
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="common-free">
    <h2>公共空闲查询</h2>
    <p class="hint">
      所选教师在该学期、该周、该天、该节次组都没有课时才算空闲。需要先完成一次课表同步。
    </p>

    <el-form label-width="90px" class="query-form">
      <el-form-item label="学期">
        <el-input v-model="form.semesterCode" placeholder="2026-2027-1" style="width: 200px" />
      </el-form-item>
      <el-form-item label="周次">
        <el-input-number v-model="form.week" :min="1" :max="30" />
      </el-form-item>
      <el-form-item label="范围">
        <el-radio-group v-model="form.scope">
          <el-radio value="major">按专业</el-radio>
          <el-radio value="teachers">按教师</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="form.scope === 'major'" label="专业">
        <el-select v-model="form.majorId" placeholder="选择专业" style="width: 260px">
          <el-option v-for="item in majors" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-else label="教师">
        <el-select
          v-model="form.teacherIds"
          multiple
          filterable
          placeholder="选择教师"
          style="width: 420px"
        >
          <el-option
            v-for="item in teacherOptions"
            :key="item.id"
            :label="item.label"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="submit">查询</el-button>
        <el-button :loading="exporting" @click="exportExcel">导出 Excel</el-button>
      </el-form-item>
    </el-form>

    <el-alert v-if="notice" :title="notice" type="warning" show-icon :closable="false" class="notice" />

    <template v-if="result">
      <el-table :data="grid" border class="grid">
        <el-table-column label="节次" width="110" fixed>
          <template #default="{ row }">
            {{ row.label }}
          </template>
        </el-table-column>
        <el-table-column
          v-for="(slotCode, index) in grid[0]?.cells.map((entry) => entry.slotCode) || []"
          :key="slotCode"
          :label="slotLabel(slotCode)"
          min-width="150"
        >
          <template #default="{ row }">
            <div class="cell" :class="`is-${cellTone(row.cells[index].slot)}`">
              {{ cellSummary(row.cells[index].slot) }}
            </div>
          </template>
        </el-table-column>
      </el-table>

      <section class="excluded">
        <h3>未参与判断的教师（{{ excludedRows.length }}）</h3>
        <el-empty v-if="excludedRows.length === 0" description="没有教师被排除" :image-size="60" />
        <el-table v-else :data="excludedRows" border>
          <el-table-column prop="sourceName" label="教师" />
          <el-table-column prop="reason" label="原因码" width="200" />
          <el-table-column prop="reasonLabel" label="说明" />
        </el-table>
      </section>
    </template>
  </div>
</template>

<style scoped>
.common-free {
  padding: 16px;
}
.hint {
  color: #6b7280;
  margin-bottom: 16px;
}
.query-form {
  max-width: 900px;
}
.notice {
  margin-bottom: 16px;
}
.grid {
  margin-bottom: 24px;
}
.cell {
  padding: 4px 6px;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.4;
}
.cell.is-free {
  background: #ecfdf5;
  color: #047857;
}
.cell.is-busy {
  background: #fef2f2;
  color: #b91c1c;
}
.cell.is-unknown {
  background: #f3f4f6;
  color: #6b7280;
}
</style>
