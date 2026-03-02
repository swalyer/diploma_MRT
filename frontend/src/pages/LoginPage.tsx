import { useState } from 'react'
import { api } from '../api/client'
import { useAuthStore } from '../store/authStore'

export function LoginPage() {
  const [email, setEmail] = useState('admin@demo.local')
  const [password, setPassword] = useState('Admin123!')
  const setToken = useAuthStore((s) => s.setToken)
  const login = async () => {
    const response = await api.post('/auth/login', { email, password })
    setToken(response.data.token)
  }
  return <div><h2>Login</h2><input value={email} onChange={(e)=>setEmail(e.target.value)} /><input type="password" value={password} onChange={(e)=>setPassword(e.target.value)} /><button onClick={login}>Sign In</button></div>
}
