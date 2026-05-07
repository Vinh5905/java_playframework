/**
 * app.js - Logic chính của Chat App
 *
 * State management và UI rendering tập trung ở đây.
 * Các file khác (api.js, websocket.js) chỉ lo networking.
 */

// ────────────────────────────────────────────────────────────────
// STATE - Trạng thái toàn cục của app
// ────────────────────────────────────────────────────────────────
const STATE = {
  accounts: [],           // Tất cả accounts từ server/mock
  currentAccount: null,   // Account đang dùng
  conversations: [],      // Danh sách conversations
  messages: {},           // { convId: [messages] }
  activeConvId: null,     // Conversation đang hiển thị
  onlineUsers: new Set(), // Set<userId> đang online
  typingUsers: {},        // { convId: { userId: timeoutId } }
  settings: {},           // User settings
};

// Object APP expose ra để websocket.js gọi vào
const APP = {
  receiveMessage,
  setTyping,
  setPresence,
  appendBotChunk,
};

// ────────────────────────────────────────────────────────────────
// KHỞI TẠO
// ────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
  console.log('[App] Starting...');

  // 1. Load accounts
  STATE.accounts = await getAccounts();
  STATE.onlineUsers = new Set(MOCK.onlineUsers);  // Mock: Florencio và Bot online

  // 2. Khôi phục account từ localStorage (nếu có)
  const savedId = parseInt(localStorage.getItem('currentAccountId'));
  const savedAccount = STATE.accounts.find(a => a.id === savedId);

  if (savedAccount) {
    // Có account đã lưu → tự động đăng nhập
    await selectAccount(savedAccount);
  } else {
    // Chưa có account → hiện modal chọn
    showAccountSwitcher();
  }
});

// ────────────────────────────────────────────────────────────────
// ACCOUNT MANAGEMENT
// ────────────────────────────────────────────────────────────────

function showAccountSwitcher() {
  renderAccountList();
  document.getElementById('accountOverlay').classList.add('visible');
}

function hideAccountSwitcher() {
  document.getElementById('accountOverlay').classList.remove('visible');
}

function renderAccountList() {
  const list = document.getElementById('accountList');
  list.innerHTML = STATE.accounts.map(acc => `
    <div
      class="account-item ${STATE.currentAccount?.id === acc.id ? 'selected' : ''}"
      onclick="selectAccount(${JSON.stringify(acc).replace(/"/g, '&quot;')})"
    >
      ${renderAvatarHTML(acc.name, 'avatar-md')}
      <div class="account-item-info">
        <div class="name">${acc.name}</div>
        <div class="username">@${acc.username}</div>
      </div>
      ${acc.isBot ? '<span class="bot-badge">🤖 Bot</span>' : ''}
    </div>
  `).join('');
}

async function selectAccount(account) {
  STATE.currentAccount = account;
  localStorage.setItem('currentAccountId', account.id);

  // Gọi backend để switch session (Week 2+ mới có tác dụng thật)
  await switchAccount(account.id);

  // Connect WebSocket (Week 4 mới thực sự kết nối)
  WS.connect(account.id);

  hideAccountSwitcher();
  updateCurrentAccountUI();
  await loadConversations();
}

function updateCurrentAccountUI() {
  const acc = STATE.currentAccount;
  if (!acc) return;

  const barEl = document.getElementById('currentAccountBar');
  const nameEl = document.getElementById('currentAccountName');
  const avatarEl = document.getElementById('currentAccountAvatar');

  nameEl.textContent = acc.name;
  avatarEl.outerHTML = renderAvatarHTML(acc.name, 'avatar-sm') // replace with proper avatar
    .replace('<div', '<div id="currentAccountAvatar"');
  // Simple approach: just set innerHTML
  document.getElementById('currentAccountBar').querySelector('.avatar-sm').style.background = avatarColor(acc.name);
  document.getElementById('currentAccountBar').querySelector('.avatar-sm').textContent = initials(acc.name);
}

// ────────────────────────────────────────────────────────────────
// CONVERSATIONS
// ────────────────────────────────────────────────────────────────

async function loadConversations() {
  STATE.conversations = await getConversations();
  renderConversations(STATE.conversations);
  updateUnreadBadge();
}

function renderConversations(convs) {
  const list = document.getElementById('convList');

  if (!convs || convs.length === 0) {
    list.innerHTML = '<div class="loading-state">No conversations yet</div>';
    return;
  }

  list.innerHTML = convs.map(conv => {
    const partner = getAccountById(conv.participantId);
    if (!partner) return '';

    const isOnline = STATE.onlineUsers.has(partner.id);
    const isActive = conv.id === STATE.activeConvId;

    return `
      <div class="conv-item ${isActive ? 'active' : ''}" onclick="openConversation(${conv.id})">
        <div class="conv-avatar-wrap">
          ${renderAvatarHTML(partner.name, 'avatar-lg')}
          <div class="status-dot ${isOnline ? 'online' : ''}"></div>
        </div>
        <div class="conv-info">
          <div class="conv-top">
            <span class="conv-name">${partner.name}</span>
            <span class="conv-time">${conv.lastTime}</span>
          </div>
          <div class="conv-last">${conv.lastMessage}</div>
          <div class="tags">
            ${(conv.tags || []).map(t => `<span class="tag tag-${t.color}">${t.label}</span>`).join('')}
          </div>
        </div>
      </div>
    `;
  }).join('');
}

