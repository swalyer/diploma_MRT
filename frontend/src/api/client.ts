import axios from 'axios'
import { useAuthStore } from '../store/authStore'

export const api = axios.create({ baseURL: 'http://localhost:8080/api' })

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token
  if (token) config.headers.Authorization = `Bearer ${token}`
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
