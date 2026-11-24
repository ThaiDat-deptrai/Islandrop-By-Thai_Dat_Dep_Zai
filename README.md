# Islanđrop By Thai_Dat_Dep_Zai :)

> Kéo ảnh, chữ lên **đảo** (chỗ camera) rồi thả vào app để gửi — giống "Origin Island" của Vivo, làm cho máy Android khác.

IslandDrop là một app Android nhỏ, viết bằng Java, **không cần root**, không sửa hệ thống. Nó vẽ một khay màu đen mọc ra từ chỗ camera đục lỗ, nhìn như đảo (Dynamic Island) giãn ra, để bạn chia sẻ nội dung sang app khác chỉ bằng một cú kéo.

Được làm và thử trên **Tecno Camon 30 5G (CL6), Android 16, HiOS 16.3, màn hình 120Hz**.

---

## ✨ Tính năng

| Tính năng | Cách dùng |
|---|---|
| **Kéo ảnh lên đảo** | Ở lưới ảnh trong Thư viện, giữ một ảnh (hoặc chọn nhiều ảnh) rồi kéo lên chỗ camera. Khay giãn ra với các app; rê tới app nào thì icon phóng to và máy rung nhẹ; thả để gửi. |
| **Kéo chữ lên đảo** | Bôi đen một đoạn chữ ở bất kỳ app nào, giữ lên phần bôi đen rồi kéo lên đảo. |
| **Giữ ảnh đang xem** *(Thư viện Tecno)* | Mở một ảnh, giữ ngón tay lên ảnh: ảnh thu nhỏ **bay lên đảo**, khay mở ra để chạm chọn app. |
| **Thẻ "Đã sao chép"** | Bấm **Sao chép / Copy** ở bất kỳ app nào: đảo hiện thẻ xem trước. Giữ thẻ rồi kéo tới app, hoặc chạm thẻ rồi chạm app. |
| **Nút "+" thêm app** | Ô cuối khay. Mở danh sách mọi app nhận chia sẻ: **Ghim lên đầu / Bình thường / Ẩn**. App cài sau này tự có trong danh sách. |
| **Tự cuộn mượt** | Khay nhiều app: rê sát mép trái/phải để tự cuộn (có gia tốc), hoặc vuốt ngang khi ở chế độ chạm. |
| **Hiệu ứng lò xo 120Hz** | Mọi chuyển động dùng lò xo vật lý, nảy nhẹ giống Vivo. |
| **Thông minh dần** | App bạn hay gửi tự lên đầu khay. |

Khi không kéo gì, cửa sổ của app **vô hình và không cản chạm** (độ mờ ≤ 0.8 nên Android cho chạm xuyên qua).

---

## 📱 Yêu cầu

- Android **10 trở lên** (minSdk 29). Đã thử trên Android 16.
- Máy có camera đục lỗ ở giữa mép trên sẽ đẹp nhất.
- Tính năng **giữ ảnh đang xem** hiện chỉ hỗ trợ **Thư viện HiOS (`com.gallery20`) bản tiếng Việt** trên Tecno / Infinix / itel. Các tính năng còn lại chạy trên đa số máy Android.

---

## 📥 Cài đặt

1. Tải file `.apk` ở mục **Releases** (hoặc tự build — xem bên dưới).
2. Mở file trên điện thoại, cho phép **"Cài ứng dụng không rõ nguồn gốc"**.
3. Mở app **IslandDrop** và bấm lần lượt:
   - **Cấp quyền hiển thị trên ứng dụng khác** — bắt buộc, để vẽ khay.
   - **Cho phép thông báo** — không bắt buộc.
   - **Cho chạy nền** — nên bật, để máy không tắt app khi tiết kiệm pin.
   - **Giữ ảnh đang xem: cho phép đọc ảnh** và **bật Trợ năng** — chỉ cần nếu dùng tính năng giữ ảnh / thẻ "Đã sao chép".
   - **BẬT khay kéo thả**.
4. Trên Xiaomi / Oppo / Vivo / Tecno: vào cài đặt pin của máy, bật **Tự khởi động** cho IslandDrop.

> ⚠️ Bản build hiện tại là bản **debug**. Play Protect có thể cảnh báo khi cài — đây là cảnh báo chung cho app không tải từ CH Play.

Muốn tắt: mở app → **TẮT khay kéo thả**. Muốn gỡ: gỡ như mọi app khác, máy trở lại như cũ.

---

## 🔐 Quyền và lý do

