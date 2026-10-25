import { useCallback, useEffect, useMemo, useState } from 'react'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { seesFinancials } from '../auth/roles'
import Modal from '../components/Modal'
import Stat from '../components/Stat'
import { PencilIcon, ReceiptIcon, TrashIcon, TruckIcon, TrendUpIcon } from '../components/icons'
import { resolveRange } from '../utils/dateRange'
import { money } from '../utils/format'
import { tint } from '../utils/tint'

function PlatformForm({ platform, onSaved, onCancel }) {
  const [name, setName] = useState(platform?.name ?? '')
  const [commissionPct, setCommissionPct] = useState(platform?.commissionPct ?? 0)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    const body = { name, commissionPct: commissionPct === '' ? 0 : commissionPct }
    try {
      if (platform) await api.put(`/platforms/${platform.id}`, body)
      else await api.post('/platforms', body)
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <label>Platform name<input required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} /></label>
      <label>
        Commission % (optional)
        <input type="number" min="0" max="100" step="0.01" value={commissionPct}
               onChange={(e) => setCommissionPct(e.target.value)} />
        <span className="hint">
          Charged automatically as an expense on every new order on this platform (while it is paid or pending).
          Changing it later affects new orders only.
        </span>
      </label>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button type="button" className="btn secondary" onClick={onCancel}>Cancel</button>
        <button className="btn" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
    </form>
  )
}

const TABS = [
  { value: 'this_month', label: 'This month' },
  { value: 'last_month', label: 'Last month' },
  { value: 'this_year', label: 'This year' },
  { value: 'all', label: 'All time' },
]

export default function Platforms() {
  const { user } = useAuth()
  const showMoney = seesFinancials(user)
  const [platforms, setPlatforms] = useState([])
  const [summary, setSummary] = useState(null) // the period's figures, for Owner and Admin only
  const [preset, setPreset] = useState('this_month')
  const [editing, setEditing] = useState(null)
  const [error, setError] = useState('')

  const range = useMemo(() => resolveRange({ preset }), [preset])

  const load = useCallback(() => {
    api.get('/platforms').then((res) => setPlatforms(res.data)).catch((err) => setError(errorMessage(err)))
  }, [])
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { load() }, [load])

  useEffect(() => {
    if (!showMoney) return undefined
    let live = true
    api.get('/reports/summary', { params: range })
      .then((res) => live && setSummary(res.data))
      .catch(() => live && setSummary(null))
    return () => { live = false }
  }, [showMoney, range])

  const remove = async (p) => {
    if (!window.confirm(`Remove "${p.name}"? Existing orders keep it; you just can't pick it for new sales.`)) return
    try {
      await api.delete(`/platforms/${p.id}`)
      load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const rows = new Map((summary?.byPlatform ?? []).map((r) => [r.platformId, r]))
  const revenue = Number(summary?.realized.revenue ?? 0)
  const best = [...rows.values()].filter((r) => Number(r.revenue) > 0).sort((a, b) => Number(b.revenue) - Number(a.revenue))[0]
  const commission = Number(summary?.expenses.byType.find((t) => t.type === 'PLATFORM_COMMISSION')?.total ?? 0)
  const share = (r) => (revenue > 0 && r ? (Number(r.revenue) / revenue) * 100 : 0)

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Platforms</h1>
          <p className="muted prod-sub">Where you sell, and what each one charges you</p>
        </div>
        <button className="btn" onClick={() => setEditing({})}>+ Add platform</button>
      </div>
      {error && <p className="error" role="alert">{error}</p>}

      {showMoney && summary && (
        <>
          <div className="stats kpis">
            <Stat label="Platforms" value={platforms.length} icon={<TruckIcon />} accent="blue" />
            <Stat label="Best in this period" value={best ? best.platformName : '—'} icon={<TrendUpIcon />} accent="green"
                  hint={best ? `${share(best).toFixed(0)}% of sales` : 'no paid orders yet'} />
            <Stat label="Commission" value={money(commission)} icon={<ReceiptIcon />} accent="violet"
                  hint="charged automatically" />
          </div>
          <div className="dash-tabs" role="tablist" aria-label="Period">
            {TABS.map((t) => (
              <button key={t.value} type="button" role="tab" aria-selected={preset === t.value}
                      className={preset === t.value ? 'on' : ''} onClick={() => setPreset(t.value)}>{t.label}</button>
            ))}
          </div>
        </>
      )}

      <div className="cour-grid">
        {platforms.map((p) => {
          const r = rows.get(p.id)
          const pct = Number(p.commissionPct)
          return (
            <div key={p.id} className="dash-card cour-card">
              <div className="cour-top">
                <span className="cust-av cour-tile" style={tint(p.name)} aria-hidden="true">{p.name.trim().charAt(0).toUpperCase()}</span>
                <b className="cour-name">{p.name}</b>
                <span className="cour-acts">
                  <button type="button" className="ord-act" title="Edit" aria-label={`Edit ${p.name}`} onClick={() => setEditing(p)}><PencilIcon /></button>
                  <button type="button" className="ord-act danger" title="Remove" aria-label={`Remove ${p.name}`} onClick={() => remove(p)}><TrashIcon /></button>
                </span>
              </div>
              <span className={`plat-rate${pct > 0 ? '' : ' none'}`}>{pct > 0 ? `${pct}% commission` : 'No commission'}</span>
              {showMoney && summary && (
                <>
                  <div className="cour-two">
                    <div className="cour-mini"><span>Orders</span><b>{r?.orders ?? 0}</b></div>
                    <div className="cour-mini"><span>Revenue</span><b>{money(r?.revenue ?? 0)}</b></div>
                    <div className="cour-mini"><span>Gross profit</span><b className={Number(r?.profit ?? 0) < 0 ? 'neg' : 'pos'}>{money(r?.profit ?? 0)}</b></div>
                    <div className="cour-mini"><span>Net profit</span><b className={Number(r?.netProfit ?? 0) < 0 ? 'neg' : 'pos'}>{money(r?.netProfit ?? 0)}</b></div>
                  </div>
                  <div className="plat-share">
                    {share(r).toFixed(0)}% of the period&apos;s sales
                    <div className="plat-bar"><i style={{ width: `${share(r)}%`, background: tint(p.name).color }} /></div>
                  </div>
                </>
              )}
            </div>
          )
        })}
        <button type="button" className="cour-add" onClick={() => setEditing({})}>
          <span>＋</span>
          {platforms.length === 0 ? 'No platforms yet. Add where you sell.' : 'Add a platform'}
        </button>
      </div>

      <p className="plat-hint">
        Commission is charged automatically as an expense on every new order on the platform (while it is paid or
        pending). Changing it later affects new orders only.
      </p>

      {editing && (
        <Modal title={editing.id ? 'Edit platform' : 'Add platform'} onClose={() => setEditing(null)}>
          <PlatformForm
            platform={editing.id ? editing : null}
            onSaved={() => { setEditing(null); load() }}
            onCancel={() => setEditing(null)}
          />
        </Modal>
      )}
    </section>
  )
}
