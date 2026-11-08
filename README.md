# VTMen — Hệ thống Quản lý & Điều phối Robot Giao hàng

Ứng dụng Full-stack gồm **Next.js 16 (React 19)** ở Frontend và **Spring Boot 3.5 (Java 17)** ở Backend, sử dụng **MongoDB**, cập nhật thời gian thực qua **WebSocket (STOMP)**, gửi mã PIN qua **Gmail SMTP**, và tích hợp trực tiếp với hệ thống điều khiển robot **DCS**.

---

## 1. Yêu cầu cài đặt trên máy mới (Prerequisites)

Đồng nghiệp nhận dự án chỉ cần cài đặt **3 công cụ cơ bản** sau:

| Công cụ | Phiên bản yêu cầu | Ghi chú |
| :--- | :--- | :--- |
| **Node.js** | **20.x LTS** trở lên | Đi kèm `npm` để chạy Frontend và script khởi động |
| **Java JDK** | **JDK 17** trở lên | Dự án đã có sẵn Maven Wrapper (`mvnw`), **không cần** cài thêm Maven |
| **MongoDB** | **MongoDB 6.0/7.0** (hoặc **Docker**) | Chạy ở cổng mặc định `27017` hoặc dùng MongoDB Atlas |

> **Mẹo cho máy chưa cài MongoDB Server:** Nếu máy đã có Docker Desktop, chỉ cần mở terminal tại thư mục dự án và chạy:
> ```bash
> docker compose up -d
> ```

---

## 2. Cách chạy nhanh nhất (1-Click trên Windows)

Sau khi clone hoặc giải nén dự án về máy:

1. **Chạy ở chế độ Local (Khuyên dùng khi phát triển/test trên máy mới):**
   * Nhấp đúp vào file **`start-local.bat`** (hoặc chạy lệnh `npm run dev:all`).
   * Script sẽ **tự động**:
     * Tạo file `.env` từ `.env.example` (nếu chưa có).
     * Cài đặt thư viện `node_modules` (nếu chạy lần đầu).
     * Kiểm tra và bật dịch vụ MongoDB.
     * Giải phóng cổng bị kẹt và khởi chạy đồng thời cả Frontend (`3005`) và Backend (`8085`).

2. **Chạy kèm Ngrok Tunnel (Khi cần nhận Webhook từ Robot DCS qua Internet):**
   * Đảm bảo máy đã cài `ngrok` và đăng nhập token (`ngrok config add-authtoken <TOKEN>`).
   * Cập nhật biến `NGROK_DOMAIN` trong file `.env` (hoặc để trống nếu dùng domain ngẫu nhiên của Ngrok).
   * Nhấp đúp vào file **`start-tunnel.bat`** (hoặc chạy `npm run dev:tunnel`).

3. **Truy cập ứng dụng:**
   * Giao diện Quản trị / Tạo đơn / Điều phối Robot: **http://localhost:3005**
   * Backend REST API & WebSocket: **http://localhost:8085**

---

## 3. Cấu hình biến môi trường (`.env`)

Khi chạy lần đầu, file `.env` được tự động tạo từ `.env.example`. Bạn có thể tùy chỉnh các thông số trong `.env`:

```env
# Cổng chạy dịch vụ
FRONTEND_PORT=3005
BACKEND_PORT=8085

# Kết nối MongoDB (Local hoặc MongoDB Atlas URI)
SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/vtmen

# Địa chỉ API & WebSocket
NEXT_PUBLIC_API_URL=http://localhost:8085/api
NEXT_PUBLIC_WS_URL=http://localhost:8085
NEXT_PUBLIC_DCS_MAP_NAME=@

# Máy chủ điều khiển Robot DCS
DCS_HOST=203.145.47.216
DCS_PORT=8081
DCS_USERNAME=admin
DCS_PASSWORD=123

# Cấu hình Gmail SMTP gửi mã PIN mở tủ cho khách hàng
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password

# Tên miền tĩnh Ngrok (để trống nếu dùng domain ngẫu nhiên)
NGROK_DOMAIN=
```

> **Lưu ý về dữ liệu bản đồ (Seed Data):** Trong lần đầu tiên Backend khởi động với MongoDB mới, `CampusMapStartupSeed` sẽ tự động khởi tạo dữ liệu bản đồ và danh sách điểm đỗ mẫu vào collection `maps` mà không cần import thủ công.

---

## 4. Danh sách câu lệnh (NPM Scripts)

| Lệnh | Mô tả |
| :--- | :--- |
| `npm run dev:all` | Khởi chạy tự động cả **Frontend (3005)** + **Backend (8085)** |
| `npm run dev:tunnel` | Khởi chạy **Frontend** + **Backend** + **Ngrok Tunnel** |
| `npm run dev` | Chỉ chạy riêng Frontend Next.js |
| `npm run build` | Build Frontend cho môi trường Production |
| `npm run lint` | Kiểm tra lỗi cú pháp với ESLint |

---

## 5. Xử lý sự cố thường gặp (Troubleshooting)

1. **Lỗi `Port 3005 / 8085 already in use`**:
   * Chạy thông qua `start-local.bat` hoặc `npm run dev:all` — script sẽ tự động đóng tiến trình cũ đang chiếm cổng trước khi bật mới.
2. **Backend báo lỗi kết nối MongoDB (`MongoSocketOpenException`)**:
   * Mở **Windows Services** (`services.msc`) -> tìm **MongoDB Server** -> bấm **Start**, hoặc chạy `docker compose up -d`.
3. **Không gửi được Email mã PIN**:
   * Kiểm tra `MAIL_USERNAME` và `MAIL_PASSWORD` trong `.env`. Lưu ý `MAIL_PASSWORD` phải là **Mật khẩu ứng dụng (App Password) 16 ký tự** của Google, không phải mật khẩu đăng nhập Gmail thông thường.
