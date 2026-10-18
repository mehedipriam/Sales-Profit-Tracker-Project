const KEY = 'spt_agent_chat'
const MAX_MESSAGES = 30

/**
 * The order assistant's conversation, kept in this tab's sessionStorage so a reload does not lose a draft. It goes
 * when the tab closes or the user logs out, and is only ever read back for the same user. Storage can be blocked or
 * full, so every access is allowed to fail.
 */
export function loadChat(userId) {
  try {
    const saved = JSON.parse(sessionStorage.getItem(KEY) ?? 'null')
    if (!saved || saved.userId !== userId || !Array.isArray(saved.messages)) return { open: false, messages: [], maxKey: 0 }
    const messages = saved.messages.map((m) => (m.status === 'saving'
      // A reload mid-save: the order may or may not have been stored.
      ? { ...m, status: 'open', error: 'Reloaded while saving. Check Orders before confirming again.' }
      : m))
    const keys = messages.flatMap((m) => [m.key, ...(m.draft?.lines ?? []).map((l) => l.key)])
    return { open: Boolean(saved.open), messages, maxKey: Math.max(0, ...keys.filter(Number.isFinite)) }
  } catch {
    return { open: false, messages: [], maxKey: 0 }
  }
}

export function saveChat(userId, open, messages) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify({ userId, open, messages: messages.slice(-MAX_MESSAGES) }))
  } catch {
    // Not saved: the chat still works, it just will not survive a reload.
  }
}

export function clearChat() {
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    // Nothing to clear.
  }
}
