export default function Pager({ data, onPage }) {
  if (!data) return null
  return (
    <div className="pager">
      <span className="muted">{data.totalElements} total</span>
      <div className="spacer" />
      <button className="btn secondary" disabled={data.page <= 0} onClick={() => onPage(data.page - 1)}>
        Previous
      </button>
      <span>Page {data.totalPages === 0 ? 0 : data.page + 1} of {data.totalPages}</span>
      <button
        className="btn secondary"
        disabled={data.page + 1 >= data.totalPages}
        onClick={() => onPage(data.page + 1)}
      >
        Next
      </button>
    </div>
  )
}
