// The card reader agent installed on this PC listens on this address and answers only the clinic's own site.
// It is used for two things: telling whether it is installed and paired, and pairing it with a code.
// Reading a card never goes through here; that always goes through the backend (audit, rate limit, branch check).
export const AGENT_URL = 'http://127.0.0.1:47631'

export type LocalAgentStatus = {
  paired: boolean
  workstationName: string | null
  branchCode: string | null
  workstationId: number | null
  version: string
}

export type LocalPairResult = { ok: true; status: LocalAgentStatus } | { ok: false; message: string }

async function call(path: string, init: RequestInit, timeoutMs: number): Promise<Response> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    return await fetch(`${AGENT_URL}${path}`, { ...init, signal: controller.signal, cache: 'no-store' })
  } finally {
    clearTimeout(timer)
  }
}

function isStatus(value: unknown): value is LocalAgentStatus {
  return typeof value === 'object' && value !== null && typeof (value as LocalAgentStatus).paired === 'boolean'
}

export const agentLocal = {
  /** The agent on this PC, or null when there is none (or it does not allow this site). Never throws. */
  async status(timeoutMs = 1500): Promise<LocalAgentStatus | null> {
    try {
      const response = await call('/status', { method: 'GET' }, timeoutMs)
      if (!response.ok) return null
      const body: unknown = await response.json()
      return isStatus(body) ? body : null
    } catch {
      return null
    }
  },

  /** Hands a one-time pairing code to the agent on this PC. Never throws. */
  async pair(code: string, timeoutMs = 30_000): Promise<LocalPairResult> {
    let response: Response
    try {
      response = await call(
        '/pair',
        { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ code }) },
        timeoutMs,
      )
    } catch {
      return { ok: false, message: 'ติดต่อโปรแกรมตัวกลางบนเครื่องนี้ไม่ได้ ตรวจว่าเปิดโปรแกรมอยู่' }
    }
    let body: unknown = null
    try {
      body = await response.json()
    } catch {
      // the answer below covers a reply that is not JSON
    }
    if (response.ok && isStatus(body)) return { ok: true, status: body }
    if (response.status === 403) {
      return { ok: false, message: 'โปรแกรมตัวกลางไม่อนุญาตให้เว็บนี้จับคู่ ตรวจค่าเว็บที่อนุญาตตอนติดตั้งโปรแกรม' }
    }
    const reason = (body as { error?: unknown } | null)?.error
    return { ok: false, message: typeof reason === 'string' && reason ? reason : 'จับคู่ไม่สำเร็จ ลองใหม่อีกครั้ง' }
  },
}
