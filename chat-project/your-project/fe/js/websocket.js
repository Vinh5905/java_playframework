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
  reconnectTimer: null,
  reconnectDelay: 3000,  // ms

  /** Kết nối WebSocket sau khi user đã chọn account */
  connect(accountId) {
    // Week 1-3: WebSocket chưa implement → skip
    // Uncomment khi có backend WS (Week 4)
    /*
    const url = `${CONFIG.WS_BASE}/ws/chat?accountId=${accountId}`;
    console.log(`[WS] Connecting to ${url}`);

    this.connection = new WebSocket(url);

    this.connection.onopen = () => {
      console.log('[WS] Connected!');
      clearTimeout(this.reconnectTimer);
    };

    this.connection.onmessage = (event) => {
      const data = JSON.parse(event.data);
      this.handleMessage(data);
    };

    this.connection.onclose = () => {
      console.warn('[WS] Disconnected. Reconnecting in', this.reconnectDelay, 'ms...');
      this.reconnectTimer = setTimeout(() => this.connect(accountId), this.reconnectDelay);
    };

    this.connection.onerror = (err) => {
      console.error('[WS] Error:', err);
    };
    */
    console.log('[WS] WebSocket sẽ được implement ở Tuần 4');
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

      case 'bot_chunk':
        // Bot streaming (Week 7)
        APP.appendBotChunk(data.convId, data.chunk, data.isDone);
        break;

      default:
        console.warn('[WS] Unknown message type:', data.type);
    }
  },

  /** Gửi event lên server qua WebSocket */
  send(data) {
    if (!this.connection || this.connection.readyState !== WebSocket.OPEN) {
      // WS chưa kết nối → thử qua REST API fallback
      return;
    }
    this.connection.send(JSON.stringify(data));
  },

  /** Gửi typing indicator */
  sendTyping(convId, isTyping) {
    this.send({ type: 'typing', convId, isTyping });
  },

  /** Đóng kết nối */
  disconnect() {
    clearTimeout(this.reconnectTimer);
    if (this.connection) {
      this.connection.close();
      this.connection = null;
    }
  },
};
