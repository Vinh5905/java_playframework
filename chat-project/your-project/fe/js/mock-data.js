/**
 * mock-data.js - Dữ liệu mô phỏng cho frontend
 *
 * Dữ liệu này được dùng khi backend chưa hoàn thiện.
 * Mỗi tuần khi bạn code thêm tính năng backend,
 * api.js sẽ chuyển dần từ mock sang real API.
 */

const MOCK = {

  // ────────────────────────────────────────────────────────────
  // ACCOUNTS - Tất cả tài khoản trong hệ thống
  // Người dùng có thể "switch" vào bất kỳ tài khoản nào
  // ────────────────────────────────────────────────────────────
  accounts: [
    { id: 1, name: 'Alice Johnson',      username: 'alice',   isBot: false },
    { id: 2, name: 'Bob Smith',          username: 'bob',     isBot: false },
    { id: 3, name: 'Elmer Laverty',      username: 'elmer',   isBot: false },
    { id: 4, name: 'Florencio Dorrance', username: 'florencio', isBot: false },
    { id: 5, name: 'Lavern Laboy',       username: 'lavern',  isBot: false },
    { id: 6, name: 'Titus Kitamura',     username: 'titus',   isBot: false },
    { id: 7, name: 'Geoffrey Mott',      username: 'geoffrey', isBot: false },
    { id: 8, name: 'Alfonzo Schuessler', username: 'alfonzo', isBot: false },
    { id: 9, name: 'ChatGPT Bot',        username: 'gpt_bot', isBot: true  },
  ],

  // ────────────────────────────────────────────────────────────
  // CONVERSATIONS - Danh sách cuộc hội thoại
  // participantId = ID của người bên kia
  // ────────────────────────────────────────────────────────────
  conversations: [
    {
      id: 1,
      participantId: 3,
      lastMessage:   'Haha oh man 🔥',
      lastTime:      '12m',
      unread:        2,
      tags:          [{ label: 'Question', color: 'orange' }, { label: 'Help wanted', color: 'green' }],
    },
    {
      id: 2,
      participantId: 4,
      lastMessage:   'woohoooo',
      lastTime:      '24m',
      unread:        0,
      tags:          [{ label: 'Some content', color: 'gray' }],
    },
    {
      id: 3,
      participantId: 5,
      lastMessage:   'Haha that\'s terrifying 😂',
      lastTime:      '1h',
      unread:        0,
      tags:          [{ label: 'Bug', color: 'red' }, { label: 'Hacktoberfest', color: 'green' }],
    },
    {
      id: 4,
      participantId: 6,
      lastMessage:   'omg, this is amazing',
      lastTime:      '5h',
      unread:        1,
      tags:          [{ label: 'Question', color: 'orange' }, { label: 'Some content', color: 'gray' }],
    },
    {
      id: 5,
      participantId: 7,
      lastMessage:   'aww 😍',
      lastTime:      '2d',
      unread:        0,
      tags:          [{ label: 'Request', color: 'green' }],
    },
    {
      id: 6,
      participantId: 8,
      lastMessage:   'perfect!',
      lastTime:      '1m',
      unread:        0,
      tags:          [{ label: 'Follow up', color: 'gray' }],
    },
    {
      id: 7,
      participantId: 9,
      lastMessage:   'Sure! How can I help?',
      lastTime:      '3m',
      unread:        0,
      tags:          [{ label: 'Bot', color: 'purple' }],
    },
  ],

  // ────────────────────────────────────────────────────────────
  // MESSAGES - Nội dung từng cuộc hội thoại
  // senderId = 0 → người dùng hiện tại (current account)
  // ────────────────────────────────────────────────────────────
  messages: {
    // Conversation 2: Alice (current) ↔ Florencio (matching screenshot)
    2: [
      { id: 1,  senderId: 4, text: 'omg, this is amazing',        time: '10:28 AM' },
      { id: 2,  senderId: 4, text: 'perfect! ✅',                  time: '10:28 AM' },
      { id: 3,  senderId: 4, text: 'Wow, this is really epic',     time: '10:29 AM' },
      { id: 4,  senderId: 0, text: 'How are you?',                 time: '10:30 AM' },
      { id: 5,  senderId: 4, text: 'just ideas for next time',     time: '10:32 AM' },
      { id: 6,  senderId: 4, text: 'I\'ll be there in 2 mins ⏰', time: '10:32 AM' },
      { id: 7,  senderId: 0, text: 'woohoooo',                     time: '10:34 AM' },
      { id: 8,  senderId: 0, text: 'Haha oh man',                  time: '10:34 AM' },
      { id: 9,  senderId: 0, text: 'Haha that\'s terrifying 😂',  time: '10:35 AM' },
      { id: 10, senderId: 4, text: 'aww',                          time: '10:36 AM' },
      { id: 11, senderId: 4, text: 'omg, this is amazing',         time: '10:36 AM' },
      { id: 12, senderId: 4, text: 'woohoooo 🔥',                  time: '10:37 AM' },
    ],

    // Conversation 1: Alice ↔ Elmer
    1: [
      { id: 1, senderId: 3, text: 'Hey! Are you free to review my PR?', time: '9:00 AM' },
      { id: 2, senderId: 0, text: 'Sure, link it here!',               time: '9:02 AM' },
      { id: 3, senderId: 3, text: 'github.com/elmer/awesome-project',  time: '9:03 AM' },
      { id: 4, senderId: 3, text: 'Haha oh man 🔥',                    time: '9:05 AM' },
    ],

    // Conversation 7: Alice ↔ ChatGPT Bot
    7: [
      { id: 1, senderId: 0, text: 'Hello! Can you help me with Play Framework?', time: '2:00 PM' },
      { id: 2, senderId: 9, text: 'Of course! Play Framework is a reactive web framework built on top of Pekko (formerly Akka). What would you like to know?', time: '2:00 PM' },
      { id: 3, senderId: 0, text: 'What\'s the difference between sync and async actions?', time: '2:01 PM' },
      { id: 4, senderId: 9, text: 'Sure! How can I help?', time: '2:01 PM' },
    ],
  },

  // ────────────────────────────────────────────────────────────
  // ONLINE USERS (Tuần 5 sẽ thay bằng real-time từ WebSocket)
  // ────────────────────────────────────────────────────────────
  onlineUsers: new Set([4, 9]),  // Florencio và Bot đang online
};
