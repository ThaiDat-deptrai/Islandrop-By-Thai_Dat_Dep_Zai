# Bảo mật

IslandDrop dùng các quyền nhạy cảm (hiển thị trên ứng dụng khác, Trợ năng, đọc ảnh), nên mọi lỗi bảo mật đều được xem là ưu tiên cao.

## Báo lỗi bảo mật

Nếu bạn tìm thấy lỗ hổng (ví dụ: app khác đọc được tệp qua `ShareProvider`, rò rỉ nội dung bộ nhớ tạm, overlay cản chạm hoặc bị lợi dụng để lừa người dùng), **đừng mở Issue công khai**. Hãy dùng tính năng **Report a vulnerability** (Security → Advisories) của repo trên GitHub.

Vui lòng ghi: phiên bản app, máy và Android đã thử, các bước tái hiện.

## Phạm vi hiện có

- `ShareProvider` không export, chỉ cho đọc, chặn truy cập ngoài thư mục `cache/share`.
- Tệp chia sẻ tự xóa sau 24 giờ.
- Không có quyền Internet.
- Bộ nhớ tạm chỉ đọc ngay sau khi người dùng bấm Sao chép; bỏ qua nội dung đánh dấu nhạy cảm.
