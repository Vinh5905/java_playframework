# Day 40 - Production Build & Deployment

## Mục tiêu
- Build production package
- Production configuration
- Health checks & graceful shutdown

---

## 1. Build Production Package

```bash
# Clean build + create distribution
sbt clean dist

# Output: target/universal/your-app-1.0.zip
# Chứa: start script, dependencies, conf

# Unzip và chạy
unzip target/universal/your-app-1.0.zip -d /opt/
/opt/your-app-1.0/bin/your-app \
  -Dplay.http.secret.key="$APP_SECRET" \
  -Dconfig.file=/opt/your-app-1.0/conf/prod.conf \
  -Dhttp.port=9000
```

---

## 2. sbt stage (Faster, No Zip)

```bash
# Stage: như dist nhưng không nén
sbt stage

# Chạy trực tiếp
./target/universal/stage/bin/your-app \
  -Dplay.http.secret.key="production-secret"
```

---

## 3. Production Checklist

```
[ ] play.http.secret.key = production random string (min 32 chars)
[ ] play.evolutions.autoApply = false
[ ] play.filters.hosts.allowed = [production domain only]
[ ] Tắt CSRF nếu dùng JWT; bật CSRF nếu dùng session
[ ] HTTPS chỉ (secure cookie, HSTS header)
[ ] Không có default password/key nào
[ ] Logging: JSON format, không log sensitive data
[ ] Health check endpoint: /health
[ ] Graceful shutdown configured
[ ] JVM heap tuned (-Xmx, -Xms)
[ ] DB connection pool tuned
[ ] Rate limiting enabled
[ ] CORS configured correctly
```

---

## 4. Graceful Shutdown

Play hỗ trợ graceful shutdown: drain requests trước khi tắt.

```hocon
# Timeout để drain existing requests khi shutdown
play.server.terminationTimeout = 30 seconds
```

```bash
# Gửi SIGTERM (graceful)
kill -TERM <pid>

# Gửi SIGKILL (force, immediate)
kill -9 <pid>
```

---

## 5. Reverse Proxy với Nginx

```nginx
# /etc/nginx/sites-available/myapp
upstream play_app {
    server 127.0.0.1:9000;
    keepalive 32;
}

server {
    listen 80;
    server_name api.myapp.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name api.myapp.com;

    ssl_certificate /etc/ssl/myapp.crt;
    ssl_certificate_key /etc/ssl/myapp.key;

    location / {
        proxy_pass http://play_app;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_connect_timeout 5s;
        proxy_read_timeout 30s;
    }

    # WebSocket support
    location /ws {
        proxy_pass http://play_app;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

---

## 6. Systemd Service

```ini
# /etc/systemd/system/myapp.service
[Unit]
Description=My Play Application
After=network.target postgresql.service

[Service]
Type=simple
User=appuser
WorkingDirectory=/opt/myapp
ExecStart=/opt/myapp/bin/my-app -J-Xmx1g
Environment=APP_SECRET=production-secret
Environment=DATABASE_URL=jdbc:postgresql://db:5432/mydb
Restart=always
RestartSec=10
StandardOutput=syslog
StandardError=syslog
SyslogIdentifier=myapp

[Install]
WantedBy=multi-user.target
```

```bash
systemctl enable myapp
systemctl start myapp
systemctl status myapp
journalctl -u myapp -f  # Xem logs
```
