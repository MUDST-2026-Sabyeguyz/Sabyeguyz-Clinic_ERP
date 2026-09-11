const tokenKey = 'clinic-erp-token'

export function authFetch(input: RequestInfo | URL, init: RequestInit = {}) {
  const token = typeof localStorage === 'undefined' ? '' : localStorage.getItem(tokenKey)
  if (!token && Object.keys(init).length === 0) return fetch(input)
  const headers = new Headers(init.headers)
  if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  return fetch(input, { ...init, headers })
}
