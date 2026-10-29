# Hoa Sen Từ Vựng – dự án Android

App học 295 từ vựng theo phương pháp lặp lại ngắt quãng. Chạy offline, tiến độ lưu trong máy.
Phát âm dùng giọng đọc Google có sẵn trên điện thoại.

## Chức năng

- **Học theo lịch lặp lại ngắt quãng**, 9 dạng câu hỏi đổi theo mức độ nhớ.
- **3 câu ví dụ cho mỗi từ**: mỗi lần ôn, app xoay sang câu khác.
- **Cụm từ hay gặp, họ từ, mẹo nhớ và gốc từ** trên mỗi thẻ; chạm vào từ cùng họ để mở thẻ của từ đó.
- **Tab Luyện**:
  - *Nghe rảnh tay*: app tự đọc từ, nghĩa tiếng Việt, câu ví dụ; nghe được khi tắt màn hình.
  - *Luyện phát âm*: nói vào micro, app chấm từ và tô màu từng chữ trong câu ví dụ.
  - *Xếp lại câu*: sắp các mảnh từ thành câu đúng.
  - *Ôn nhanh*: trắc nghiệm.
  - *Gốc từ*: các tiền tố, hậu tố trong danh sách.
- **Thống kê chi tiết**: lịch học 17 tuần, tỷ lệ nhớ, thời gian học, dự báo 30 ngày, ngày dự kiến học xong, các từ hay quên.

Lưu ý về micro: lần đầu chạm nút micro, Android sẽ hỏi quyền ghi âm, hãy chọn **Cho phép**. Nhận dạng giọng nói dùng dịch vụ của Google nên thường cần có mạng (trừ khi máy đã tải gói nhận dạng tiếng Anh ngoại tuyến).

Nghe nghĩa tiếng Việt cần giọng tiếng Việt của Dịch vụ lời nói của Google. Nếu máy chưa có, app chỉ đọc phần tiếng Anh.

Có hai cách để có file APK. Chọn một.

---

## Cách 1: Để GitHub build giúp (không cần cài gì, làm trên điện thoại hoặc máy tính đều được)

1. Tạo tài khoản miễn phí tại https://github.com (nếu chưa có).
2. Bấm **+** → **New repository**. Đặt tên `HoaSenTuVung`, chọn **Public**, bấm **Create repository**.
3. Trên trang repo mới, bấm **uploading an existing file**.
4. Giải nén file zip này, rồi kéo **toàn bộ nội dung bên trong thư mục `HoaSenTuVung`** vào trang (gồm cả thư mục `.github`, `app`, `gradle` và các file `gradlew`, `build.gradle`…). Bấm **Commit changes**.
   - Nếu trình duyệt không kéo được thư mục `.github` (thư mục ẩn), hãy bấm **Add file → Create new file**, gõ tên `.github/workflows/build-apk.yml`, dán nội dung file đó vào, rồi Commit.
5. Mở tab **Actions**. GitHub tự build trong khoảng 3–5 phút. Khi thấy dấu tích xanh là xong.
6. Mở trang chính của repo → mục **Releases** ở cột bên phải → tải **HoaSenTuVung.apk**.

Mỗi lần bạn sửa file trong repo, GitHub sẽ build lại bản mới.

## Cách 2: Build bằng Android Studio (trên máy tính)

1. Cài Android Studio từ https://developer.android.com/studio.
2. **File → Open**, chọn thư mục `HoaSenTuVung`. Chờ Gradle tải xong (lần đầu khá lâu).
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. File nằm ở `app/build/outputs/apk/debug/app-debug.apk`.
   Hoặc cắm điện thoại (bật Gỡ lỗi USB) rồi bấm nút ▶ Run để cài thẳng.

---

## Cài APK lên điện thoại

1. Chép file APK vào điện thoại (hoặc tải thẳng từ Releases bằng Chrome trên điện thoại).
2. Mở file. Android sẽ hỏi cho phép cài từ nguồn này: chọn **Cài đặt → Cho phép**, rồi quay lại bấm **Cài đặt**.
3. Nếu Play Protect cảnh báo "ứng dụng không xác định", chọn **Vẫn cài đặt**. Cảnh báo này xuất hiện với mọi app tự build không qua Google Play.

## Không nghe thấy phát âm?

Vào **Cài đặt Android → Hệ thống → Ngôn ngữ → Đầu ra chuyển văn bản thành giọng nói**, chọn **Dịch vụ lời nói của Google**, tải giọng **English (United Kingdom)** và **English (United States)**. Trong app, mục Cài đặt cũng có nút mở thẳng trang này.

## Cập nhật và sao lưu

- Dự án đi kèm khoá ký cố định (`app/hoasen.keystore`), nên bản build mới cài đè lên bản cũ mà vẫn giữ tiến độ.
- Gỡ app sẽ xoá tiến độ. Trước khi gỡ hoặc đổi máy, vào **Cài đặt → Sao lưu** để lấy mã, rồi dán mã vào app trên máy mới.
- Mã sao lưu dùng chung được giữa app Android và bản web.

## Sửa danh sách từ

Toàn bộ nội dung nằm trong `app/src/main/assets/index.html`. Danh sách từ là mảng `LINES` ở đầu phần `<script>`, mỗi dòng có dạng:

```
từ|/phiên âm/|nghĩa tiếng Việt|từ loại|emoji|mô tả hình|Câu ví dụ tiếng Anh.|Bản dịch câu ví dụ.
```

Câu ví dụ cần chứa đúng từ đó để dạng câu hỏi "điền từ" hoạt động.
