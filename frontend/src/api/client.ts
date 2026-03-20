import axios, { AxiosHeaders } from 'axios'
import { useAuthStore } from '../store/authStore'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

export const api = axios.create({ baseURL })

function resolveConfiguredApiBase(): URL | null {
  if (typeof window === 'undefined') return null
  return new URL(baseURL, window.location.origin)
}

function resolveToken(): string | null {
  const fromStore = useAuthStore.getState().token
  if (fromStore) return fromStore
  if (typeof window === 'undefined') return null
  return localStorage.getItem('mrt.auth.token')
}

export function resolveApiUrl(path: string): string {
  if (/^https?:\/\//.test(path)) return path
  const apiBase = resolveConfiguredApiBase()
  if (!apiBase) return path
  if (/^\/api(?:\/|$)/.test(path)) {
    return new URL(path, apiBase.origin).toString()
  }
  if (path.startsWith('/')) {
    return new URL(path, window.location.origin).toString()
  }
  return new URL(path, apiBase.toString().endsWith('/') ? apiBase.toString() : `${apiBase.toString()}/`).toString()
}

export async function authorizedFetch(input: string, init: RequestInit = {}): Promise<Response> {
  const token = resolveToken()
  const headers = new Headers(init.headers ?? {})
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  return fetch(resolveApiUrl(input), { ...init, headers })
}

export async function downloadWithAuth(path: string, filename: string): Promise<void> {
  const response = await authorizedFetch(path)
  if (!response.ok) {
    throw new Error(`Download failed with HTTP ${response.status}`)
  }
  const blob = await response.blob()
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = filename
  link.click()
  URL.revokeObjectURL(objectUrl)
}

api.interceptors.request.use((config) => {
  const token = resolveToken()
  if (token) {
    const headers = AxiosHeaders.from(config.headers)
    headers.set('Authorization', `Bearer ${token}`)
    config.headers = headers
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      useAuthStore.getState().clearToken()
      window.dispatchEvent(new CustomEvent('auth:expired'))
    }
    return Promise.reject(error)
  }
)