function filterConversations(query) {
  if (!query.trim()) {
    renderConversations(STATE.conversations);
    return;
  }
  const q = query.toLowerCase();
  const filtered = STATE.conversations.filter(conv => {
    const partner = getAccountById(conv.participantId);
    return partner?.name.toLowerCase().includes(q) ||
           conv.lastMessage.toLowerCase().includes(q);
  });
  renderConversations(filtered);
}

function updateUnreadBadge() {
  const total = STATE.conversations.reduce((sum, c) => sum + (c.unread || 0), 0);
  document.getElementById('unreadBadge').textContent = total;
}

// ────────────────────────────────────────────────────────────────
// MESSAGES
// ────────────────────────────────────────────────────────────────

async function openConversation(convId) {
  STATE.activeConvId = convId;

  const conv = STATE.conversations.find(c => c.id === convId);
  const partner = conv ? getAccountById(conv.participantId) : null;
  if (!partner) return;

  // Mark as read
  if (conv) conv.unread = 0;
  updateUnreadBadge();
  renderConversations(STATE.conversations);

  // Show chat window
  document.getElementById('emptyState').style.display = 'none';
  document.getElementById('chatWindow').style.display = 'flex';

  // Update header
  renderChatHeader(partner);

  // Load messages
  if (!STATE.messages[convId]) {
    STATE.messages[convId] = await getMessages(convId);
  }
  renderMessages();
}

function renderChatHeader(partner) {
  const isOnline = STATE.onlineUsers.has(partner.id);

  const avatarEl = document.getElementById('chatPartnerAvatar');
  avatarEl.style.background = avatarColor(partner.name);
  avatarEl.textContent = initials(partner.name);

  document.getElementById('chatPartnerName').textContent = partner.name;

  const statusEl = document.getElementById('chatPartnerStatus');
  statusEl.textContent = isOnline ? 'Online' : 'Offline';
  statusEl.className = `chat-partner-status ${isOnline ? 'online' : ''}`;
}

function renderMessages() {
  const area = document.getElementById('messagesArea');
  const msgs = STATE.messages[STATE.activeConvId] || [];
  const currentId = STATE.currentAccount?.id;

  if (msgs.length === 0) {
    area.innerHTML = '<div class="loading-state" style="flex:1;display:flex;align-items:center;justify-content:center;">No messages yet. Say hi! 👋</div>';
    return;
  }

  // Group consecutive messages from same sender
  const groups = [];
  msgs.forEach(msg => {
    const isSent = msg.senderId === 0 || msg.senderId === currentId;
    const last = groups[groups.length - 1];
    if (last && last.isSent === isSent && last.senderId === msg.senderId) {
      last.messages.push(msg);
    } else {
      groups.push({ senderId: msg.senderId, isSent, messages: [msg] });
    }
  });

  area.innerHTML = groups.map(group => {
    const senderAcc = group.isSent ? STATE.currentAccount : getAccountById(group.senderId);
    const lastMsg = group.messages[group.messages.length - 1];

    const bubbles = group.messages.map(msg =>
      `<div class="msg-bubble">${escapeHtml(msg.text)}</div>`
    ).join('');

    const avatarHtml = senderAcc
      ? renderAvatarHTML(senderAcc.name, 'avatar-msg')
      : '<div class="avatar-msg" style="background:#999">?</div>';

    return `
      <div class="msg-group ${group.isSent ? 'sent' : 'received'}">
        <div class="msg-row">
          ${avatarHtml}
          <div style="display:flex;flex-direction:column;gap:4px;">${bubbles}</div>
        </div>
        <div class="msg-time">${lastMsg.time}</div>
      </div>
    `;
  }).join('');

  // Scroll to bottom
  area.scrollTop = area.scrollHeight;
}

async function sendMessage() {
  const input = document.getElementById('msgInput');
  const text = input.value.trim();
  if (!text || !STATE.activeConvId) return;

  input.value = '';

  const msg = await sendMessage_api(STATE.activeConvId, text);

  // Thêm vào state ngay (optimistic update)
  if (!STATE.messages[STATE.activeConvId]) {
    STATE.messages[STATE.activeConvId] = [];
  }
  STATE.messages[STATE.activeConvId].push({
    id: msg.id || Date.now(),
    senderId: STATE.currentAccount?.id || 0,
    text,
    time: new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }),
  });

  renderMessages();

  // Update conversation preview
  const conv = STATE.conversations.find(c => c.id === STATE.activeConvId);
  if (conv) {
    conv.lastMessage = text;
    conv.lastTime = 'Just now';
    renderConversations(STATE.conversations);
  }

  // Stop typing indicator
  WS.sendTyping(STATE.activeConvId, false);

  // Auto reply từ bot (mock - Week 7 sẽ có OpenAI)
  const partner = STATE.conversations.find(c => c.id === STATE.activeConvId);
  const partnerAcc = partner ? getAccountById(partner.participantId) : null;
  if (partnerAcc?.isBot && CONFIG.USE_MOCK) {
    simulateBotReply(STATE.activeConvId, partnerAcc);
  }
}

