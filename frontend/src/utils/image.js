const MAX_SIDE = 1600 // enough to read Messenger text, small enough to upload quickly
const THUMB_SIDE = 160

function load(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => { URL.revokeObjectURL(url); resolve(img) }
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('unreadable')) }
    img.src = url
  })
}

function draw(img, maxSide) {
  const scale = Math.min(1, maxSide / Math.max(img.naturalWidth, img.naturalHeight))
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(img.naturalWidth * scale))
  canvas.height = Math.max(1, Math.round(img.naturalHeight * scale))
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = '#fff' // transparent PNGs would otherwise turn black as JPEG
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
  return canvas
}

/**
 * A picked or pasted screenshot, shrunk to at most 1600px on its longer side and saved as JPEG. Returns the upload
 * (blob) and a small data: URL for the preview (data: so a strict Content-Security-Policy still allows it).
 * Rejects with a message fit to show the user.
 */
export async function prepareImage(file) {
  if (!file || !file.type.startsWith('image/')) throw new Error('Please choose an image file.')
  let img
  try {
    img = await load(file)
  } catch {
    throw new Error('That image could not be read. Try a JPEG, PNG or WebP screenshot.')
  }
  const blob = await new Promise((resolve) => draw(img, MAX_SIDE).toBlob(resolve, 'image/jpeg', 0.85))
  if (!blob) throw new Error('That image could not be prepared.')
  if (blob.size > 5 * 1024 * 1024) throw new Error('That image is too large. Crop it to the conversation.')
  return { blob, thumb: draw(img, THUMB_SIDE).toDataURL('image/jpeg', 0.7) }
}
