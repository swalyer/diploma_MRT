import axios, { AxiosHeaders } from 'axios'
import { useAuthStore } from '../store/authStore'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

export const api = axios.create({ baseURL })

function resolveToken(): string | null {
  const fromStore = useAuthStore.getState().token
  if (fromStore) return fromStore
  if (typeof window === 'undefined') return null
  return localStorage.getItem('mrt.auth.token')
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