// Avoid name collision with api.js sendMessage
async function sendMessage_api(convId, text) {
  return await sendMessage(convId, text);
}

// ────────────────────────────────────────────────────────────────
// REAL-TIME HANDLERS (Called by websocket.js)
// ────────────────────────────────────────────────────────────────

function receiveMessage(convId, message) {
  if (!STATE.messages[convId]) STATE.messages[convId] = [];
  STATE.messages[convId].push(message);

  if (convId === STATE.activeConvId) {
    renderMessages();
  } else {
    // Update unread count
    const conv = STATE.conversations.find(c => c.id === convId);
    if (conv) { conv.unread = (conv.unread || 0) + 1; }
    updateUnreadBadge();
    renderConversations(STATE.conversations);
  }
}

function setTyping(convId, userId, isTyping) {
  if (convId !== STATE.activeConvId) return;
  const partner = getAccountById(userId);
  const bar = document.getElementById('typingBar');
  const txt = document.getElementById('typingText');

  if (isTyping && partner) {
    txt.textContent = `${partner.name} is typing...`;
    bar.style.display = 'flex';
  } else {
    bar.style.display = 'none';
  }
}

function setPresence(userId, status) {
  if (status === 'online') {
    STATE.onlineUsers.add(userId);
  } else {
    STATE.onlineUsers.delete(userId);
  }

  // Update conversation list dots
  renderConversations(STATE.conversations);

  // Update header if current chat partner
  const conv = STATE.conversations.find(c => c.id === STATE.activeConvId);
  if (conv && conv.participantId === userId) {
    const partner = getAccountById(userId);
    if (partner) renderChatHeader(partner);
  }
}

function appendBotChunk(convId, chunk, isDone) {
  // Week 7: streaming bot response
  // For now, no-op
}

// ────────────────────────────────────────────────────────────────
// TYPING INDICATOR (sent to server)
// ────────────────────────────────────────────────────────────────
let typingTimer = null;

function handleTyping() {
  if (!STATE.activeConvId) return;
  WS.sendTyping(STATE.activeConvId, true);

  clearTimeout(typingTimer);
  typingTimer = setTimeout(() => {
    WS.sendTyping(STATE.activeConvId, false);
  }, CONFIG.TYPING_DEBOUNCE_MS);
}

function handleInputKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    sendMessage();
  }
}

// ────────────────────────────────────────────────────────────────
// BOT MOCK REPLY (Week 7 sẽ thay bằng OpenAI streaming)
// ────────────────────────────────────────────────────────────────
function simulateBotReply(convId, botAcc) {
  const replies = [
    'That\'s a great question! Let me think about that...',
    'In Play Framework, you can handle this with CompletionStage for async operations.',
    'I recommend checking the Play documentation for more details on this topic.',
    'Great! Would you like me to show you a code example?',
  ];
  const reply = replies[Math.floor(Math.random() * replies.length)];

  // Show typing indicator
  setTyping(convId, botAcc.id, true);

  setTimeout(() => {
    setTyping(convId, botAcc.id, false);
    receiveMessage(convId, {
      id: Date.now(),
      senderId: botAcc.id,
      text: reply,
      time: new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }),
    });
  }, 1500);
}

// ────────────────────────────────────────────────────────────────
// UI UTILITIES
// ────────────────────────────────────────────────────────────────

/** Tìm account theo ID */
function getAccountById(id) {
  return STATE.accounts.find(a => a.id === id) ?? null;
}

/** Generate màu từ tên (deterministic) */
const AVATAR_COLORS = [
  '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4',
  '#FFEAA7', '#DDA0DD', '#98D8C8', '#F7DC6F',
  '#BB8FCE', '#85C1E9', '#82E0AA', '#F0B27A',
];

function avatarColor(name) {
  const hash = [...name].reduce((acc, c) => acc + c.charCodeAt(0), 0);
  return AVATAR_COLORS[hash % AVATAR_COLORS.length];
}

function initials(name) {
  return name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2);
}

function renderAvatarHTML(name, cls) {
  const color = avatarColor(name);
  const inits = initials(name);
  return `<div class="${cls}" style="background:${color}">${inits}</div>`;
}

function escapeHtml(text) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function showNewConvModal() {
  alert('📝 New conversation (feature coming in Week 3)');
}

function toggleEmojiPicker() {
  alert('😊 Emoji picker coming soon!');
}
