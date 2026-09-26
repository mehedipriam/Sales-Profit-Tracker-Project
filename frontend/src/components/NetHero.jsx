import { money } from '../utils/format'
import { WalletIcon } from './icons'

/** The headline net profit banner (Dashboard, Reports), with how it's worked out alongside. */
export default function NetHero({ realized, expenses, netProfit }) {
  const revenue = Number(realized.revenue)
  const margin = revenue > 0 ? (Number(netProfit) / revenue) * 100 : null
  const loss = netProfit < 0

  return (
    <div className={`net-hero${loss ? ' loss' : ''}`}>
      <div className="net-hero-main">
        <span className="net-hero-label"><WalletIcon /> {loss ? 'Net loss' : 'Net profit'}</span>
        <span className="net-hero-value">{money(netProfit)}</span>
        <span className="net-hero-sub">
          {margin === null ? 'No paid sales yet' : `${margin.toFixed(1)}% of revenue`}
          {' · '}{realized.orders} paid order{realized.orders === 1 ? '' : 's'}
        </span>
      </div>
      {/* revenue − cost + delivery charged − expenses */}
      <ol className="net-flow" aria-label="How net profit is worked out">
        <li><span>Revenue</span><b>{money(realized.revenue)}</b></li>
        <li className="op" aria-hidden="true">−</li>
        <li><span>Cost of goods</span><b>{money(realized.cost)}</b></li>
        <li className="op" aria-hidden="true">+</li>
        <li><span>Delivery</span><b>{money(realized.delivery)}</b></li>
        <li className="op" aria-hidden="true">−</li>
        <li><span>Expenses</span><b>{money(expenses)}</b></li>
      </ol>
    </div>
  )
}
