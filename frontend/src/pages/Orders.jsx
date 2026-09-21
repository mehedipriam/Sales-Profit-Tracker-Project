import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import Pager from '../components/Pager'
import { STATUSES, STATUS_LABEL, dateTime, money } from '../utils/format'

export default function Orders() {
  const [platformId, setPlatformId] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [platforms, setPlatforms] = useState([])
  const [error, setError] = useState('')

  useEffect(() => {
    api.get('/platforms').then((res) => setPlatforms(res.data)).catch(() => {})
  }, [])

  const load = useCallback(() => {
    api
      .get('/orders', { params: { platformId: platformId || undefined, status: status || undefined, page } })
      .then((res) => { setData(res.data); setError('') })
      .catch((err) => setError(errorMessage(err)))
  }, [platformId, status, page])
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { load() }, [load])

  const changeStatus = async (o, next) => {
    try {
      await api.patch(`/orders/${o.id}/status`, { status: next })
      load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const remove = async (o) => {
    if (!window.confirm(`Delete order #${o.id} for ${o.customerName}? This cannot be undone.`)) return
    try {
      await api.delete(`/orders/${o.id}`)
      load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <section>
      <div className="page-head">
        <h1>Orders</h1>
        <Link className="btn" to="/orders/new">+ New sale</Link>
      </div>

      <div className="filters">
        <select value={platformId} onChange={(e) => { setPlatformId(e.target.value); setPage(0) }}>
          <option value="">All platforms</option>
          {platforms.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
        <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }}>
          <option value="">All statuses</option>
          {STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
        </select>
      </div>

      {error && <p className="error" role="alert">{error}</p>}

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>#</th><th>Date</th><th>Customer</th><th>Platform</th><th className="num">Items</th>
              <th className="num">Revenue</th><th className="num">Cost</th><th className="num">Profit</th>
              <th>Status</th><th />
            </tr>
          </thead>
          <tbody>
            {data?.content.map((o) => (
              <tr key={o.id}>
                <td>{o.id}</td>
                <td>{dateTime(o.orderedAt)}</td>
                <td>{o.customerName}</td>
                <td>{o.platformName}</td>
                <td className="num">{o.itemCount}</td>
                <td className="num">{money(o.revenue)}</td>
                <td className="num">{money(o.cost)}</td>
                <td className={`num ${o.profit < 0 ? 'neg' : 'pos'}`}>{money(o.profit)}</td>
                <td>
                  <select className={`status ${o.status}`} value={o.status} aria-label={`Status of order ${o.id}`}
                          onChange={(e) => changeStatus(o, e.target.value)}>
                    {STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
                  </select>
                </td>
                <td className="row-actions">
                  <Link className="link" to={`/orders/${o.id}`}>Edit</Link>
                  <button className="link danger" onClick={() => remove(o)}>Delete</button>
                </td>
              </tr>
            ))}
            {data && data.content.length === 0 && (
              <tr><td colSpan={10} className="empty">No orders yet. Record your first sale.</td></tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager data={data} onPage={setPage} />
    </section>
  )
}
