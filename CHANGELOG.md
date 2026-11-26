# Lịch sử thay đổi

Định dạng theo [Keep a Changelog](https://keepachangelog.com/vi/1.1.0/).

## [1.4] – 2026-11-26

Viên đảo luôn hiện được làm lại **theo đúng mã nguồn Atoll** (Dynamic Island cho Windows, MIT — xem [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)).

### Thêm
- **38 nhân vật** trong viên thuốc (Đôi mắt mặc định, Mèo, Ma, Robot, Cá, Hành tinh, Slime, Nhịp tim, Thỏ, Cú mèo, Khủng long, Ngôi sao, Mây, Claude, Cua Clawd, Cún, Cáo, Gấu trúc, Gấu, Ếch, Chim cánh cụt, Gà con, Heo, Koala, Chuột, Ong, Bạch tuộc, Nấm, Xương rồng, Người ngoài hành tinh, Dơi, Mặt trăng, Mặt trời, Ngọn lửa, Giọt nước, Trái tim, Bí ngô, Không có). Mỗi nhân vật thở, chớp mắt, đảo mắt, nhún theo nhạc với chuyển động riêng. Chọn trong app: **"Chọn nhân vật trên đảo"**.
- **7 cảm xúc** khi chạm vào nhân vật: yêu, giận, ngạc nhiên, buồn, chóng mặt, cười, đeo kính râm.
- **Aura "Aurora"** (xanh–tím–xanh lơ; nhân vật Claude/Clawd: cam) trượt 1 chu kỳ / 4s, dâng từ mép dưới, thở 0.45↔0.9, viền dưới phát sáng.
- **Bóng đổ** mềm; **đổi bài** thì bóng loé màu ảnh bìa rồi tắt dần trong 1.3s, nhân vật nhảy mừng.
- **Bọng nhạc**: mép dưới phồng một bọng đi lang thang khi phát nhạc; **sóng nhạc** 4 vạch chu kỳ ngẫu nhiên, màu chủ đạo của ảnh bìa.
- **Vòng tiến độ** khi hẹn giờ (chấm xanh, chạy theo chiều kim đồng hồ).
- Lò xo bề ngang 380/24, **nhún khi chạm** (96%, lò xo 520/18), cú "đẩy" mỗi lần đổi trạng thái, **hiện lại mềm** sau khi đóng bảng.

### Đổi
- Viên thuốc tách thành cửa sổ vẽ (không nhận chạm) + cửa sổ cảm ứng vừa khít: bóng và aura không còn chặn kéo thanh thông báo.
- Không tạo đối tượng đồ hoạ mới mỗi khung hình (tiết kiệm RAM); đứng yên vẽ 30 khung hình/giây, chuyển động vẽ đủ tốc độ màn hình.

## [1.3.1] – 2026-11-26

### Sửa
- Đảo không có ảnh bìa với YouTube (YouTube chỉ gửi link ảnh trên Internet, IslandDrop không có quyền Internet). Giờ thử thêm icon trong mô tả bài và link ảnh nội bộ; vẫn không có thì dùng **icon app nhạc** (sóng nhạc và viền sáng lấy màu theo icon).

## [1.3] – 2026-11-26

Đảo luôn hiện kiểu **Atoll**.

### Thêm
- **Hoạt động trực tiếp trên viên đảo** (giãn ra theo lò xo, nội dung hiện dần):
  - **Nhạc**: ảnh bìa + sóng nhạc 4 vạch nhảy, viền sáng "thở" theo màu ảnh bìa.
  - **Hẹn giờ**: đồng hồ + đếm ngược; hết giờ thì hiện "Hết giờ", rung và thông báo.
  - **Sạc pin**: cắm sạc thì hiện tia sét + % pin vài giây.
- **Bảng mở rộng 3 tab** (chạm hoặc vuốt xuống viên đảo): **Nhạc** (ảnh bìa, tên bài, thanh tua kéo được, lùi / phát-dừng / sang bài), **Ứng dụng** (app mở nhanh), **Hẹn giờ** (1–30 phút, +1 phút, Hủy). Nở ra từ viên đảo, tự thu sau 12 giây không chạm.
- **Vuốt ngang viên đảo** để chuyển bài.
- **Tắt Trợ năng 5 phút rồi tự bật lại** (cho app ngân hàng như MB Bank): nút ngay trong thông báo "IslandDrop đang chạy", trong tab Ứng dụng, và ô cài đặt nhanh. Đang tạm tắt thì có nút "Bật lại ngay"; khởi động lại máy vẫn tự bật lại đúng giờ.
- Viên đảo dùng **lớp phủ Trợ năng** (nằm trên thanh trạng thái) nên chạm vào chỗ camera luôn trúng.

### Đổi
- Chạm viên đảo mở bảng 3 tab thay cho khay app nhanh cũ.
- Thông báo thường trực đổi sang mức "thấp" để luôn thấy nút.

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
