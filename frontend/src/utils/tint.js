const HUES = [210, 160, 270, 35, 330, 185, 95]

/** A steady colour per name, so the same category, product or customer always gets the same tile. */
const hue = (text) => HUES[[...(text || '?')].reduce((n, c) => n + c.charCodeAt(0), 0) % HUES.length]

/** Inline style for a soft tinted background with matching text. */
export const tint = (text) => ({ background: `hsl(${hue(text)} 70% 60% / 0.18)`, color: `hsl(${hue(text)} 80% 68%)` })
