# Chạy full-project

File này hướng dẫn cách bật backend và frontend khi test local.

## Yêu cầu

- Java 17+ hoặc Java 21
- `sbt`
- `python3`

Frontend hiện gọi backend theo cấu hình trong `fe/js/config.js`:

```js
API_BASE: 'http://localhost:9000'
WS_BASE: 'ws://localhost:9000'
USE_MOCK: false
```

## 1. Bật backend

Mở terminal 1:

```bash
cd full-project/be
sbt run
```

Backend chạy tại:

```text
http://localhost:9000
```

Kiểm tra nhanh:

```bash
curl http://localhost:9000/health
curl http://localhost:9000/api/accounts
```

## 2. Bật frontend

Mở terminal 2:

```bash
cd full-project/fe
python3 -m http.server 3000
```

Mở trình duyệt:

```text
http://localhost:3000
```

Sau khi sửa JS/CSS, hard refresh trang để tránh browser dùng cache cũ:

```text
Cmd + Shift + R
```

## 3. Cách test nhiều account

Mỗi tab sẽ giữ account đang chạy trong state riêng, nhưng các tab cùng một profile Chrome hoặc cùng một cửa sổ ẩn danh vẫn dùng chung `localStorage`.

Khi test 2-3 account, nên dùng một trong các cách sau:

- Chrome thường cho account 1
- Chrome ẩn danh cho account 2
- Browser profile khác, hoặc browser khác, cho account 3

Nếu dùng nhiều tab trong cùng một cửa sổ ẩn danh, việc chọn account ở một tab sẽ đổi `currentAccountId` trong `localStorage` chung. Tab đang mở vẫn chạy account cũ cho đến khi reload, nhưng sau khi reload nó sẽ lấy account mới nhất trong `localStorage`.

## 4. Kiểm tra trước khi test

Backend:

```bash
cd full-project/be
sbt compile
```

Frontend:

```bash
deno check fe/js/app.js fe/js/websocket.js fe/js/api.js fe/js/config.js
```

Nếu máy không có `deno`, có thể bỏ qua bước này và kiểm tra bằng browser console.
