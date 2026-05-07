/**
 * config.js - Cấu hình toàn cục của Frontend
 *
 * Cách dùng:
 *   - Khi backend CHƯA có: USE_MOCK = true → frontend dùng dữ liệu giả
 *   - Khi backend ĐÃ có:   USE_MOCK = false → frontend gọi API thật
 *   - Thường api.js tự fallback về mock nếu server offline, bạn không cần đổi tay
 */

const CONFIG = {
  // Base URL của Play backend (thay đổi nếu dùng port khác)
  API_BASE: 'http://localhost:9000',

  // Base URL WebSocket (ws:// hoặc wss:// nếu có HTTPS)
  WS_BASE: 'ws://localhost:9000',

  // true  = dùng mock data ngay (backend chưa cần chạy)
  // false = luôn gọi API, không fallback về mock
  USE_MOCK: true,

  // Số ms chờ trước khi coi API là offline và fallback về mock
  API_TIMEOUT_MS: 2000,

  // Thời gian debounce typing indicator (ms)
  TYPING_DEBOUNCE_MS: 1500,
};
