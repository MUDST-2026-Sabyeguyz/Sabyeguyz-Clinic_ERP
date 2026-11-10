<script setup lang="ts">
import { computed, ref } from 'vue'
import RegisterMethodDialog from '@/features/idcard/RegisterMethodDialog.vue'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import { usePatientDirectory } from '../usePatientDirectory'
const registerDialogOpen = ref(false)
const readerBranch = computed(() => selectedManagerBranch()?.code)
const {
  isManager,
  query,
  branch,
  sortOrder,
  sortOptions,
  branches,
  page,
  totalPages,
  totalItems,
  loading,
  errorMessage,
  showCreationNotice,
  filteredPatients,
  refreshSearch,
  changePage,
  retryLoad,
  birthDateLabel,
  birthDateStates,
} = usePatientDirectory()
</script>

<template>
  <div class="patient-workspace patient-directory-page">
    <StaffHeader />
    <main>
      <section class="page-heading">
        <div>
          <h1>ค้นหาผู้ป่วย</h1>
          <p>ค้นหาด้วยชื่อหรือหมายเลข HN</p>
          <button type="button" class="primary" @click="registerDialogOpen = true">
            + ลงทะเบียนผู้ป่วยใหม่
          </button>
          <RegisterMethodDialog
            :open="registerDialogOpen"
            :branch-code="readerBranch"
            @close="registerDialogOpen = false"
          />
        </div>
      </section>

      <section class="directory" :aria-busy="loading">
        <header>
          <div class="search">
            <span class="search-icon" aria-hidden="true"
              ><svg viewBox="0 0 24 24">
                <circle cx="11" cy="11" r="6" />
                <path d="m16 16 4 4" /></svg></span
            ><input
              v-model="query"
              type="search"
              autocomplete="off"
              spellcheck="false"
              @input="refreshSearch"
              aria-label="ค้นหาผู้ป่วย"
              placeholder="ค้นหาชื่อหรือหมายเลข HN"
            />
          </div>
          <FilterDropdown
            v-if="!isManager"
            v-model="branch"
            class="branch-filter"
            label="กรองตามสาขา"
            default-value="ทั้งหมด"
            default-label="สาขาทั้งหมด"
            :options="branches.map((item) => ({ value: item.code, label: item.name }))"
          />
          <FilterDropdown
            v-model="sortOrder"
            class="sort-filter"
            label="เรียงลำดับผู้ป่วย"
            default-value="asc"
            default-label="ชื่อ ก-ฮ"
            :clear-on-reselect="false"
            :options="sortOptions"
          />
        </header>
        <div class="directory-results">
          <h2>รายชื่อผู้ป่วย</h2>
          <span v-if="!loading && !errorMessage" class="result-count" aria-live="polite"
            >ทั้งหมด {{ totalItems }} ราย</span
          >
        </div>
        <div class="table-scroll">
          <div v-if="loading" class="empty" role="status">กำลังโหลดข้อมูลผู้ป่วย...</div>
          <div v-else-if="errorMessage" class="empty" role="alert">
            <p>{{ errorMessage }}</p>
            <button type="button" class="directory-retry" @click="retryLoad">ลองใหม่</button>
          </div>
          <table v-else>
            <caption class="directory-sr-only">
              รายชื่อผู้ป่วยและข้อมูลติดต่อ
            </caption>
            <thead>
              <tr>
                <th scope="col">หมายเลข HN</th>
                <th
                  scope="col"
                  :aria-sort="
                    sortOrder === 'asc' ? 'ascending' : sortOrder === 'desc' ? 'descending' : 'none'
                  "
                >
                  ชื่อผู้ป่วย
                </th>
                <th scope="col">เบอร์โทรศัพท์</th>
                <th scope="col">วันเกิด</th>
                <th scope="col">สาขา</th>
                <th scope="col" aria-label="การดำเนินการ"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="patient in filteredPatients" :key="patient.id">
                <td class="patient-id">
                  <span class="patient-avatar">{{ patient.fullName.slice(0, 1) }}</span>
                  <b>{{ patient.hn }}</b>
                </td>
                <td>{{ patient.fullName }}</td>
                <td>{{ patient.phone || '-' }}</td>
                <td>
                  <span v-if="birthDateStates[patient.id] !== 'error'">{{
                    birthDateLabel(patient.id)
                  }}</span
                  ><button
                    v-else
                    class="directory-date-retry"
                    type="button"
                    @click="retryLoad"
                    :aria-label="'โหลดวันเกิดของ ' + patient.fullName + ' อีกครั้ง'"
                  >
                    โหลดวันเกิดอีกครั้ง
                  </button>
                </td>
                <td>{{ patient.branchName ?? patient.branchCode ?? '-' }}</td>
                <td>
                  <RouterLink
                    class="row-action"
                    :to="`/staff/patients/${patient.id}`"
                    :aria-label="'รายละเอียดผู้ป่วย ' + patient.fullName"
                    >รายละเอียด</RouterLink
                  >
                </td>
              </tr>
              <tr v-if="filteredPatients.length === 0">
                <td class="empty" colspan="6">ไม่พบผู้ป่วย</td>
              </tr>
            </tbody>
          </table>
        </div>
        <nav
          v-if="!loading && !errorMessage && totalPages > 1"
          class="patient-pagination"
          aria-label="เปลี่ยนหน้ารายชื่อผู้ป่วย"
        >
          <button type="button" :disabled="page === 0" @click="changePage(page - 1)">
            ← ก่อนหน้า
          </button>
          <span>หน้า {{ page + 1 }} จาก {{ totalPages }}</span>
          <button type="button" :disabled="page + 1 >= totalPages" @click="changePage(page + 1)">
            ถัดไป →
          </button>
        </nav>
      </section>
    </main>
    <Teleport to="body">
      <Transition name="save-success">
        <div
          v-if="showCreationNotice"
          class="patient-save-success-backdrop patient-directory-toast"
          role="status"
        >
          <section class="patient-save-success">ลงทะเบียนผู้ป่วยแล้ว</section>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped src="../styles/manager-patients.css"></style>
<style src="../styles/therapist.css"></style>

<style src="../styles/patient-directory.css"></style>
