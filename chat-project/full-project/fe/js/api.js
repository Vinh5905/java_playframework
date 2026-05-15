/**
 * api.js - Tất cả HTTP API calls, với mock fallback
 *
 * Pattern: Nếu CONFIG.USE_MOCK = true → trả mock data ngay
 *          Nếu = false → gọi API, nếu fail → warn + trả mock
 *
 * Tuần 1: USE_MOCK = true, tất cả return mock data
 * Tuần 2: getAccounts(), getConversations() gọi API thật
 * Tuần 3: getMessages(), sendMessage() gọi API thật
 * Tuần 4+: Thêm WebSocket
 */

// ────────────────────────────────────────────────────────────────
// Helper: fetch với timeout và fallback về mock
// ────────────────────────────────────────────────────────────────
async function apiFetch(path, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), CONFIG.API_TIMEOUT_MS);

  try {
    const res = await fetch(`${CONFIG.API_BASE}${path}`, {
      ...options,
      signal: controller.signal,
      headers: {
        'Content-Type': 'application/json',
        ...options.headers,
      },
    });
    clearTimeout(timer);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } catch (err) {
    clearTimeout(timer);
    if (err.name === 'AbortError') {
      console.warn(`[API] Timeout calling ${path} → fallback to mock`);
    } else {
      console.warn(`[API] ${path} failed: ${err.message} → fallback to mock`);
    }
    return null;  // Caller xử lý null bằng cách dùng mock
  }
}

function currentAccountId() {
  return parseInt(localStorage.getItem('currentAccountId')) || 1;
}

// ────────────────────────────────────────────────────────────────
// ACCOUNTS
// ────────────────────────────────────────────────────────────────

/** Lấy danh sách tất cả accounts (Week 1: mock, Week 2: DB) */
async function getAccounts() {
  if (CONFIG.USE_MOCK) return MOCK.accounts;

  const data = await apiFetch('/api/accounts');
  return data ?? MOCK.accounts;
}

/** Switch sang account khác (Week 1: localStorage, Week 2: server session) */
async function switchAccount(accountId) {
  if (CONFIG.USE_MOCK) {
    // Mock: chỉ lưu vào localStorage
    return { success: true, accountId };
  }

  const data = await apiFetch(`/api/accounts/switch/${accountId}`, { method: 'POST' });
  return data ?? { success: true, accountId };
}

/** Lấy current account từ server (Week 2+) */
async function getCurrentAccount() {
  if (CONFIG.USE_MOCK) {
    const id = parseInt(localStorage.getItem('currentAccountId')) || 1;
    return MOCK.accounts.find(a => a.id === id) ?? MOCK.accounts[0];
  }

  const data = await apiFetch('/api/accounts/current');
  return data ?? MOCK.accounts[0];
}

// ────────────────────────────────────────────────────────────────
// CONVERSATIONS
// ────────────────────────────────────────────────────────────────

/** Lấy danh sách conversations của current user (Week 3+: từ DB) */
async function getConversations() {
  if (CONFIG.USE_MOCK) return MOCK.conversations;

  const data = await apiFetch(`/api/conversations?accountId=${currentAccountId()}`);
  return data ?? MOCK.conversations;
}

// ────────────────────────────────────────────────────────────────
// MESSAGES
// ────────────────────────────────────────────────────────────────

/** Lấy messages của một conversation (Week 3+: từ MongoDB) */
async function getMessages(conversationId) {
  if (CONFIG.USE_MOCK) {
    return MOCK.messages[conversationId] ?? [];
  }

  const path = conversationId === 'global'
    ? '/api/global/messages'
    : `/api/conversations/${conversationId}/messages`;
  const data = await apiFetch(path);
  return data ?? MOCK.messages[conversationId] ?? [];
}

/** Gửi tin nhắn (Week 3+: lưu DB, Week 4+: push WebSocket) */
async function apiSendMessage(conversationId, text) {
  const msg = {
    id: Date.now(),
    senderId: currentAccountId(),
    text,
    time: new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }),
  };

  if (CONFIG.USE_MOCK) {
    // Mock: thêm vào local state
    if (!MOCK.messages[conversationId]) MOCK.messages[conversationId] = [];
    MOCK.messages[conversationId].push(msg);
    return msg;
  }

  const data = await apiFetch(`/api/conversations/${conversationId}/messages?senderId=${currentAccountId()}`, {
    method: 'POST',
    body: JSON.stringify({ text }),
  });
  return data ?? msg;
}

// ────────────────────────────────────────────────────────────────
// SETTINGS (Week 8)
// ────────────────────────────────────────────────────────────────

async function getSettings() {
  if (CONFIG.USE_MOCK) {
    return {
      typingIndicators: true,
      showOnlineStatus: true,
      notifications: true,
    };
  }
  const data = await apiFetch(`/api/settings?userId=${currentAccountId()}`);
  return data ?? { typingIndicators: true, showOnlineStatus: true, notifications: true };
}

async function updateSetting(key, value) {
  if (CONFIG.USE_MOCK) return { success: true };
  return await apiFetch(`/api/settings?userId=${currentAccountId()}`, {
    method: 'PATCH',
    body: JSON.stringify({ [key]: value }),
  }) ?? { success: true };
}
