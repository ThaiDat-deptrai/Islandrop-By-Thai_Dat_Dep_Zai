# Quyền riêng tư

IslandDrop được thiết kế để **mọi thứ nằm trong máy của bạn**.

## Tóm tắt

- **Không có quyền Internet.** App không thể gửi bất kỳ dữ liệu nào ra khỏi máy.
- **Không thu thập, không theo dõi, không quảng cáo.**
- Nội dung chỉ được gửi tới **app bạn tự chọn** (bằng cách thả hoặc chạm vào icon của app đó).

## App đọc gì, khi nào

| Dữ liệu | Khi nào | Lưu ở đâu |
|---|---|---|
| Ảnh / tệp bạn kéo thả | Chỉ khi bạn thả lên một app trong khay | Bản chép tạm trong bộ nhớ đệm của app, **tự xóa sau 24 giờ** |
| Ảnh đang xem trong Thư viện | Chỉ khi bạn giữ ngón tay lên ảnh (cần bật Trợ năng + quyền đọc ảnh) | Ảnh chụp màn hình thu nhỏ chỉ nằm trong bộ nhớ để so màu, không ghi ra file |
| Danh sách ảnh trong máy | Để tìm đúng ảnh đang xem: đọc ảnh thu nhỏ của các ảnh cùng phút chụp hoặc gần đây | Đặc trưng màu giữ tạm trong bộ nhớ, mất khi app tắt |
| Bộ nhớ tạm (clipboard) | **Chỉ ngay sau khi bạn bấm "Sao chép"/"Copy"** | Không lưu. Nội dung được đánh dấu nhạy cảm (mật khẩu, OTP) bị bỏ qua |
| Sự kiện Trợ năng | Thông báo của Thư viện Tecno, và nút bấm có chữ "Sao chép"/"Copy" | Không lưu nội dung; log chỉ ghi số ký tự hoặc nhãn nút |
| Thống kê app hay dùng | Khi bạn gửi qua một app | Số lần dùng mỗi app, trong bộ nhớ riêng của IslandDrop |

## Dịch vụ Trợ năng

Dịch vụ Trợ năng của IslandDrop:
- **không** chặn, thay đổi hay giả lập thao tác chạm;
- **không** đọc nội dung các app khác, ngoài: thông báo của Thư viện Tecno, chữ trên màn hình xem ảnh (ngày giờ) khi bạn giữ ảnh, và nhãn của nút vừa bấm;
- chỉ chụp màn hình khi bạn giữ ảnh trong Thư viện, để nhận ra ảnh đang xem.

Nếu bạn cấp thêm quyền tùy chọn `WRITE_SECURE_SETTINGS` (chỉ cấp được qua adb), IslandDrop chỉ dùng nó để tắt/bật lại **đúng dịch vụ Trợ năng của chính nó** khi hệ thống ngắt dịch vụ này; các dịch vụ Trợ năng của app khác được giữ nguyên.

Bạn có thể tắt Trợ năng bất cứ lúc nào trong **Cài đặt → Trợ năng**. Các tính năng kéo thả vẫn chạy bình thường mà không cần Trợ năng.

## Xóa dữ liệu

Gỡ app sẽ xóa toàn bộ dữ liệu của IslandDrop (bản chép tạm, thống kê, cài đặt ghim/ẩn, log).
