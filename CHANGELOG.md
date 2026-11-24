# Lịch sử thay đổi

Định dạng theo [Keep a Changelog](https://keepachangelog.com/vi/1.1.0/).

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
