# DAT Seller Shop — Frontend

Frontend React + Vite cho backend Spring Boot trong `../backend/DatShop`.

## Yêu cầu
- Node.js 18+
- Backend Spring Boot chạy tại `http://localhost:8080`

## Cài đặt và chạy
```bash
cd frontend
npm install
cp .env.example .env
npm run dev
```
Mở `http://localhost:5173`.

Nếu Backend chạy cổng khác, sửa `VITE_API_URL` trong `.env`. Backend hiện cấu hình CORS mặc định cho `http://localhost:5173`.

## API đã tích hợp
- Public catalog: `GET /api/products`, `GET /api/products/{slug}`, `GET /api/categories`
- Authentication: `POST /api/auth/login`, `POST /api/auth/register`
- Customer: cart, addresses, checkout, orders
- Admin: dashboard summary, products, orders

Token JWT được lưu ở localStorage (`dat_token`) và tự động gửi dưới dạng Bearer token. Dữ liệu catalog/đơn hàng lấy từ Backend; khi API không khả dụng, frontend hiển thị trạng thái trống thay vì giả lập dữ liệu thật.

## Lưu ý tích hợp
Request đăng ký hiện gửi `username`, `password`, `email`, `fullName`, `phoneNumber`. Kiểm tra `RegisterRequest` trong Backend nếu bạn đã thay đổi DTO.
Giỏ hàng thêm sản phẩm cần `variantId`; frontend lấy biến thể đầu tiên còn hàng từ chi tiết sản phẩm. Checkout gửi `addressId`, `voucherCode`, `customerNote` theo `CheckoutRequest`.
