const base = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
import { authHeaders } from './auth'

export async function workflowRequest<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || `ไม่สามารถดำเนินการได้ (${response.status})`)
  }
  return response.status === 204 ? undefined as T : response.json()
}
