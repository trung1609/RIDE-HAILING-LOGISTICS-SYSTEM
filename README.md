# Ride Hailing & Logistics System

Dự án này là hệ thống ride-hailing theo kiến trúc microservices, dùng Java Spring Boot, Redis, PostgreSQL, Kafka, WebSocket và Docker Compose để hỗ trợ đầy đủ quy trình đặt xe, nhận cuốc, định giá, thanh toán và giám sát vận hành.

## Kiến trúc tổng quát
- API Gateway: điểm vào chung cho toàn bộ hệ thống, xác thực JWT, chuyển header và kiểm soát truy cập.
- Discovery Service: đăng ký và tìm service bằng Eureka.
- User-Driver Service: quản lý khách hàng, tài xế, đăng nhập, token, trạng thái online/offline và hồ sơ xe.
- Booking Service: xử lý lifecycle cuốc xe từ tạo booking đến nhận cuốc, cập nhật trạng thái và auto-reassign.
- Location Service: lưu và truy vấn vị trí tài xế thời gian thực bằng Redis Geo và WebSocket.
- Pricing Service: tính giá động theo khoảng cách, thời gian, mức cầu/cung và surge.
- Payment Service: quản lý ví, giao dịch, thanh toán MoMo/VNPay, nạp/rút tiền và hoa hồng.
- Frontend: giao diện demo cho khách hàng và tài xế.
- Hạ tầng: PostgreSQL, Redis, Kafka, Prometheus, Grafana, Fluentd, Elasticsearch, Kibana.
- CI/CD: GitHub Actions tự động build, test, push Docker image và deploy lên server staging.
## Chức năng theo service

### 1. API Gateway
- Chặn request chưa có token hoặc token không hợp lệ.
- Dùng JWT để xác thực người dùng và thêm header X-User-Id, X-User-Role, X-User-Phone vào request.
- Cho phép bỏ qua xác thực cho endpoint đăng nhập, đăng ký và callback thanh toán.
- Hỗ trợ CORS và chặn các route nội bộ không được gọi trực tiếp từ ngoài.

### 2. Discovery Service
- Đăng ký các service backend vào Eureka.
- Giúp các service gọi nhau thông qua tên dịch vụ thay vì hardcode địa chỉ.

### 3. User-Driver Service
- Đăng ký khách hàng và tài xế.
- Đăng nhập bằng số điện thoại và mật khẩu, tạo access token và refresh token.
- Logout xóa refresh token khỏi cookie và đưa token vào blacklist Redis.
- Refresh token tự động cấp lại token mới.
- Quản lý hồ sơ người dùng, hồ sơ tài xế, biển số xe, loại xe và model xe.
- Bật/tắt trạng thái tài xế online/offline.
- Khi tài xế offline, hệ thống gửi yêu cầu xóa vị trí khỏi Redis Geo.
- Cung cấp API nội bộ cho booking/location/payment kiểm tra trạng thái online của tài xế.

### 4. Booking Service
- Khách hàng tạo booking với điểm đón và điểm đến.
- Kiểm tra spam bằng Redis: nếu khách đặt quá nhanh thì chặn trong 5 giây.
- Kiểm tra booking đang hoạt động để ngăn đặt cuốc chồng.
- Gọi Location Service để tìm tài xế gần và Pricing Service để tính giá trước khi tạo booking.
- Nếu Pricing Service lỗi, hệ thống dùng fallback giá cơ sở.
- Chọn tài xế phù hợp theo bán kính 3km, 5km, 8km.
- Dùng Redis lock để giữ chỗ tài xế tạm thời trong 20 giây trước khi tài xế phản hồi.
- Tài xế nhận cuốc bằng endpoint accept.
- Cập nhật trạng thái chuyến đi: pending, accepted, arrived, in-progress, completed, canceled.
- Nếu tài xế không phản hồi trong 20 giây, hệ thống giải phóng giữ chỗ và hủy lời mời.
- Nếu tài xế tự hủy, hệ thống đưa booking về PENDING và thêm tài xế hủy vào danh sách tài xế từ chối để tránh mời lại sau đó tìm tài xế khác.
- Sau khi hoàn thành chuyến, booking phát sự kiện Kafka booking-completed-topic cho Payment Service.

