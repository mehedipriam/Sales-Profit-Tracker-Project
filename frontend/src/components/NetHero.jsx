import { money } from '../utils/format'
import { MASK } from './Reveal'
import { WalletIcon } from './icons'

/**
 * The headline net profit banner (Dashboard, Reports), with how it's worked out alongside; `change` as on Stat.
 * `hidden` masks every amount and keeps the banner out of its loss colours, which would give the figure away.
 */
export default function NetHero({ realized, expenses, netProfit, change, hidden = false }) {
  const revenue = Number(realized.revenue)
  const margin = revenue > 0 ? (Number(netProfit) / revenue) * 100 : null
  const loss = !hidden && netProfit < 0
  const m = (n) => (hidden ? MASK : money(n))

  return (
    <div className={`net-hero stacked${loss ? ' loss' : ''}`}>
      <div className="net-hero-top">
        <div className="net-hero-main">
          <span className="net-hero-label"><WalletIcon /> {loss ? 'Net loss' : 'Net profit'} · after expenses</span>
          <span className={`net-hero-value${hidden ? ' masked' : ''}`}>{m(netProfit)}</span>
          {change}
        </div>
        <div className="net-hero-gross">
          <span>{!hidden && Number(realized.profit) < 0 ? 'Gross loss' : 'Gross profit'}</span>
          <b>{m(realized.profit)}</b>
          <small>revenue − cost</small>
        </div>
      </div>
      {/* revenue − cost + delivery charged − expenses */}
      <ol className="net-flow" aria-label="How net profit is worked out">
        <li><span>Revenue</span><b>{m(realized.revenue)}</b></li>
        <li className="op" aria-hidden="true">−</li>
        <li><span>Cost of goods</span><b>{m(realized.cost)}</b></li>
        <li className="op" aria-hidden="true">+</li>
        <li><span>Delivery</span><b>{m(realized.delivery)}</b></li>
        <li className="op" aria-hidden="true">−</li>
        <li><span>Expenses</span><b>{m(expenses)}</b></li>
      </ol>
      <span className="net-hero-sub">
        {margin === null ? 'No paid sales yet' : hidden ? `${MASK} of revenue` : `${margin.toFixed(1)}% of revenue`}
        {' · '}{realized.orders} paid order{realized.orders === 1 ? '' : 's'}
      </span>
    </div>
  )
}
