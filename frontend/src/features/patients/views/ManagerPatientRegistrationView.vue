<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { vModalFocus } from '@/features/appointments/modalFocus'
import { vRegistrationErrors } from '../registrationErrors'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { registerPatient } from '@/api/patients'
import {
  appointmentApi,
  branchApi,
  masterDataApi,
  patientApi,
  type PatientProfile,
  type Appointment,
} from '@/api/client'
import { type PatientRegistrationForm } from '../data/managerPatientRegistration'
import { selectedManagerBranch, setManagerBranches } from '@/features/staff/data/managerBranch'
import { isOptionalPhoneValid, limitPhoneDigits } from '@/utils/phone'
import { clinicToday, clinicYesterday } from '@/utils/clinicDate'
import { takePendingCard } from '@/features/idcard/pendingCard'
import { cardExpired, mapCardToRegistration } from '@/features/idcard/cardToForm'
import {
  patientTitleOptions,
  titleForNationality,
  titleForGender,
  genderForTitle,
} from '../data/patientTitles'

const isManager = JSON.parse(localStorage.getItem('authUser') ?? 'null')?.role === 'MANAGER'

const submitting = ref(false)
const route = useRoute()
// ป้องกันผลคำขอเก่าพากลับไปหน้าเดิมหลังผู้ใช้ออกจากฟอร์มแล้ว
const registrationActive = ref(true)
onBeforeUnmount(() => {
  registrationActive.value = false
})
const router = useRouter()
const registrationBackTarget = computed(() =>
  route.query.appointmentId
    ? {
        name: 'staff-appointments',
        query: {
          startDate:
            typeof route.query.returnStartDate === 'string' ? route.query.returnStartDate : '',
          endDate: typeof route.query.returnEndDate === 'string' ? route.query.returnEndDate : '',
          type: typeof route.query.returnType === 'string' ? route.query.returnType : 'all',
        },
      }
    : { name: 'patient-search' },
)
const registrationForm = ref<HTMLFormElement | null>(null)
const sourceAppointment = ref<Appointment | null>(null)
const sourceLoading = ref(Boolean(route.query.appointmentId))
const sourceError = ref('')
const initialForm = ref('')
const cardFilled = ref(false)
const cardExpiredWarning = ref('')
const appointmentSummary = computed(() => {
  const item = sourceAppointment.value
  if (!item) return ''
  const date = new Intl.DateTimeFormat('th-TH', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Asia/Bangkok',
  }).format(new Date(item.startAt))
  return [
    item.contactName || item.patient?.fullName,
    item.contactPhone ? ` ${item.contactPhone}` : null,
    `เวลา ${date} น.`,
  ]
    .filter(Boolean)
    .join(' ')
})
async function loadSourceAppointment() {
  sourceLoading.value = true
  sourceError.value = ''
  try {
    const id = Number(route.query.appointmentId)
    if (!Number.isSafeInteger(id) || id <= 0) throw new Error('ไม่พบนัดหมายต้นทาง')
    const item = await appointmentApi.get(id)
    if (!registrationActive.value) return
    if (item.patient?.id)
      throw new Error('นัดหมายนี้มีผู้ป่วยที่ลงทะเบียนแล้ว กรุณากลับไปตรวจสอบนัดหมาย')
    if (item.patientType !== 'NEW') throw new Error('นัดหมายนี้ไม่ใช่นัดสำหรับผู้ป่วยใหม่')
    if (item.status !== 'UNCONFIRMED')
      throw new Error('นัดหมายนี้ยังไม่พร้อมลงทะเบียน กรุณากลับไปตรวจสอบสถานะ')
    sourceAppointment.value = item
    form.branchCode = item.branchCode
    if (!form.nickname) form.nickname = item.contactNickname || ''
    if (!form.phone) form.phone = limitPhoneDigits(item.contactPhone || '')
  } catch (error) {
    sourceError.value = error instanceof Error ? error.message : 'โหลดนัดหมายไม่สำเร็จ'
  } finally {
    sourceLoading.value = false
  }
}
// เปิดเฉพาะตอนระบบพาไปหน้าชำระเงินเอง ไม่ให้ guard ด้านล่างบล็อกการนำทางนี้
const leavingToServices = ref(false)
const leaveDialogOpen = ref(false)
let resolveLeave: ((leave: boolean) => void) | undefined
function answerLeave(leave: boolean) {
  leaveDialogOpen.value = false
  const resolve = resolveLeave
  resolveLeave = undefined
  resolve?.(leave)
}
onBeforeUnmount(() => answerLeave(false))
onBeforeRouteLeave(() => {
  if (leavingToServices.value) return true
  if (submitting.value || continuingAppointment.value) return false
  if (!registeredPatientId.value && initialForm.value && JSON.stringify(form) !== initialForm.value) {
    if (leaveDialogOpen.value) return false
    leaveDialogOpen.value = true
    return new Promise<boolean>((resolve) => { resolveLeave = resolve })
  }
  return true
})
const submitted = ref(false)
const emailFocused = ref(false)
const identityFocused = ref(false)
const errorMessage = ref('')
const confirmationOpen = ref(false)
const duplicateOpen = ref(false)
const successOpen = ref(false)
const duplicatePatient = ref<PatientProfile | null>(null)
const registeredHn = ref('')
const registeredPatientId = ref<number | null>(null)
const registeredPatientName = computed(() =>
  `${form.title}${form.firstName} ${form.middleName} ${form.lastName}`.replace(/\s+/g, ' ').trim(),
)
const registeredIdentityNumber = computed(() => {
  const value = form.identityNumber.trim()
  if (!value || isForeign.value) return value
  return value.replace(/^(\d)(\d{4})(\d{5})(\d{2})(\d)$/, '$1-$2-$3-$4-$5')
})
const customerGroups = ref<string[]>([])
const referralChannels = ref<string[]>([])
const insuranceTypes = ref<string[]>([])
const insuranceCompanies = ref<string[]>([])
const branches = ref<Array<{ code: string; name: string }>>([])
const titleOptions = computed(() => patientTitleOptions(form.nationality === 'ต่างชาติ'))
const genderOptions = ['ชาย', 'หญิง', 'ไม่ระบุ'].map((value) => ({ value, label: value }))
const bloodGroupOptions = ['O', 'A', 'B', 'AB', 'ไม่ทราบ'].map((value) => ({
  value,
  label: value,
}))
const branchOptions = computed(() =>
  branches.value.map((branch) => ({ value: branch.code, label: branch.name })),
)
const customerGroupOptions = computed(() =>
  customerGroups.value.map((value) => ({ value, label: value })),
)
const referralChannelOptions = computed(() =>
  referralChannels.value.map((value) => ({ value, label: value })),
)
const insuranceCompanyOptions = computed(() =>
  insuranceCompanies.value.map((value) => ({ value, label: value })),
)
const insuranceTypeOptions = computed(() =>
  insuranceTypes.value.map((value) => ({ value, label: value })),
)
const form = reactive<PatientRegistrationForm>({
  branchCode: '',
  email: '',
  title: 'นาย',
  customerType: '',
  firstName: '',
  middleName: '',
  lastName: '',
  nickname: '',
  fullNameEnglish: '',
  birthDate: '',
  gender: 'ชาย',
  phone: '',
  identityDocumentType: 'NATIONAL_ID',
  identityNumber: '',
  address: '',
  bloodGroup: 'ไม่ทราบ',
  nationality: 'ไทย',
  customerSource: '',
  insuranceType: '',
  insuranceCompany: '',
  salesChannel: 'หน้าสาขา',
  paymentType: 'ชำระด้วยตนเอง',
  drugAllergy: false,
  foodAllergy: false,
  chronicDisease: '',
  privacyConsent: false,
})
const identityIsValid = computed(() => {
  if (!form.identityNumber.trim()) return true
  if (form.nationality === 'ต่างชาติ') return /^[A-Za-z0-9]{6,20}$/.test(form.identityNumber)
  if (form.identityDocumentType === 'NATIONAL_ID') return /^\d{13}$/.test(form.identityNumber)
  if (form.identityDocumentType === 'PASSPORT') return /^\d{10}$/.test(form.identityNumber)
  return false
})
const isForeign = computed(() => form.nationality === 'ต่างชาติ')
const emailIsValid = computed(() =>
  !form.email.trim()
    ? true
    : form.email.trim().length <= 254 &&
      /^[^@.\s]+(\.[^@.\s]+)*@[^@.\s]+(\.[^@.\s]+)+$/.test(form.email.trim()),
)
const emailErrorVisible = computed(
  () => submitted.value && !emailFocused.value && !emailIsValid.value,
)
const identityErrorVisible = computed(
  () => submitted.value && !identityFocused.value && !identityIsValid.value,
)
const thaiOnly = (value: string) => value.replace(/[^\u0E00-\u0E7F\s]/g, '')
const allowedName = (value: string) =>
  isForeign.value ? value.replace(/[^\p{L}\s.'-]/gu, '') : thaiOnly(value)
const digitsOnly = (value: string) => value.replace(/\D/g, '')
const thaiNameIsValid = (value: string) => /^[\u0E00-\u0E7F\s]+$/.test(value.trim())
const personNameIsValid = (value: string) =>
  isForeign.value ? /^[\p{L}\s.'-]+$/u.test(value.trim()) : thaiNameIsValid(value)
const phoneIsValid = computed(() =>
  isForeign.value ? isOptionalPhoneValid(form.phone) : /^[0-9]{10}$/.test(form.phone),
)
const birthDateIsValid = computed(() =>
  isForeign.value && !form.birthDate
    ? true
    : Boolean(form.birthDate && form.birthDate < clinicToday()),
)
const birthDateErrorVisible = computed(
  () => (Boolean(form.birthDate) || submitted.value) && !birthDateIsValid.value,
)
const canSubmit = computed(() =>
  Boolean(
    !sourceLoading.value &&
    !sourceError.value &&
    form.branchCode &&
    branches.value.length &&
    personNameIsValid(form.firstName) &&
    personNameIsValid(form.lastName) &&
    (!form.nickname || personNameIsValid(form.nickname)) &&
    emailIsValid.value &&
    birthDateIsValid.value &&
    phoneIsValid.value &&
    identityIsValid.value,
  ),
)
watch(
  () => form.title,
  (title) => {
    const gender = genderForTitle(title)
    if (gender && form.gender !== 'ไม่ระบุ') form.gender = gender
  },
)
watch(
  () => form.gender,
  (gender) => {
    if (gender === 'ชาย' || gender === 'หญิง')
      form.title = titleForGender(form.title, gender, form.nationality === 'ต่างชาติ')
  },
)
watch(
  () => form.identityDocumentType,
  () => {
    form.identityNumber = ''
  },
)
watch(
  () => form.nationality,
  (nationality) => {
    form.title = titleForNationality(form.title, nationality === 'ต่างชาติ')
    form.identityDocumentType = nationality === 'ต่างชาติ' ? 'PASSPORT' : 'NATIONAL_ID'
  },
)
onMounted(async () => {
  try {
    const [items, branchList] = await Promise.all([masterDataApi.list(), branchApi.list()])
    branches.value = branchList
      .filter((item) => item.active)
      .map((item) => ({ code: item.code, name: item.name }))
    if (isManager) {
      setManagerBranches(branches.value)
      applyManagerBranch()
    }
    if (branches.value.length && !branches.value.some((item) => item.code === form.branchCode))
      form.branchCode = branches.value[0]!.code
    const active = items.filter((item) => item.active)
    const valuesFor = (category: string) =>
      active.filter((item) => item.category === category).map((item) => item.name)
    customerGroups.value = valuesFor('Customer Group')
    referralChannels.value = valuesFor('Referral Channel')
    insuranceTypes.value = valuesFor('Insurance Type')
    insuranceCompanies.value = valuesFor('Insurance Company')
    if (!form.customerType) form.customerType = customerGroups.value[0]!
  } catch {
    errorMessage.value = 'โหลดข้อมูลไม่สำเร็จ'
  }
  if (route.query.appointmentId) await loadSourceAppointment()
  initialForm.value = JSON.stringify(form)
  // a card read from the registration dialog: fill the form (after the snapshot above, so leaving it still asks)
  const card = takePendingCard()
  if (card) {
    Object.assign(form, mapCardToRegistration(card))
    cardFilled.value = true
    cardExpiredWarning.value = cardExpired(card, clinicToday())
      ? 'บัตรประชาชนใบนี้หมดอายุแล้ว ตรวจสอบกับผู้ป่วยก่อนบันทึก'
      : ''
  }
})
function applyManagerBranch() {
  if (sourceAppointment.value) return
  if (isManager) form.branchCode = selectedManagerBranch()?.code ?? form.branchCode
}
onMounted(() => window.addEventListener('manager-branch-changed', applyManagerBranch))
onBeforeUnmount(() => window.removeEventListener('manager-branch-changed', applyManagerBranch))
async function requestSubmit() {
  if (submitting.value || confirmationOpen.value) return
  submitted.value = true
  if (!canSubmit.value) {
    errorMessage.value = ''
    await nextTick()
    const first = registrationForm.value?.querySelector<HTMLElement>(
      '.invalid-field input, .invalid-field select, .invalid-field textarea, .invalid-field button',
    )
    first?.focus()
    first?.scrollIntoView({ block: 'center', behavior: 'smooth' })
    return
  }
  try {
    duplicatePatient.value = await findDuplicatePatient()
    if (duplicatePatient.value) {
      duplicateOpen.value = true
      return
    }
  } catch {}
  errorMessage.value = ''
  confirmationOpen.value = true
}
async function openExistingPatient() {
  if (!duplicatePatient.value || continuingAppointment.value) return
  if (route.query.appointmentId) {
    if (await continueToServices(duplicatePatient.value.id)) duplicateOpen.value = false
    return
  }
  duplicateOpen.value = false
  void router.push(`/staff/patients/${duplicatePatient.value.id}`)
}
const continuingAppointment = ref(false)
async function continueToServices(patientId: number) {
  if (continuingAppointment.value || !registrationActive.value) return false
  const appointmentId = Number(route.query.appointmentId)
  if (!Number.isSafeInteger(appointmentId) || appointmentId <= 0) {
    errorMessage.value = 'ไม่พบนัดหมายต้นทาง กรุณากลับไปหน้านัดหมาย'
    return false
  }
  continuingAppointment.value = true
  errorMessage.value = ''
  try {
    // ตรวจสถานะนัดอีกครั้งก่อนพาไปหน้าบริการ
    const appointment = await appointmentApi.get(appointmentId)
    if (!Number.isSafeInteger(patientId) || patientId <= 0)
      throw new Error('ไม่พบรหัสผู้ป่วยที่ลงทะเบียน')
    if (appointment.patientType !== 'NEW' || appointment.status !== 'UNCONFIRMED')
      throw new Error('สถานะนัดหมายเปลี่ยนแล้ว กรุณากลับไปตรวจสอบนัดหมาย')
    if (!registrationActive.value) return true
    // ส่ง patientId จริงคู่กับ appointmentId โดยไม่ผูกนัดย้อนหลัง
    leavingToServices.value = true
    const failure = await router.push({
      name: 'staff-services',
      query: { patientId: String(patientId), appointmentId: String(appointmentId) },
    })
    if (failure) throw new Error('ไปหน้าชำระเงินไม่สำเร็จ กรุณาลองใหม่')
    return true
  } catch (error) {
    leavingToServices.value = false
    errorMessage.value =
      error instanceof Error ? error.message : 'ไปหน้าบริการไม่สำเร็จ กรุณาลองใหม่'
    return false
  } finally {
    continuingAppointment.value = false
  }
}
async function openRegisteredPatient() {
  if (!registeredPatientId.value || continuingAppointment.value || !registrationActive.value) return
  if (route.query.appointmentId) {
    if (await continueToServices(registeredPatientId.value)) successOpen.value = false
    return
  }
  successOpen.value = false
  void router.push(`/staff/patients/${registeredPatientId.value}`)
}
async function findDuplicatePatient() {
  if (!form.identityNumber.trim()) return null
  const candidates = await patientApi.searchAll(form.identityNumber)
  const profiles = await Promise.all(candidates.map((item) => patientApi.get(item.id)))
  return (
    profiles.find(
      (item) =>
        item.identityNumber.trim().toLocaleLowerCase() ===
        form.identityNumber.trim().toLocaleLowerCase(),
    ) ?? null
  )
}
async function submit() {
  if (submitting.value || !canSubmit.value) return
  confirmationOpen.value = false
  submitting.value = true
  errorMessage.value = ''
  try {
    const fullName = `${form.firstName} ${form.middleName} ${form.lastName}`
      .replace(/\s+/g, ' ')
      .trim()
    // ส่งข้อมูลสุขภาพ ผู้ติดต่อฉุกเฉิน และความยินยอมไปบันทึกพร้อมโปรไฟล์
    const result = await registerPatient({
      branchCode: form.branchCode,
      email: form.email,
      title: form.title,
      fullName,
      firstName: form.firstName,
      middleName: form.middleName || null,
      lastName: form.lastName,
      birthDate: form.birthDate,
      gender: form.gender,
      phone: form.phone,
      identityDocumentType: form.identityDocumentType,
      identityNumber: form.identityNumber,
      address: form.address.trim() || 'ไม่ระบุ',
      drugAllergy: form.drugAllergy,
      foodAllergy: form.foodAllergy,
      chronicDisease: form.chronicDisease,
      emergencyContactName: fullName,
      emergencyContactRelationship: 'ญาติ',
      emergencyContactPhone: form.phone,
      privacyConsent: form.privacyConsent,
      nickname: form.nickname,
      bloodGroup: form.bloodGroup,
      nationality: form.nationality,
      customerType: form.customerType,
      customerSource: form.customerSource,
      insuranceType: form.insuranceType,
      insuranceCompany: form.insuranceCompany,
    })
    registeredHn.value = result.hn
    registeredPatientId.value = result.id
    localStorage.setItem('patientCreationHn', result.hn)
    successOpen.value = true
    if (route.query.appointmentId) {
      // ให้เห็นป๊อปอัปลงทะเบียนสำเร็จและ HN ก่อนพาไปหน้าชำระเงิน
      await new Promise((resolve) => setTimeout(resolve, 3000))
      await openRegisteredPatient()
    }
  } catch (error) {
    try {
      duplicatePatient.value = await findDuplicatePatient()
    } catch {
      duplicatePatient.value = null
    }
    if (duplicatePatient.value) duplicateOpen.value = true
    else errorMessage.value = error instanceof Error ? error.message : 'เกิดข้อผิดพลาด'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="registration-page">
    <StaffHeader />
    <main class="registration-content">
      <div class="back-row">
        <RouterLink :to="registrationBackTarget" class="back-link">{{
          route.query.appointmentId ? '← กลับไปหน้านัดหมาย' : '← กลับไปค้นหาผู้ป่วย'
        }}</RouterLink>
      </div>
      <section v-if="!registeredHn" class="registration-shell">
        <div class="intro">
          <h1>ลงทะเบียนผู้ป่วยใหม่</h1>
        </div>
        <aside
          v-if="route.query.appointmentId"
          class="registration-source"
          aria-label="นัดหมายต้นทาง"
        >
          <p v-if="sourceLoading" role="status">กำลังโหลดข้อมูลนัดหมาย…</p>
          <template v-else-if="sourceError">
            <p role="alert">{{ sourceError }}</p>
            <button type="button" class="secondary" @click="loadSourceAppointment">ลองใหม่</button>
          </template>
          <template v-else
            ><strong>ลงทะเบียนจากนัดหมาย</strong>
            <p>{{ appointmentSummary }}</p></template
          >
        </aside>
        <form
          ref="registrationForm"
          v-registration-errors
          class="form-card"
          novalidate
          @submit.prevent="requestSubmit"
        >
          <section class="registration-section">
            <h2>ข้อมูลส่วนตัว</h2>
            <fieldset class="patient-type">
              <legend>สัญชาติ <em>*</em></legend>
              <label :class="{ selected: form.nationality === 'ไทย' }">
                <input v-model="form.nationality" type="radio" value="ไทย" />ไทย
              </label>
              <label :class="{ selected: form.nationality === 'ต่างชาติ' }">
                <input v-model="form.nationality" type="radio" value="ต่างชาติ" />ต่างชาติ
              </label>
            </fieldset>
            <div class="personal-details-grid">
              <label>
                คำนำหน้า
                <FilterDropdown
                  v-model="form.title"
                  :options="titleOptions"
                  default-label="เลือกคำนำหน้า"
                  label="คำนำหน้า"
                  :clear-on-reselect="false"
                  hide-default-option
                />
              </label>
              <label>
                เพศ <em>*</em>
                <FilterDropdown
                  v-model="form.gender"
                  :options="genderOptions"
                  default-label="เลือกเพศ"
                  label="เพศ"
                  :clear-on-reselect="false"
                  hide-default-option
                />
              </label>
            </div>
            <div class="patient-name-grid">
              <label
                :class="{
                  'invalid-field': submitted && !personNameIsValid(form.firstName),
                }"
              >
                ชื่อ <em>*</em>
                <input
                  v-model.trim="form.firstName"
                  placeholder="ชื่อ"
                  @input="form.firstName = allowedName(form.firstName)"
                />
                <small v-if="submitted && !form.firstName" class="required-error"
                  >กรุณากรอกข้อมูล</small
                >
                <small
                  v-else-if="submitted && !personNameIsValid(form.firstName)"
                  class="required-error"
                  >รูปแบบชื่อไม่ถูกต้อง</small
                >
              </label>
              <label>
                ชื่อกลาง
                <input
                  v-model.trim="form.middleName"
                  placeholder="ระบุชื่อกลาง (ถ้ามี)"
                  @input="form.middleName = allowedName(form.middleName)"
                />
              </label>
              <label
                :class="{
                  'invalid-field': submitted && !personNameIsValid(form.lastName),
                }"
              >
                นามสกุล <em>*</em>
                <input
                  v-model.trim="form.lastName"
                  placeholder="นามสกุล"
                  @input="form.lastName = allowedName(form.lastName)"
                />
                <small v-if="submitted && !form.lastName" class="required-error"
                  >กรุณากรอกข้อมูล</small
                >
                <small
                  v-else-if="submitted && !personNameIsValid(form.lastName)"
                  class="required-error"
                  >รูปแบบนามสกุลไม่ถูกต้อง</small
                >
              </label>
            </div>
            <div class="personal-details-grid">
              <label
                :class="{
                  'invalid-field': submitted && form.nickname && !personNameIsValid(form.nickname),
                }"
              >
                ชื่อเล่น
                <input
                  v-model.trim="form.nickname"
                  placeholder="ชื่อเล่น"
                  @input="form.nickname = allowedName(form.nickname)"
                />
                <small
                  v-if="submitted && form.nickname && !personNameIsValid(form.nickname)"
                  class="required-error"
                  >รูปแบบชื่อเล่นไม่ถูกต้อง</small
                >
              </label>
              <label :class="{ 'invalid-field': birthDateErrorVisible }">
                วันเกิด <em v-if="!isForeign">*</em>
                <ThaiDatePicker
                  v-model="form.birthDate"
                  :invalid="birthDateErrorVisible"
                  :max-date="clinicYesterday()"
                  show-calendar-icon
                  numeric-month
                  hide-caret
                />
                <small v-if="submitted && !isForeign && !form.birthDate" class="required-error"
                  >กรุณากรอกข้อมูล</small
                >
                <small v-else-if="birthDateErrorVisible" class="required-error" role="alert"
                  >วันเกิดต้องเป็นวันก่อนวันนี้</small
                >
              </label>
            </div>
            <div class="personal-details-grid">
              <label>
                กรุ๊ปเลือด
                <FilterDropdown
                  v-model="form.bloodGroup"
                  :options="bloodGroupOptions"
                  default-label="เลือกกรุ๊ปเลือด"
                  label="กรุ๊ปเลือด"
                  :clear-on-reselect="false"
                  hide-default-option
                />
              </label>
              <label :class="{ 'invalid-field': identityErrorVisible }">
                {{ isForeign ? 'เลขพาสปอร์ต' : 'เลขบัตรประชาชน' }} <em>*</em>
                <input
                  v-model.trim="form.identityNumber"
                  :inputmode="isForeign ? 'text' : 'numeric'"
                  :maxlength="isForeign ? 20 : 13"
                  :placeholder="isForeign ? 'กรอกเลขพาสปอร์ต' : 'x-xxxx-xxxxx-xx-x'"
                  @focus="identityFocused = true"
                  @blur="identityFocused = false"
                  @input="
                    form.identityNumber = isForeign
                      ? form.identityNumber.replace(/[^A-Za-z0-9]/g, '')
                      : digitsOnly(form.identityNumber)
                  "
                />
                <small v-if="identityErrorVisible" class="required-error">{{
                  isForeign ? 'รูปแบบเลขพาสปอร์ตไม่ถูกต้อง' : 'กรุณากรอกตัวเลข 13 หลัก'
                }}</small>
              </label>
              <p v-if="cardFilled" class="card-filled-note" role="status" style="grid-column: 1 / -1">
                กรอกข้อมูลจากบัตรประชาชนให้แล้ว ตรวจสอบและแก้ไขได้ก่อนบันทึก
                <small v-if="cardExpiredWarning" class="required-error">{{ cardExpiredWarning }}</small>
              </p>
            </div>
          </section>
          <section class="registration-section">
            <h2>ข้อมูลติดต่อ</h2>
            <div class="form-grid">
              <label :class="{ 'invalid-field': submitted && !phoneIsValid }"
                >เบอร์โทร <em v-if="!isForeign">*</em
                ><input
                  v-model.trim="form.phone"
                  type="tel"
                  inputmode="numeric"
                  pattern="[0-9]*"
                  maxlength="10"
                  placeholder="กรอกตัวเลขไม่เกิน 10 หลัก"
                  @input="form.phone = limitPhoneDigits(form.phone)"
                /><small v-if="submitted && !isForeign && !form.phone" class="required-error"
                  >กรุณากรอกข้อมูล</small
                ><small v-else-if="submitted && !phoneIsValid" class="required-error"
                  >กรุณากรอกตัวเลข 10 หลัก</small
                ></label
              >
              <label :class="{ 'invalid-field': emailErrorVisible }"
                >อีเมล<input
                  v-model.trim="form.email"
                  type="text"
                  inputmode="email"
                  placeholder="example@gmail.com"
                  @focus="emailFocused = true"
                  @blur="emailFocused = false"
                /><small v-if="emailErrorVisible" class="required-error"
                  >รูปแบบอีเมลไม่ถูกต้อง</small
                ></label
              >
              <label class="wide">ที่อยู่<textarea v-model.trim="form.address" rows="4" /></label>
            </div>
          </section>
          <section class="registration-section">
            <h2>ข้อมูลเพิ่มเติม</h2>
            <div class="form-grid">
              <label
                >สาขาที่ลงทะเบียน
                <FilterDropdown
                  v-model="form.branchCode"
                  :options="branchOptions"
                  default-label="เลือกสาขา"
                  label="สาขาที่ลงทะเบียน"
                  hide-chevron
                  disabled
                  :clear-on-reselect="false"
                  hide-default-option
              /></label>
              <label
                >กลุ่มลูกค้า<FilterDropdown
                  v-model="form.customerType"
                  :options="customerGroupOptions"
                  default-label="เลือกกลุ่มลูกค้า"
                  label="กลุ่มลูกค้า"
                  :clear-on-reselect="false"
                  hide-default-option
              /></label>
              <label
                >ช่องทางแนะนำ<FilterDropdown
                  v-model="form.customerSource"
                  :options="referralChannelOptions"
                  default-label="ไม่ระบุ"
                  label="ช่องทางแนะนำ"
                  :clear-on-reselect="false"
              /></label>
              <label
                >ประเภทประกัน<FilterDropdown
                  v-model="form.insuranceType"
                  :options="insuranceTypeOptions"
                  default-label="ไม่ระบุ"
                  label="ประเภทประกัน"
                  :clear-on-reselect="false"
              /></label>
              <label
                >บริษัทประกัน<FilterDropdown
                  v-model="form.insuranceCompany"
                  :options="insuranceCompanyOptions"
                  default-label="ไม่มีประกัน"
                  label="บริษัทประกัน"
                  :clear-on-reselect="false"
              /></label>
            </div>
          </section>
          <div class="actions end">
            <button
              type="button"
              class="secondary registration-cancel"
              :disabled="submitting || continuingAppointment"
              @click="router.push(registrationBackTarget)"
            >
              ยกเลิก
            </button>
            <button
              type="submit"
              class="primary"
              :disabled="submitting || sourceLoading || Boolean(sourceError)"
            >
              {{ submitting ? 'กำลังบันทึก...' : route.query.appointmentId ? 'บันทึก' : 'บันทึก' }}
            </button>
          </div>
          <p v-if="errorMessage" class="error" role="alert">
            {{ errorMessage }}
          </p>
        </form>
      </section>
      <section v-else class="success-card">
        <span class="success-icon">✓</span>
        <h1>ลงทะเบียนสำเร็จ</h1>
        <p>หมายเลข HN</p>
        <strong>{{ registeredHn }}</strong>
        <template v-if="route.query.appointmentId">
          <p v-if="!errorMessage" role="status">กำลังไปหน้าชำระเงิน…</p>
          <template v-else>
            <p role="alert">{{ errorMessage }}</p>
            <div class="success-actions">
              <RouterLink class="secondary" :to="registrationBackTarget"
                >กลับไปหน้านัดหมาย</RouterLink
              ><button
                type="button"
                class="primary"
                :disabled="continuingAppointment"
                @click="openRegisteredPatient"
              >
                ไปหน้าชำระเงินอีกครั้ง
              </button>
            </div>
          </template>
        </template>
        <div v-else class="success-actions">
          <RouterLink class="secondary" :to="registrationBackTarget">กลับไปรายชื่อผู้ป่วย</RouterLink
          ><RouterLink
            v-if="registeredPatientId"
            class="primary"
            :to="`/staff/patients/${registeredPatientId}`"
            >แสดงข้อมูลผู้ป่วย</RouterLink
          >
        </div>
      </section>
    </main>
    <Teleport to="body">
      <div
        v-if="confirmationOpen"
        class="registration-modal-backdrop"
        @keydown.esc="confirmationOpen = false"
      >
        <section
          class="registration-modal confirmation-modal"
          v-modal-focus="{ close: () => (confirmationOpen = false), busy: submitting }"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="registration-confirmation-title"
        >
          <h2 id="registration-confirmation-title">ยืนยันลงทะเบียนผู้ป่วย?</h2>
          <p class="confirmation-patient-name">{{ registeredPatientName }}</p>
          <p v-if="errorMessage" role="alert">{{ errorMessage }}</p>
          <div class="registration-modal-actions">
            <button type="button" class="secondary" autofocus @click="confirmationOpen = false">
              ยกเลิก
            </button>
            <button type="button" class="primary" :disabled="submitting" @click="submit">
              {{ submitting ? 'กำลังบันทึก...' : 'ยืนยัน' }}
            </button>
          </div>
        </section>
      </div>
    </Teleport>
    <Teleport to="body">
      <div v-if="duplicateOpen" class="registration-modal-backdrop">
        <section
          class="registration-modal duplicate-modal"
          v-modal-focus="{ close: () => (duplicateOpen = false), busy: continuingAppointment }"
          aria-labelledby="registration-duplicate-title"
          role="alertdialog"
          aria-modal="true"
        >
          <span class="duplicate-warning-icon" aria-hidden="true">!</span>
          <h2 id="registration-duplicate-title">พบเลขบัตรประชาชน/เลข HN ซ้ำ</h2>
          <p>ระบบตรวจพบข้อมูลที่ตรงกับผู้ป่วยเดิม จึงไม่สามารถสร้างรายการใหม่ได้</p>
          <strong>ไม่สามารถบันทึกเป็นผู้ป่วยใหม่ได้</strong>
          <div v-if="duplicatePatient" class="duplicate-patient">
            <div>
              <b>{{ duplicatePatient.fullName }}</b>
              <span>HN {{ duplicatePatient.hn }}</span>
              <span>เลขบัตรประชาชน {{ duplicatePatient.identityNumber }}</span>
            </div>
            <small>ข้อมูลเดิมในระบบ</small>
          </div>
          <div class="registration-modal-actions">
            <button type="button" class="secondary" @click="duplicateOpen = false">
              ตรวจสอบข้อมูล
            </button>
            <button
              type="button"
              class="primary"
              :disabled="continuingAppointment"
              @click="openExistingPatient"
            >
              {{
                continuingAppointment
                  ? 'กำลังไปหน้าชำระเงิน…'
                  : route.query.appointmentId
                    ? 'ใช้ผู้ป่วยนี้และไปชำระเงิน'
                    : 'แสดงข้อมูลผู้ป่วยเดิม'
              }}
            </button>
          </div>
        </section>
      </div>
    </Teleport>
    <Teleport to="body">
      <div v-if="successOpen" class="registration-modal-backdrop">
        <section
          class="registration-modal registration-success-modal"
          v-modal-focus="{ close: () => {}, busy: continuingAppointment }"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="registration-success-title"
        >
          <span class="registration-success-check" aria-hidden="true">✓</span>
          <h2 id="registration-success-title">ลงทะเบียนผู้ป่วยสำเร็จ</h2>
          <div class="registration-success-patient">
            <b>{{ registeredPatientName }}</b>
            <span>HN {{ registeredHn }}</span>
            <span v-if="registeredIdentityNumber">
              {{ isForeign ? 'เลขหนังสือเดินทาง' : 'เลขบัตรประชาชน' }}
              {{ registeredIdentityNumber }}
            </span>
          </div>
          <p v-if="errorMessage" role="alert">{{ errorMessage }}</p>
          <p v-else-if="route.query.appointmentId" class="registration-redirect" role="status">
            กำลังไปหน้าชำระเงิน<span class="redirect-dots" aria-hidden="true"></span>
          </p>
          <button
            v-if="errorMessage || !route.query.appointmentId"
            type="button"
            class="primary"
            :disabled="continuingAppointment"
            @click="openRegisteredPatient"
          >
            {{
              continuingAppointment
                ? 'กำลังไปหน้าชำระเงิน…'
                : route.query.appointmentId
                  ? 'บริการและชำระเงิน'
                  : 'แสดงข้อมูลผู้ป่วย'
            }}
          </button>
        </section>
      </div>
    </Teleport>
  </div>
    <Teleport to="body">
      <div v-if="leaveDialogOpen" class="registration-modal-backdrop" @click.self="answerLeave(false)">
        <section
          class="registration-modal confirmation-modal"
          v-modal-focus="{ close: () => answerLeave(false), busy: false }"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="registration-leave-title"
          aria-describedby="registration-leave-description"
        >
          <h2 id="registration-leave-title">ออกจากหน้านี้?</h2>
          <p id="registration-leave-description">ข้อมูลที่กรอกยังไม่ได้บันทึก หากออกจากหน้านี้ ข้อมูลจะไม่ถูกบันทึก</p>
          <div class="registration-modal-actions">
            <button type="button" class="secondary" autofocus @click="answerLeave(false)">ยกเลิก</button>
            <button type="button" class="primary" @click="answerLeave(true)">ตกลง</button>
          </div>
        </section>
      </div>
    </Teleport>
</template>
<style scoped src="../styles/patient-registration.css"></style>
<style src="../styles/patient-workspace.css"></style>

<style scoped src="../styles/registration-polish.css"></style>
