import { Route, Routes, Link } from 'react-router-dom'
import { LoginPage } from '../pages/LoginPage'
import { CasesPage } from '../pages/CasesPage'
import { CreateCasePage } from '../pages/CreateCasePage'
import { CaseDetailsPage } from '../pages/CaseDetailsPage'
import { AdminPage } from '../pages/AdminPage'

export function App() {
  return <div style={{padding:16}}>
    <nav style={{display:'flex', gap:12}}><Link to="/login">Login</Link><Link to="/cases">Cases</Link><Link to="/cases/new">New Case</Link><Link to="/admin">Admin</Link></nav>
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/cases" element={<CasesPage />} />
      <Route path="/cases/new" element={<CreateCasePage />} />
      <Route path="/cases/:id" element={<CaseDetailsPage />} />
      <Route path="/admin" element={<AdminPage />} />
      <Route path="*" element={<CasesPage />} />
    </Routes>
  </div>
}