### 5. Location Service
- Nhận vị trí tài xế theo thời gian thực qua WebSocket.
- Lưu vị trí vào Redis Geo để tìm tài xế gần.
- Trả về danh sách tài xế gần khách hàng trong bán kính cho trước.
- Loại bỏ tài xế đang bị giữ chỗ cho cuốc khác.
- Gọi User-Driver Service để kiểm tra trạng thái online của nhiều tài xế cùng lúc.
- Hỗ trợ cập nhật vị trí và xóa vị trí khi tài xế offline.

### 6. Pricing Service
- Tính giá động bằng công thức dựa trên khoảng cách, thời gian ước tính và cấu hình giá cơ bản.
- Sử dụng H3 grid để phân vùng địa lý và ghi nhận mức cầu theo ô lục giác.
- Dựa trên tỷ lệ cầu/cung để quyết định mức surge: NORMAL, LIGHT, MODERATE, SEVERE.
- Nếu không có tài xế gần, mức độ surge có thể tăng mạnh do cung thấp.
- Giá cuối cùng được làm tròn về bội của 1000 VND.

### 7. Payment Service
- Tạo và quản lý ví cho người dùng và tài xế.
- Tạo giao dịch thanh toán cho chuyến đi hoặc nạp tiền.
- Hỗ trợ thanh toán qua MoMo và VNPay.
- Xử lý callback IPN và return URL từ cổng thanh toán.
- Tiếp nhận sự kiện booking-completed-topic từ Kafka để thu hoa hồng và cập nhật ví.
- Tiếp nhận sự kiện driver-registered-topic từ Kafka để tạo ví cho tài xế mới.
- Kiểm tra trạng thái thanh toán của booking.
- Hủy giao dịch pending khi khách hàng đổi phương thức hoặc hủy giao dịch.
- Mỗi 5 phút, Payment Service kiểm tra các giao dịch pending quá 15 phút và tự động hủy.
- Rút tiền từ ví tài xế.
- Nếu số dư ví của tài xế âm quá mức (-50.000 VND), hệ thống tự động ép tài xế offline để ngăn nhận cuốc tiếp.

### 8. Frontend
- Frontend khách hàng cho phép đăng nhập, đăng ký, xem bản đồ, đặt xe và theo dõi trạng thái chuyến đi.
- Frontend tài xế cho phép đăng nhập, bật/tắt app, nhận cuốc, cập nhật trạng thái chuyến đi và quản lý ví.
- Cả hai frontend đều dùng WebSocket để nhận cập nhật thời gian thực từ backend.

### 9. Hạ tầng giám sát
- Prometheus thu thập metrics từ các service backend.
- Grafana hiển thị dashboard tổng quan về hiệu năng và trạng thái hệ thống.
- Fluentd thu thập log từ các service backend và gửi đến Elasticsearch.
- Kibana hiển thị log và hỗ trợ tìm kiếm, phân tích log theo thời gian thực.

### 10. Github Actions CI/CD
- Tự động build và test các service backend khi có commit mới.
- Tự động build Docker image và push lên Docker Hub khi commit vào nhánh main.
- Tự động deploy hệ thống lên server staging khi có commit vào nhánh main.
## Công nghệ chính
- Java Spring Boot
- Spring Security
- Spring Cloud Gateway
- Spring Cloud Eureka
- Redis
- PostgreSQL
- Kafka
- WebSocket
- Docker Compose
- Prometheus/Grafana
- Elasticsearch/Kibana
- Fluentd
- MoMo/VNPay API
- H3 Grid
- GitHub Actions
