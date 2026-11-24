# Kiến trúc và cách hoạt động

Tài liệu này giải thích các kỹ thuật chính, kèm những điều đã kiểm chứng trên Tecno CL6 (Android 16, HiOS 16.3).

## 1. Cửa sổ overlay "vô hình"

`DropService` giữ **một** cửa sổ `TYPE_APPLICATION_OVERLAY` rộng toàn màn hình, cao ~160dp ở mép trên, chứa `IslandView`.

- **Khi rảnh:** `FLAG_NOT_TOUCHABLE` + `alpha = 0.8` và không vẽ gì.
  Từ Android 12, chạm đi xuyên qua overlay của app khác chỉ khi tổng độ mờ ≤ 0.8 — vì vậy cửa sổ không bao giờ cản thao tác.
- **Khi khay hiện:** bỏ `FLAG_NOT_TOUCHABLE`, thêm `FLAG_WATCH_OUTSIDE_TOUCH`, `alpha = 1`.
- `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS` để vẽ được vào vùng camera.

## 2. Nhận kéo thả từ app khác

Android gửi `ACTION_DRAG_STARTED` tới **mọi cửa sổ đang hiển thị** khi một app bắt đầu kéo với `View.DRAG_FLAG_GLOBAL` — kể cả cửa sổ đang `NOT_TOUCHABLE` (đã kiểm chứng). Nhờ đó khay biết lúc bắt đầu kéo mà không cần nhận chạm.

Luồng:

```
App nguồn bắt đầu kéo (global)
  └─ IslandView: DRAG_STARTED → bật nhận chạm, lò xo expand 0 → 1 (viên gợi ý)
       └─ ngón tay vào vùng đảo: DRAG_ENTERED/LOCATION → expand → 2 (khay), hover icon
            └─ DROP trên icon → DropService.onDropOnTarget()
                 └─ startActivity(DropActivity, extra = DragEvent)   // DragEvent là Parcelable
```

`DropActivity` (trong suốt) gọi `requestDragAndDropPermissions(event)` — **chỉ Activity mới gọi được** — để nhận quyền đọc URI mà app nguồn cấp kèm (ví dụ `content://com.gallery20.provider/...`). Sau đó chép tệp vào `cache/share/<thời điểm>/` ở luồng nền và mở app đích bằng `ACTION_SEND` / `ACTION_SEND_MULTIPLE`, cấp quyền đọc tạm qua `ShareProvider`.

> `DragEvent` bị tái sử dụng ngay sau khi `onDragEvent` trả về, nên phải `startActivity` ngay trong callback (Intent được đóng gói đồng bộ).

## 3. Chuyển động

- `Spring` — lò xo khối lượng 1, tự chia nhỏ bước thời gian (≤ 4ms) để ổn định ở 60–120Hz.
- `IslandView` chạy vòng khung hình bằng `Choreographer`, chỉ khi có gì đang chuyển động.
- Hình dạng nội suy liên tục: viên ở camera (0) → viên gợi ý (1) → khay (2). Giá trị lò xo hơi vượt quá đích tạo cảm giác nảy.
- Icon vẽ bằng `translate/scale` số thực (không `setBounds` số nguyên) để trượt mượt từng phần pixel khi cuộn.
- Tự cuộn ở mép: vận tốc đuổi theo vận tốc mục tiêu với hằng số thời gian 70ms (`ease = 0.22 + 0.78·t²`).

## 4. Giữ ảnh đang xem (Thư viện Tecno)

Không app nào theo dõi được thao tác chạm đang thuộc về app khác (API "chỉ quan sát chạm" của Trợ năng là API ẩn trên Android 16; chế độ còn lại nuốt mất chạm). Vì vậy dùng **tín hiệu gián tiếp**:

1. Khi giữ ảnh, Thư viện HiOS luôn thử "AI cắt chủ thể"; nếu không cắt được sẽ hiện toast
   *"Không có nội dung nào có thể bị cắt bỏ"* → `ViewerWatcher` nhận `TYPE_NOTIFICATION_STATE_CHANGED`.
2. Xác nhận đang ở màn hình xem ảnh: tìm view `com.gallery20:id/photo_view`
   (cần cờ `flagReportViewIds`).
3. Đọc ngày giờ trên thanh trên (`"23 thg 11, 2026"`, `"19:02"`) nếu đang hiện.
4. `takeScreenshot()` của Trợ năng → thu nhỏ 1/4.
5. `ViewerMatcher` tìm ảnh khớp:
   - ứng viên: ảnh chụp trong khoảng phút đó (nếu đọc được ngày giờ), không thì lần lượt 300 ảnh gần nhất;
   - với mỗi ứng viên, tính vị trí ảnh khi hiện "vừa màn hình" (fit-center), lấy lưới 14×28 điểm màu từ ảnh thu nhỏ trong dải giữa màn hình (bỏ thanh trên/dưới);
   - sai khác = trung bình **70% điểm khớp nhất** (bỏ qua toast, hiệu ứng AI che ảnh);
   - chấp nhận nếu sai khác ≤ 16 (thang 0–255). Thực tế: ~6–9, mất ~0,1 giây.
6. `FlyView` (cửa sổ toàn màn hình, không nhận chạm, alpha 0.8) cho ảnh bay lên đảo; khay mở ra ở **chế độ chạm**.

## 5. Thẻ "Đã sao chép"

- `ViewerWatcher` nhận `TYPE_VIEW_CLICKED` có nhãn "Sao chép"/"Copy" (hoặc toast "Đã sao chép"/"Copied").
- Từ Android 10, chỉ app đang được focus mới đọc được bộ nhớ tạm → mở `ClipReadActivity` trong suốt, đợi `onWindowFocusChanged(true)`, đọc, đóng ngay.
- Bỏ qua `ClipDescription.EXTRA_IS_SENSITIVE`.
- `IslandView` hiện thẻ 2 dòng; giữ thẻ là bắt đầu "kéo" do chính đảo theo dõi ngón tay (vì ngón tay đã nằm trong cửa sổ của đảo).

## 6. Danh sách app

`ShareTargets` hỏi `PackageManager` các activity nhận `ACTION_SEND` theo nhóm nội dung (`image/*`, `video/*`, `text/plain`, `*/*`), lưu đệm và nạp lại ở luồng nền sau mỗi lần kéo. Thứ tự: ghim → dùng nhiều → app nhắn tin quen thuộc → theo tên. `<queries>` trong manifest để thấy app khác trên Android 11+.

## 7. Chống lỗi

- Mọi callback kéo thả / Trợ năng bọc `try/catch`, lỗi chỉ ghi log.
- Cửa sổ overlay thêm thất bại → dịch vụ tự dừng.
- `ClipReadActivity` tự đóng sau 1,2 giây nếu không nhận được focus.
- `ShareProvider` chỉ cho đọc, chặn truy cập ra ngoài thư mục chia sẻ (path traversal).
