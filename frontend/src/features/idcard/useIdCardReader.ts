import { ref } from 'vue'
import { ApiError, idCardApi, type IdCardReadView, type IdCardWorkstation } from '@/api/client'
import { agentLocal } from './agentLocal'

type Api = Pick<typeof idCardApi, 'workstations' | 'start' | 'result'>
type LocalAgent = Pick<typeof agentLocal, 'status'>
type State = 'idle' | 'waiting' | 'reading' | 'done' | 'failed'

const STORAGE_KEY = 'idCardWorkstationId'

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms))

function stored(): number | null {
  try {
    const value = Number(localStorage.getItem(STORAGE_KEY))
    return Number.isFinite(value) && value > 0 ? value : null
  } catch {
    return null
  }
}

export function useIdCardReader(options: { api?: Api; local?: LocalAgent; intervalMs?: number; timeoutMs?: number } = {}) {
  const api = options.api ?? idCardApi
  const local = options.local ?? agentLocal
  const intervalMs = options.intervalMs ?? 700
  const timeoutMs = options.timeoutMs ?? 50_000

  const workstations = ref<IdCardWorkstation[]>([])
  const selectedId = ref<number | null>(null)
  const state = ref<State>('idle')
  const message = ref('')
  let generation = 0

  async function load(branchCode?: string) {
    // asked together: the list comes from the backend, the agent answers (or not) from this PC
    const [list, agent] = await Promise.all([
      api.workstations(branchCode).catch((): IdCardWorkstation[] => []),
      local.status().catch(() => null),
    ])
    workstations.value = list
    const online = workstations.value.filter((w) => w.online)
    const remembered = stored()
    // the agent installed on this PC is the reader at this counter; it beats a choice remembered from an earlier visit
    const own = agent?.paired && agent.workstationId !== null
      ? workstations.value.find((w) => w.id === agent.workstationId && w.status === 'Active')
      : undefined
    if (own) selectedId.value = own.id
    else if (remembered && workstations.value.some((w) => w.id === remembered)) selectedId.value = remembered
    else if (online.length === 1) selectedId.value = online[0]!.id
    else if (workstations.value.length === 1) selectedId.value = workstations.value[0]!.id
    else selectedId.value = null
  }

  function select(id: number) {
    selectedId.value = id
    try {
      localStorage.setItem(STORAGE_KEY, String(id))
    } catch {
      // the choice is only a convenience
    }
  }

  function cancel() {
    generation++
    state.value = 'idle'
    message.value = ''
  }

  function fail(text: string): null {
    state.value = 'failed'
    message.value = text
    return null
  }

  async function read(): Promise<IdCardReadView | null> {
    const mine = ++generation
    message.value = ''
    if (selectedId.value === null) {
      return fail('ยังไม่ได้เลือกเครื่องอ่านบัตร หรือยังไม่มีเครื่องอ่านบัตรของสาขานี้')
    }
    state.value = 'waiting'
    let id: string
    try {
      id = (await api.start(selectedId.value, 'REGISTER')).id
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        return fail('โปรแกรมตัวกลางของเครื่องนี้ออฟไลน์ ตรวจว่าเปิดโปรแกรมที่เคาน์เตอร์แล้ว')
      }
      if (error instanceof ApiError && error.status === 429) return fail('สั่งอ่านบัตรบ่อยเกินไป รอสักครู่แล้วลองใหม่')
      if (error instanceof ApiError && error.status === 403) return fail('เครื่องอ่านบัตรนี้อยู่คนละสาขากับคุณ')
      return fail(error instanceof ApiError ? error.message : 'สั่งอ่านบัตรไม่สำเร็จ')
    }
    const deadline = Date.now() + timeoutMs
    while (mine === generation) {
      await sleep(intervalMs)
      if (mine !== generation) return null
      let view: IdCardReadView
      try {
        view = await api.result(id)
      } catch (error) {
        if (mine !== generation) return null
        return fail(
          error instanceof ApiError && error.status === 404
            ? 'หมดเวลา ไม่ได้รับข้อมูลจากเครื่องอ่านบัตร'
            : 'ติดต่อระบบไม่ได้',
        )
      }
      if (mine !== generation) return null
      if (view.status === 'DONE') {
        state.value = 'done'
        return view
      }
      if (view.status === 'FAILED') return fail(view.message ?? 'อ่านบัตรไม่สำเร็จ')
      state.value = view.status === 'READING' ? 'reading' : 'waiting'
      if (Date.now() >= deadline) return fail('หมดเวลา ไม่ได้รับข้อมูลจากเครื่องอ่านบัตร')
    }
    return null
  }

  return { workstations, selectedId, state, message, load, select, read, cancel }
}
