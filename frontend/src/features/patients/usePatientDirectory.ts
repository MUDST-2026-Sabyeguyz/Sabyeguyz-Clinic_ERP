import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { branchApi, patientApi, type Branch, type Patient } from '@/api/client'
import { selectedManagerBranch, setManagerBranches } from '@/features/staff/data/managerBranch'

// Only navigation context stays in memory; patient records/searches are never written to storage.
const contexts = new Map<string, { query: string; branch: string; sort: string; page: number }>()
export function usePatientDirectory() {
  const user = JSON.parse(localStorage.getItem('authUser') ?? 'null')
  const isManager = user?.role === 'MANAGER'
  const key = String(user?.id ?? '') + ':' + String(user?.role ?? '')
  const saved = contexts.get(key)
  const query = ref(saved?.query ?? '')
  const branch = ref(saved?.branch ?? 'ทั้งหมด')
  const sortOrder = ref(saved?.sort ?? 'asc')
  const page = ref(saved?.page ?? 0)
  const sortOptions = [
    { value: 'desc', label: 'ชื่อ ฮ-ก' },
    { value: 'newest', label: 'เพิ่มล่าสุด' },
  ]
  const patients = ref<Patient[]>([])
  const branches = ref<Branch[]>([])
  const patientBirthDates = ref<Record<number, string>>()
  const birthDateStates = ref<Record<number, 'loading' | 'ready' | 'error'>>({})
  const totalPages = ref(0),
    totalItems = ref(0)
  const loading = ref(true),
    errorMessage = ref('')
  const showCreationNotice = ref(false)
  const filteredPatients = computed(() => patients.value)
  let request = 0,
    initialized = false,
    disposed = false
  let searchTimer: number | undefined, noticeTimer: number | undefined
  const remember = () =>
    contexts.set(key, {
      query: query.value,
      branch: branch.value,
      sort: sortOrder.value,
      page: page.value,
    })
  const current = (id: number) => !disposed && id === request
  function invalidate() {
    ++request
    loading.value = true
    errorMessage.value = ''
  }
  // Date lookups are secondary: render the list first and bound concurrent profile requests.
  async function loadDates(items: Patient[], id: number) {
    let next = 0
    const worker = async () => {
      while (current(id) && next < items.length) {
        const item = items[next++]!
        try {
          const profile = await patientApi.get(item.id)
          if (!current(id)) return
          patientBirthDates.value = { ...patientBirthDates.value, [item.id]: profile.birthDate }
          birthDateStates.value[item.id] = 'ready'
        } catch {
          if (current(id)) birthDateStates.value[item.id] = 'error'
        }
      }
    }
    await Promise.all(Array.from({ length: Math.min(4, items.length) }, worker))
  }
  async function loadPatients() {
    window.clearTimeout(searchTimer)
    const id = ++request
    loading.value = true
    errorMessage.value = ''
    remember()
    try {
      const sort =
        sortOrder.value === 'asc' ? 'nameAsc' : sortOrder.value === 'desc' ? 'nameDesc' : 'newest'
      const result = await patientApi.search(
        query.value.trim(),
        page.value,
        20,
        branch.value === 'ทั้งหมด' ? '' : branch.value,
        sort,
      )
      if (!current(id)) return
      if (page.value > 0 && page.value >= result.totalPages) {
        page.value = Math.max(0, result.totalPages - 1)
        await loadPatients()
        return
      }
      patients.value = result.items
      totalPages.value = result.totalPages
      totalItems.value = result.totalItems
      patientBirthDates.value = {}
      birthDateStates.value = Object.fromEntries(result.items.map((item) => [item.id, 'loading']))
      loading.value = false
      void loadDates(result.items, id)
    } catch (error) {
      if (current(id))
        errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลผู้ป่วยไม่สำเร็จ'
    } finally {
      if (current(id)) loading.value = false
    }
  }
  async function retryLoad() {
    if (disposed) return
    if (initialized) {
      await loadPatients()
      return
    }
    invalidate()
    try {
      const list = await branchApi.list()
      if (disposed) return
      branches.value = list.filter((item) => item.active)
      if (isManager) {
        setManagerBranches(branches.value)
        const selected = selectedManagerBranch()?.code ?? 'ทั้งหมด'
        if (selected !== branch.value) {
          branch.value = selected
          page.value = 0
        }
      }
      initialized = true
      await loadPatients()
    } catch (error) {
      if (!disposed) {
        errorMessage.value = error instanceof Error ? error.message : 'โหลดสาขาไม่สำเร็จ'
        loading.value = false
      }
    }
  }
  function refreshSearch() {
    window.clearTimeout(searchTimer)
    invalidate()
    page.value = 0
    remember()
    if (initialized) searchTimer = window.setTimeout(() => void loadPatients(), 250)
  }
  function applyManagerBranch() {
    if (!isManager || !initialized) return
    const selected = selectedManagerBranch()?.code ?? 'ทั้งหมด'
    if (branch.value !== selected) branch.value = selected
  }
  async function changePage(value: number) {
    if (loading.value || value < 0 || value >= totalPages.value) return
    page.value = value
    await loadPatients()
  }
  watch([branch, sortOrder], () => {
    if (!initialized) return
    page.value = 0
    void loadPatients()
  })
  onMounted(() => {
    window.addEventListener('manager-branch-changed', applyManagerBranch)
    void retryLoad()
    if (localStorage.getItem('patientCreationHn')) {
      localStorage.removeItem('patientCreationHn')
      showCreationNotice.value = true
      noticeTimer = window.setTimeout(() => {
        showCreationNotice.value = false
      }, 3000)
    }
  })
  onBeforeUnmount(() => {
    remember()
    disposed = true
    ++request
    window.clearTimeout(searchTimer)
    window.clearTimeout(noticeTimer)
    window.removeEventListener('manager-branch-changed', applyManagerBranch)
  })
  function birthDateLabel(id: number) {
    if (birthDateStates.value[id] === 'loading') return 'กำลังโหลด…'
    if (birthDateStates.value[id] === 'error') return 'โหลดไม่สำเร็จ'
    const date = patientBirthDates.value?.[id]
    return date
      ? new Intl.DateTimeFormat('th-TH', { dateStyle: 'medium', timeZone: 'Asia/Bangkok' }).format(
          new Date(date),
        )
      : '-'
  }
  return {
    isManager,
    query,
    branch,
    sortOrder,
    sortOptions,
    patients,
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
  }
}
