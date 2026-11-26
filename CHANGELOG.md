# Lịch sử thay đổi

Định dạng theo [Keep a Changelog](https://keepachangelog.com/vi/1.1.0/).

## [1.2] – 2026-11-26

### Thêm
- **Ô cài đặt nhanh "Trợ năng đảo"**: chạm để tắt/bật Trợ năng của IslandDrop — dùng khi app ngân hàng (vd MB Bank) yêu cầu tắt Trợ năng trước khi đăng nhập. Cần quyền `WRITE_SECURE_SETTINGS` (cấp qua adb); chưa có quyền thì ô mở trang Trợ năng.
- **Chế độ "Đảo luôn hiện"** (ô cài đặt nhanh + nút trong app): viên đen luôn hiện quanh camera, chạm để mở khay **app mở nhanh**; chạm lại hoặc chạm ra ngoài để thu. Tự ẩn khi xoay ngang.
- Màn hình **chọn app mở nhanh** (nút "+" trên khay mở nhanh, hoặc trong app).
- Nút trong app để thêm 2 ô vào thanh cài đặt nhanh (Android 13+).

### Đổi
- Tắt Trợ năng bằng ô cài đặt nhanh sẽ không bị cơ chế tự bật lại bật ngược lại.

## [1.1] – 2026-11-24

### Sửa
- **Giữ ảnh đang xem / thẻ "Đã sao chép" ngừng chạy sau khi dọn đa nhiệm.** Khi HiOS giết app (vuốt bỏ thẻ app, nút "Dọn dẹp"), Android đánh dấu Trợ năng là *Crashed* và không tự kết nối lại, dù Cài đặt vẫn hiện "đang bật".
  - Ẩn IslandDrop khỏi màn hình đa nhiệm để không vô tình vuốt bỏ.
  - Tự kiểm tra lúc khởi động và mỗi 5 phút; nếu Trợ năng bị ngắt thì hiện **thông báo "Bấm để bật lại"**.
  - Màn hình cài đặt ghi rõ "Bị hệ thống ngắt" thay vì "đang bật".
  - **Tự bật lại Trợ năng** không cần thao tác nếu đã cấp quyền `WRITE_SECURE_SETTINGS` qua máy tính (tùy chọn, xem README).

## [1.0] – 2026-11-24

Bản đầu tiên, thử trên Tecno Camon 30 5G (Android 16, HiOS 16.3).

### Thêm
- Khay kéo thả mọc ra từ chỗ camera: viên gợi ý khi bắt đầu kéo, giãn thành khay khi kéo tới đảo.
- Gửi ảnh, video, tệp sang app khác bằng cách thả lên icon (một hoặc nhiều tệp).
- Kéo **chữ đã bôi đen** lên đảo để gửi văn bản.
- **Giữ ảnh đang xem** trong Thư viện Tecno: ảnh bay lên đảo, khay mở ra để chạm chọn app. Nhận ra ảnh bằng ngày giờ trên màn hình và so màu với ảnh thu nhỏ.
- Thẻ **"Đã sao chép"** khi bấm Sao chép / Copy ở bất kỳ app nào; giữ thẻ rồi kéo tới app hoặc chạm để chọn.
- Nút **"+ Thêm app"**: ghim app lên đầu, ẩn app khỏi khay.
- App hay dùng tự lên đầu khay.
- Tự cuộn khi rê sát mép (có gia tốc, mượt từng phần pixel); vuốt ngang có quán tính ở chế độ chạm.
- Rung nhẹ khi rê qua icon; chuyển động lò xo 120Hz.
- Tự bật lại sau khi khởi động máy hoặc cập nhật app.
- Ghi log ra file `files/log.txt` (không ghi nội dung người dùng).

### An toàn
- Cửa sổ overlay không nhận chạm và độ mờ ≤ 0.8 khi không dùng → không cản thao tác.
- Không yêu cầu quyền Internet. Bỏ qua nội dung bộ nhớ tạm được đánh dấu nhạy cảm.
- Tệp chia sẻ tạm tự xóa sau 24 giờ.
