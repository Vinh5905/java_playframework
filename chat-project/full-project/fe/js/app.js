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
  botStreams: {},         // { convId: messageId }
};

// Object APP expose ra để websocket.js gọi vào
const APP = {
  receiveMessage,
  setTyping,
  setPresence,
  setPresenceSnapshot,
  appendBotChunk,
};

// ────────────────────────────────────────────────────────────────
// KHỞI TẠO
// ────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
  console.log('[App] Starting...');

  // 1. Load accounts
  STATE.accounts = await getAccounts();
  STATE.onlineUsers = new Set();

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
  STATE.onlineUsers = new Set([account.id]);

  // Connect WebSocket (Week 4 mới thực sự kết nối)
  WS.disconnect();
  WS.connect(account.id);
  WS.connectGlobal(account.id);

  hideAccountSwitcher();
  updateCurrentAccountUI();
  await loadSettings();
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
  const directConversations = await getConversations();
  const hasGlobal = directConversations.some(conv => String(conv.id) === 'global');
  STATE.conversations = hasGlobal
    ? directConversations
    : [{ id: 'global', name: 'Global Chat', isGlobal: true, lastMessage: 'Everyone can chat here', lastTime: 'Now', unread: 0, tags: [{ label: 'Global', color: 'blue' }] }, ...directConversations];
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
    const partner = conv.isGlobal ? null : getAccountById(conv.participantId);
    if (!conv.isGlobal && !partner) return '';

    const displayName = conv.isGlobal ? conv.name : partner.name;
    const isOnline = conv.isGlobal || STATE.onlineUsers.has(partner.id);
    const isActive = String(conv.id) === String(STATE.activeConvId);
    const clickArg = JSON.stringify(conv.id).replace(/"/g, '&quot;');
    const unread = conv.unread || 0;
    const unreadLabel = unread > 99 ? '99+' : unread;

    return `
      <div class="conv-item ${isActive ? 'active' : ''} ${unread > 0 ? 'unread' : ''}" onclick="openConversation(${clickArg})">
        <div class="conv-avatar-wrap">
          ${renderAvatarHTML(displayName, 'avatar-lg')}
          <div class="status-dot ${isOnline ? 'online' : ''}"></div>
        </div>
        <div class="conv-info">
          <div class="conv-top">
            <span class="conv-name">${escapeHtml(displayName)}</span>
            <div class="conv-meta">
              <span class="conv-time">${escapeHtml(String(conv.lastTime || ''))}</span>
              ${unread > 0 ? `<span class="conv-unread">${unreadLabel}</span>` : ''}
            </div>
          </div>
          <div class="conv-last">${escapeHtml(conv.lastMessage || '')}</div>
          <div class="tags">
            ${(conv.tags || []).map(t => `<span class="tag tag-${t.color}">${escapeHtml(t.label || '')}</span>`).join('')}
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
    if (conv.isGlobal) return conv.name.toLowerCase().includes(q);
    const partner = getAccountById(conv.participantId);
    return partner?.name.toLowerCase().includes(q) ||
           (conv.lastMessage || '').toLowerCase().includes(q);
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
  const previousConvId = STATE.activeConvId;
  if (previousConvId && previousConvId !== String(convId)) {
    clearTimeout(typingTimer);
    WS.sendTyping(previousConvId, false);
  }

  STATE.activeConvId = String(convId);
  hideTypingBar();

  const conv = STATE.conversations.find(c => String(c.id) === String(convId));
  const partner = conv && !conv.isGlobal ? getAccountById(conv.participantId) : null;
  if (!conv || (!conv.isGlobal && !partner)) return;

  // Mark as read
  if (conv) conv.unread = 0;
  updateUnreadBadge();
  renderConversations(STATE.conversations);

  // Show chat window
  document.getElementById('emptyState').style.display = 'none';
  document.getElementById('chatWindow').style.display = 'flex';

  // Update header
  if (conv.isGlobal) {
    renderGlobalHeader();
  } else {
    renderChatHeader(partner);
  }

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

function renderGlobalHeader() {
  const avatarEl = document.getElementById('chatPartnerAvatar');
  avatarEl.style.background = '#2563EB';
  avatarEl.textContent = 'GC';

  document.getElementById('chatPartnerName').textContent = 'Global Chat';
  const statusEl = document.getElementById('chatPartnerStatus');
  statusEl.textContent = `${STATE.onlineUsers.size} online`;
  statusEl.className = 'chat-partner-status online';
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

    const bubbles = group.messages.map(msg => `
      <div class="msg-bubble">
        ${escapeHtml(msg.text || '')}${msg.isStreaming ? '<span class="streaming-cursor"></span>' : ''}
      </div>
    `).join('');

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

  const sentViaWs = STATE.activeConvId === 'global'
    ? WS.sendGlobalMessage(text)
    : WS.sendMessage(STATE.activeConvId, text);

  const msg = sentViaWs
    ? {
        id: Date.now(),
        senderId: STATE.currentAccount?.id || 0,
        text,
        time: new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }),
      }
    : await apiSendMessage(STATE.activeConvId, text);

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
  const conv = STATE.conversations.find(c => String(c.id) === STATE.activeConvId);
  if (conv) {
    conv.lastMessage = text;
    conv.lastTime = 'Just now';
    moveConversationToTop(STATE.activeConvId);
    renderConversations(STATE.conversations);
  }

  // Stop typing indicator
  clearTimeout(typingTimer);
  WS.sendTyping(STATE.activeConvId, false);

  // Auto reply từ bot (mock - Week 7 sẽ có OpenAI)
  const partner = STATE.conversations.find(c => String(c.id) === STATE.activeConvId);
  const partnerAcc = partner ? getAccountById(partner.participantId) : null;
  if (partnerAcc?.isBot && !sentViaWs) {
    simulateBotReply(STATE.activeConvId, partnerAcc);
  }
}

// ────────────────────────────────────────────────────────────────
// REAL-TIME HANDLERS (Called by websocket.js)
// ────────────────────────────────────────────────────────────────

function receiveMessage(convId, message) {
  const key = String(convId);
  if (!STATE.messages[key]) STATE.messages[key] = [];
  STATE.messages[key].push(message);

  const conv = ensureConversationForIncoming(key, message);
  if (conv) {
    conv.lastMessage = message.text || '';
    conv.lastTime = message.time || 'Just now';
    if (key !== STATE.activeConvId) {
      conv.unread = (conv.unread || 0) + 1;
    }
    moveConversationToTop(key);
  }

  if (key === STATE.activeConvId) {
    renderMessages();
    updateUnreadBadge();
    renderConversations(STATE.conversations);
  } else {
    updateUnreadBadge();
    renderConversations(STATE.conversations);
  }
}

function ensureConversationForIncoming(convId, message) {
  let conv = STATE.conversations.find(c => String(c.id) === convId);
  if (conv) return conv;

  const senderId = message?.senderId;
  if (!senderId || senderId === STATE.currentAccount?.id) return null;

  const sender = getAccountById(senderId);
  conv = {
    id: convId,
    participantId: senderId,
    isGlobal: false,
    lastMessage: message.text || '',
    lastTime: message.time || 'Just now',
    unread: 0,
    tags: [{ label: sender?.isBot ? 'Bot' : 'Direct', color: sender?.isBot ? 'purple' : 'gray' }],
  };
  STATE.conversations.unshift(conv);
  return conv;
}

function moveConversationToTop(convId) {
  const key = String(convId);
  const index = STATE.conversations.findIndex(c => String(c.id) === key);
  if (index < 0) return;

  const conv = STATE.conversations[index];
  if (conv.isGlobal) return;

  STATE.conversations.splice(index, 1);
  const insertAt = STATE.conversations[0]?.isGlobal ? 1 : 0;
  STATE.conversations.splice(insertAt, 0, conv);
}

function setTyping(convId, userId, isTyping) {
  const key = String(convId);
  if (!STATE.typingUsers[key]) STATE.typingUsers[key] = {};

  clearTimeout(STATE.typingUsers[key][userId]);
  if (isTyping) {
    STATE.typingUsers[key][userId] = setTimeout(() => {
      setTyping(key, userId, false);
    }, CONFIG.TYPING_DEBOUNCE_MS + 1000);
  } else {
    delete STATE.typingUsers[key][userId];
  }

  if (key !== STATE.activeConvId) return;

  const typingUserId = Object.keys(STATE.typingUsers[key] || {})[0];
  const partner = typingUserId ? getAccountById(Number(typingUserId)) : null;
  const bar = document.getElementById('typingBar');
  const txt = document.getElementById('typingText');

  if (partner) {
    txt.textContent = `${partner.name} is typing...`;
    bar.style.display = 'flex';
  } else {
    hideTypingBar();
  }
}

function hideTypingBar() {
  const bar = document.getElementById('typingBar');
  const txt = document.getElementById('typingText');
  if (bar) bar.style.display = 'none';
  if (txt) txt.textContent = '';
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
  const conv = STATE.conversations.find(c => String(c.id) === STATE.activeConvId);
  if (conv && conv.participantId === userId) {
    const partner = getAccountById(userId);
    if (partner) renderChatHeader(partner);
  }

  if (conv && conv.isGlobal) {
    renderGlobalHeader();
  }
}

function setPresenceSnapshot(onlineUsers) {
  STATE.onlineUsers = new Set((onlineUsers || []).map(id => Number(id)));
  renderConversations(STATE.conversations);

  const conv = STATE.conversations.find(c => String(c.id) === STATE.activeConvId);
  if (conv?.isGlobal) {
    renderGlobalHeader();
  } else if (conv?.participantId) {
    const partner = getAccountById(conv.participantId);
    if (partner) renderChatHeader(partner);
  }
}

function appendBotChunk(convId, chunk, isDone) {
  const key = String(convId);
  if (!STATE.messages[key]) STATE.messages[key] = [];

  let messageId = STATE.botStreams[key];
  let message = messageId ? STATE.messages[key].find(m => m.id === messageId) : null;
  let createdNewMessage = false;
  if (!message) {
    messageId = `bot-${Date.now()}`;
    message = {
      id: messageId,
      senderId: 9,
      text: '',
      time: new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }),
      isStreaming: true,
    };
    STATE.botStreams[key] = messageId;
    STATE.messages[key].push(message);
    createdNewMessage = true;
  }

  if (chunk) message.text += chunk;
  if (isDone) {
    message.isStreaming = false;
    delete STATE.botStreams[key];
  }

  const conv = STATE.conversations.find(c => String(c.id) === key);
  if (conv) {
    conv.lastMessage = message.text || 'Bot is typing...';
    conv.lastTime = message.time || 'Just now';
    if (createdNewMessage && key !== STATE.activeConvId) {
      conv.unread = (conv.unread || 0) + 1;
    }
    moveConversationToTop(key);
    updateUnreadBadge();
    renderConversations(STATE.conversations);
  }

  if (key === STATE.activeConvId) {
    renderMessages();
  }
}

// ────────────────────────────────────────────────────────────────
// TYPING INDICATOR (sent to server)
// ────────────────────────────────────────────────────────────────
let typingTimer = null;

function handleTyping() {
  if (!STATE.activeConvId) return;
  const convId = STATE.activeConvId;
  const input = document.getElementById('msgInput');
  clearTimeout(typingTimer);

  if (STATE.settings.typingIndicators === false || !input.value.trim()) {
    WS.sendTyping(convId, false);
    return;
  }

  WS.sendTyping(convId, true);
  typingTimer = setTimeout(() => {
    WS.sendTyping(convId, false);
  }, CONFIG.TYPING_DEBOUNCE_MS);
}

function stopTyping() {
  if (!STATE.activeConvId) return;
  clearTimeout(typingTimer);
  WS.sendTyping(STATE.activeConvId, false);
}

// ────────────────────────────────────────────────────────────────
// SETTINGS
// ────────────────────────────────────────────────────────────────

async function loadSettings() {
  STATE.settings = await getSettings();
  renderSettings();
}

function renderSettings() {
  const settings = {
    typingIndicators: true,
    showOnlineStatus: true,
    notifications: true,
    soundEnabled: true,
    ...STATE.settings,
  };

  document.getElementById('settingTyping').checked = settings.typingIndicators;
  document.getElementById('settingPresence').checked = settings.showOnlineStatus;
  document.getElementById('settingNotifications').checked = settings.notifications;
  document.getElementById('settingSound').checked = settings.soundEnabled;
}

function showSettingsPanel() {
  renderSettings();
  document.getElementById('settingsOverlay').classList.add('visible');
}

function hideSettingsPanel() {
  document.getElementById('settingsOverlay').classList.remove('visible');
}

async function toggleSetting(key, value) {
  STATE.settings = { ...STATE.settings, [key]: value };
  await updateSetting(key, value);
  renderSettings();
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
