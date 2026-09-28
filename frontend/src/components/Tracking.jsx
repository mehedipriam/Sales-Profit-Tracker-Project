/**
 * A tracking code, as a link to the courier's tracking page when there is one. A pasted tracking link shows as
 * "Track parcel" rather than the whole address.
 */
export default function Tracking({ number, link }) {
  if (!number) return null
  const isLink = link && link === number
  if (!link) return <span>{number}</span>
  return (
    <a href={link} target="_blank" rel="noopener noreferrer" title={number}>{isLink ? 'Track parcel ↗' : number}</a>
  )
}
