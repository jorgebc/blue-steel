/**
 * Saves a Blob to the user's disk under the given filename by briefly mounting
 * an object-URL anchor and clicking it. Revokes the object URL afterwards so the
 * blob can be garbage-collected. Pure DOM side effect — no React, no fetch.
 */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}