| Quyền | Để làm gì |
|---|---|
| Hiển thị trên ứng dụng khác | Vẽ khay ở mép trên màn hình. |
| Dịch vụ chạy nền (foreground service) | Giữ khay luôn sẵn sàng; hiện một thông báo nhỏ "IslandDrop đang chạy". |
| Trợ năng *(tùy chọn)* | Nhận biết khi bạn giữ ảnh trong Thư viện Tecno, hoặc bấm nút "Sao chép". **Không chặn hay thay đổi thao tác chạm.** |
| Đọc ảnh *(tùy chọn)* | Tìm đúng tấm ảnh bạn đang xem để gửi. |
| Khởi động cùng máy | Tự bật lại khay sau khi khởi động máy, nếu bạn đã bật trước đó. |

Mọi xử lý diễn ra **trong máy**, app **không có quyền Internet**. Chi tiết: [PRIVACY.md](PRIVACY.md).

---

## 🛠 Tự build từ mã nguồn

Cần **JDK 17** và **Android SDK** (platform 36).

```bash
git clone https://github.com/ThaiDat-deptrai/Islandrop-By-Thai_Dat_Dep_Zai.git
cd Islandrop-By-Thai_Dat_Dep_Zai
# tạo local.properties trỏ tới Android SDK, ví dụ:
# sdk.dir=C\:\\Users\\<bạn>\\AppData\\Local\\Android\\Sdk
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
```

File cài nằm ở `app/build/outputs/apk/debug/app-debug.apk`. Cài qua cáp:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Bật nhanh từ máy tính (sau khi đã cấp quyền hiển thị trên app khác):

```bash
adb shell am start -n vn.islanddrop/.MainActivity --ez start true
```

Xem log của app (ghi ra file vì log hệ thống HiOS xoay vòng rất nhanh):

```bash
adb shell run-as vn.islanddrop cat files/log.txt
```

---

## 🗂 Cấu trúc mã nguồn

```
app/src/main/java/vn/islanddrop/
├── DropService.java        Dịch vụ nền: giữ cửa sổ overlay, điều phối khay
├── IslandView.java         Khay hình viên thuốc: vẽ, lò xo, kéo thả, chạm, cuộn
├── Spring.java             Lò xo vật lý cho chuyển động
├── ShareTargets.java       Danh sách app nhận chia sẻ, sắp xếp, ghim/ẩn
├── DropActivity.java       Màn hình trong suốt nhận quyền đọc nội dung vừa thả
├── Sharer.java             Chép tệp và mở app đích (ACTION_SEND)
├── ShareProvider.java      Cung cấp tệp đã chép cho app nhận (chỉ đọc)
├── ViewerWatcher.java      Trợ năng: giữ ảnh trong Thư viện, nút Sao chép
├── ViewerMatcher.java      Nhận ra ảnh đang xem bằng so màu với ảnh thu nhỏ
├── FlyView.java            Hiệu ứng ảnh bay lên đảo
├── ClipReadActivity.java   Đọc bộ nhớ tạm ngay sau khi bấm Sao chép
├── AppPickerActivity.java  Màn hình "Thêm app" (ghim / ẩn)
├── MainActivity.java       Màn hình cài đặt, xin quyền
├── BootReceiver.java       Tự bật lại sau khởi động / cập nhật
└── L.java                  Ghi log ra file
```

Cách hoạt động chi tiết: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## ⚠️ Giới hạn đã biết

- **Giữ ảnh đang xem rồi kéo liền một mạch như Vivo là không làm được**: trong lúc ngón tay đang giữ, Thư viện nắm cử động đó và Android không cho app khác theo dõi. Vì vậy khay mở ra để **chạm** chọn app.
- Nhận ra ảnh đang xem tốt nhất khi **thanh ngày giờ phía trên đang hiện** và ảnh **không bị phóng to**.
- Ảnh có chủ thể rõ (người, vật): Thư viện Tecno tự cắt chủ thể và cho kéo phần đó — khi ấy thứ được gửi là phần đã cắt.
- Thẻ "Đã sao chép" nhận biết nút theo chữ **"Sao chép" / "Copy"**. Nút chỉ có icon có thể không được nhận. Thanh chọn chữ của Gmail không báo sự kiện bấm — dùng cách **kéo chữ đã bôi đen** thay thế.
- Lúc bấm Sao chép, màn hình có thể chớp nhẹ (một màn hình trong suốt mở ra trong tích tắc để đọc bộ nhớ tạm, vì Android 10+ chỉ cho app đang được focus đọc).

---

## 📄 Tài liệu khác

- [CHANGELOG.md](CHANGELOG.md) — lịch sử thay đổi
- [PRIVACY.md](PRIVACY.md) — quyền riêng tư
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — kiến trúc, cách hoạt động
- [CONTRIBUTING.md](CONTRIBUTING.md) — đóng góp
- [SECURITY.md](SECURITY.md) — báo lỗi bảo mật

## 📜 Giấy phép

[MIT](LICENSE) © 2026 ThaiDat-deptrai
