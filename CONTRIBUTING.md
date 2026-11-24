# Đóng góp

Cảm ơn bạn muốn góp sức cho IslandDrop!

## Báo lỗi

Mở một **Issue** và ghi:
- hãng, đời máy, phiên bản Android và giao diện (HiOS, MIUI, ColorOS, OriginOS...);
- bạn đã làm gì, mong đợi gì, thực tế ra sao;
- nếu được, đính kèm log:
  ```bash
  adb shell run-as vn.islanddrop cat files/log.txt
  ```
  (log không chứa nội dung bạn gửi, nhưng hãy xem lại trước khi đăng).

Video quay màn hình rất có ích, nhất là với lỗi hiệu ứng.

## Gửi code

1. Fork repo, tạo nhánh mới từ `main`.
2. Giữ phong cách code hiện có: Java, **không thêm thư viện ngoài** (không AndroidX) để app nhỏ và ít lỗi.
3. Comment và chuỗi hiển thị bằng **tiếng Việt**.
4. Thử trên máy thật: không cản chạm khi khay ẩn, không lag khi kéo, không crash khi từ chối quyền.
5. Mở Pull Request, mô tả thay đổi và máy đã thử.

## Ý tưởng đang cần giúp

- Hỗ trợ "giữ ảnh đang xem" cho Thư viện của hãng khác (Xiaomi, Samsung, Oppo...).
- Đặt khay theo vị trí camera thật của từng máy (camera lệch góc).
- Thêm ngôn ngữ (tiếng Anh) cho giao diện và nhận diện nút Sao chép.
