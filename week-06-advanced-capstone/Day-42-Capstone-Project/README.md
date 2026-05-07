# Day 42 - Capstone Project: URL Shortener API

## Tổng Hợp Toàn Bộ Kiến Thức

Project này áp dụng **tất cả** kiến thức đã học trong 6 tuần:

| Kiến thức | Được dùng ở |
|-----------|------------|
| Routing, Controllers | Mọi endpoint |
| Async Actions | Tất cả operations |
| Execution Contexts | DB queries |
| Request/Response | JSON API |
| Database (JDBC) | Lưu URLs |
| Forms/Validation | Input validation |
| Filters | Logging, Rate limit |
| Authentication | JWT auth |
| Error Handling | Global error handler |
| Testing | Unit + Integration tests |
| Production Config | Docker, environment vars |

---

## API Specification

### Auth
| Method | URL | Description |
|--------|-----|-------------|
| POST | /auth/register | Đăng ký user |
| POST | /auth/login | Đăng nhập, nhận JWT |

### URL Management (Cần Auth)
| Method | URL | Description |
|--------|-----|-------------|
| POST | /api/urls | Tạo short URL |
| GET | /api/urls | Lấy danh sách URLs của user |
| DELETE | /api/urls/:code | Xóa URL |
| GET | /api/urls/:code/stats | Xem stats |

### Redirect (Public)
| Method | URL | Description |
|--------|-----|-------------|
| GET | /:code | Redirect đến URL gốc |

---

## Chạy Project

```bash
# 1. Start PostgreSQL
docker run -d --name url-shortener-db \
  -e POSTGRES_USER=urluser \
  -e POSTGRES_PASSWORD=urlpass \
  -e POSTGRES_DB=urlshortener \
  -p 5432:5432 \
  postgres:16-alpine

# 2. Chạy app
cd url-shortener
sbt run

# 3. Test
# Register
curl -X POST http://localhost:9000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "secret123", "name": "Alice"}'

# Login
TOKEN=$(curl -s -X POST http://localhost:9000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "secret123"}' \
  | jq -r '.token')

# Tạo short URL
curl -X POST http://localhost:9000/api/urls \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.google.com/search?q=play+framework+java"}'

# Redirect (test trong browser hoặc curl -L)
curl -L http://localhost:9000/abc123
```

---

## Cấu Trúc Code

```
url-shortener/
├── app/
│   ├── controllers/
│   │   ├── AuthController.java       ← Register, Login
│   │   ├── UrlController.java        ← CRUD URLs
│   │   └── RedirectController.java   ← Perform redirect
│   ├── models/
│   │   ├── User.java
│   │   └── ShortUrl.java
│   ├── repositories/
│   │   ├── UserRepository.java       ← Async JDBC
│   │   └── UrlRepository.java        ← Async JDBC
│   ├── services/
│   │   ├── UrlShortenerService.java  ← Business logic
│   │   └── JwtService.java           ← JWT operations
│   ├── security/
│   │   └── JwtAction.java            ← Auth action composition
│   └── filters/
│       ├── RequestLoggingFilter.java
│       └── RateLimitFilter.java
├── conf/
│   ├── application.conf
│   ├── routes
│   └── evolutions/default/
│       └── 1.sql                     ← Schema
└── test/
    ├── controllers/
    └── services/
```
