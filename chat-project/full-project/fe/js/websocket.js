/**
 * websocket.js - Quản lý kết nối WebSocket
 *
 * Week 1-3: WS chưa có → mọi thứ là no-op (không làm gì)
 * Week 4:   Backend có WS endpoint → connect thật
 * Week 5:   Thêm typing indicator, presence events
 * Week 7:   Thêm bot streaming events
 *
 * Cấu trúc message từ server:
 * { type: "message",  convId, message: {...} }
 * { type: "typing",   convId, userId, isTyping }
 * { type: "presence", userId, status: "online"|"offline" }
 * { type: "bot_chunk", convId, chunk, isDone }
 */

const WS = {
  connection: null,
  globalConnection: null,
  reconnectTimer: null,
  globalReconnectTimer: null,
  pingTimer: null,
  globalPingTimer: null,
  reconnectDelay: 3000,  // ms
  pingInterval: 25000,   // ms
  accountId: null,

  /** Kết nối WebSocket sau khi user đã chọn account */
  connect(accountId) {
    this.accountId = accountId;
    if (this.connection) this.connection.close();

    const url = `${CONFIG.WS_BASE}/ws/chat?accountId=${accountId}`;
    console.log(`[WS] Connecting to ${url}`);

    this.connection = new WebSocket(url);
    const socket = this.connection;

    socket.onopen = () => {
      console.log('[WS] Connected!');
      clearTimeout(this.reconnectTimer);
      clearInterval(this.pingTimer);
      this.pingTimer = setInterval(() => {
        if (this.connection === socket && socket.readyState === WebSocket.OPEN) {
          socket.send(JSON.stringify({ type: 'ping' }));
        }
      }, this.pingInterval);
    };

    socket.onmessage = (event) => {
      const data = JSON.parse(event.data);
      this.handleMessage(data);
    };

    socket.onclose = () => {
      if (this.connection !== socket) return;
      clearInterval(this.pingTimer);
      console.warn('[WS] Disconnected. Reconnecting in', this.reconnectDelay, 'ms...');
      this.reconnectTimer = setTimeout(() => this.connect(accountId), this.reconnectDelay);
    };

    socket.onerror = (err) => {
      console.error('[WS] Error:', err);
    };
  },

  connectGlobal(accountId) {
    if (this.globalConnection) this.globalConnection.close();

    const url = `${CONFIG.WS_BASE}/ws/global?accountId=${accountId}`;
    console.log(`[WS] Connecting global room to ${url}`);

    this.globalConnection = new WebSocket(url);
    const socket = this.globalConnection;

    socket.onopen = () => {
      console.log('[WS] Global connected!');
      clearTimeout(this.globalReconnectTimer);
      clearInterval(this.globalPingTimer);
      this.globalPingTimer = setInterval(() => {
        if (this.globalConnection === socket && socket.readyState === WebSocket.OPEN) {
          socket.send(JSON.stringify({ type: 'ping' }));
        }
      }, this.pingInterval);
    };

    socket.onmessage = (event) => {
      const data = JSON.parse(event.data);
      this.handleMessage(data);
    };

    socket.onclose = () => {
      if (this.globalConnection !== socket) return;
      clearInterval(this.globalPingTimer);
      console.warn('[WS] Global disconnected. Reconnecting in', this.reconnectDelay, 'ms...');
      this.globalReconnectTimer = setTimeout(() => this.connectGlobal(accountId), this.reconnectDelay);
    };

    socket.onerror = (err) => {
      console.error('[WS] Global error:', err);
    };
  },

  /** Xử lý message nhận được từ server */
  handleMessage(data) {
    switch (data.type) {
      case 'message':
        // Tin nhắn mới → thêm vào conversation
        APP.receiveMessage(data.convId, data.message);
        break;

      case 'typing':
        // Typing indicator
        APP.setTyping(data.convId, data.userId, data.isTyping);
        break;

      case 'presence':
        // Online/Offline status
        APP.setPresence(data.userId, data.status);
        break;

      case 'presence_snapshot':
        APP.setPresenceSnapshot(data.onlineUsers || []);
        break;

      case 'bot_chunk':
        // Bot streaming (Week 7)
        APP.appendBotChunk(data.convId, data.chunk, data.isDone);
        break;

      case 'global_message':
        APP.receiveMessage('global', data.message);
        break;

      default:
        console.warn('[WS] Unknown message type:', data.type);
    }
  },

  /** Gửi event lên server qua WebSocket */
  send(data) {
    if (!this.connection || this.connection.readyState !== WebSocket.OPEN) {
      // WS chưa kết nối → thử qua REST API fallback
      return false;
    }
    this.connection.send(JSON.stringify(data));
    return true;
  },

  sendGlobal(data) {
    if (!this.globalConnection || this.globalConnection.readyState !== WebSocket.OPEN) {
      return false;
    }
    this.globalConnection.send(JSON.stringify(data));
    return true;
  },

  sendMessage(convId, text) {
    return this.send({ type: 'message', convId, text });
  },

  sendGlobalMessage(text) {
    return this.sendGlobal({ type: 'message', text });
  },

  /** Gửi typing indicator */
  sendTyping(convId, isTyping) {
    this.send({ type: 'typing', convId, isTyping });
  },

  /** Đóng kết nối */
  disconnect() {
    clearTimeout(this.reconnectTimer);
    clearTimeout(this.globalReconnectTimer);
    clearInterval(this.pingTimer);
    clearInterval(this.globalPingTimer);
    if (this.connection) {
      this.connection.close();
      this.connection = null;
    }
    if (this.globalConnection) {
      this.globalConnection.close();
      this.globalConnection = null;
    }
  },
};
