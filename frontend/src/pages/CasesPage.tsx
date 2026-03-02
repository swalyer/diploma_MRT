import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { CaseItem } from '../types'

export function CasesPage() {
  const [items, setItems] = useState<CaseItem[]>([])
  useEffect(() => { api.get('/cases').then((r) => setItems(r.data)) }, [])
  return <div><h2>Cases List</h2>{items.map((c)=><div key={c.id}><Link to={`/cases/${c.id}`}>Case #{c.id}</Link> {c.modality} {c.status}</div>)}</div>
}
