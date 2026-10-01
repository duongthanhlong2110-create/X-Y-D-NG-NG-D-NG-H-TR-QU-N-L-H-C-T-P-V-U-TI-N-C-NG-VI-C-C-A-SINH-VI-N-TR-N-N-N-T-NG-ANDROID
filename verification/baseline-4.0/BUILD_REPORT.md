# Kết quả kiểm tra StudyMate 4.0

Phiên bản 4.0-normalized, versionCode 6, SQLite 5. Build và kiểm thử tự động ngày 30/09/2026; lượt quan sát Doze cuối ngày 01/10/2026. SHA256 APK: `ab3da0b9ac76dc70b5b01f164b3d55e13601879a11133efc4dcacf81272ff0f7`.

## Kết quả thực chạy

| Kiểm tra | Kết quả |
|---|---|
| assembleDebug và assembleDebugAndroidTest | BUILD SUCCESSFUL |
| testDebugUnitTest | 31/31 đạt |
| AndroidJUnitRunner máy ảo chính | 36/36 đạt, 59,779 giây |
| AndroidJUnitRunner máy ảo nhỏ, cỡ chữ 1,3 | 36/36 đạt, 73,134 giây |
| lintDebug | 0 lỗi, 40 cảnh báo |
| Deep IDLE cưỡng bức bằng adb | Theo dõi 902 giây; ghi nhận phát chậm 89.496 giây so với hạn |

Tổng **67 hàm kiểm thử khác nhau** = 31 logic + 36 Android. Việc chạy lại 36 hàm trên cấu hình thứ hai không cộng thành 103 hàm khác nhau. EnvironmentProbe là bộ hỗ trợ kịch bản, bị bỏ qua khi không truyền action; không tính thêm vào 67.

36 hàm Android: DatabaseTest 6, MigrationTest 3, NormalizedDatabaseTest 10, EnvironmentLifecycleTest 3, PerformanceTest 1, ReminderIntegrationTest 2, ReportDatabaseTest 8, ReportUiTest 1, RotationTest 1, UiSmokeTest 1.

## Môi trường và cách chạy

Windows, JBR 21, Gradle 8.13, AGP 8.7.3, Java source/target 17, compile/target SDK 35, Build Tools 34.0.0. Hai máy ảo AOSP Android 13/API 33 x86_64: 1080×1920 density420 RAM2048; 720×1280 density320 RAM1536 font_scale1.3. Không tác động dữ liệu trên thiết bị đang dùng của sinh viên.

Instrumentation chạy trực tiếp qua AndroidJUnitRunner, loại EnvironmentProbe khỏi lượt tổng. Không ghi nhận là đã chạy Gradle connectedDebugAndroidTest. README hướng dẫn người dùng chạy lại bằng Android Studio hoặc Gradle trên thiết bị thử riêng.

40 cảnh báo lint còn lại được giữ nguyên trong verification/lint-results.txt; build không có lỗi. Không coi lint đạt là đã hết mọi vấn đề chất lượng hoặc đã kiểm thử mọi phiên bản Android.

## Cơ sở dữ liệu và nghiệp vụ

- Sáu bảng lõi User/Subject/Task/ClassSchedule/Exam/Reminder đã là bảng vật lý; notes/grades giữ chức năng mở rộng. entries là view chỉ đọc. Xem SCHEMA.md và SCHEMA.sql.
- Hỗ trợ nâng cấp DB1/2/3/4 sang DB5. Đã thử fixture DB1, các biến thể DB2 và DB4; chưa có lượt riêng cho fixture DB3. Kiểm tra giữ id, tài khoản và dữ liệu; liên kết sai có nhật ký và bản sao gốc; lỗi miền giá trị rollback toàn giao dịch. FK ghép chặn liên kết khác tài khoản, Reminder có đúng một đối tượng và nhiều mốc riêng.
- Có kiểm thử lưu lại sau lỗi ràng buộc, thứ tự mốc chính, cascade, xóa môn đang dùng, nhiều mốc không nhắc trùng.
- Quy tắc chốt: khẩn cấp 100/70/40/10/0 + priority×10 +5 nếu Đang làm; loại Hoàn thành; phá hòa deadline rồi id. Bộ T01–T08 dùng thời điểm cố định, điểm 120/90/95/60/60/30/20/loại; thứ tự 1/3/2/4/5/6/7; Dashboard 8/1/4/1/12,5%.

## Các kịch bản Android có bằng chứng

- Cấp/từ chối quyền thông báo rồi cấp lại; không ghi nhận phát khi thiếu quyền và nhắc bù sau cấp lại, mở app.
- Foreground, background và tắt màn hình: phát trong ngưỡng quan sát 180 giây.
- Doze: deep IDLE cưỡng bức bằng adb, quan sát ít nhất 15 phút trên APK có SHA256 khớp bản bàn giao. Ngưỡng độ trễ đặt trước là 900 giây, không phải cam kết thời gian của sản phẩm.
- Reboot thực: boot_id trước/sau khác nhau; nhắc được phát sau mở khóa thiết bị, trước khi mở lại app.
- Force-stop: không phát khi bị dừng; mở app khôi phục nhắc bù.
- Đổi múi giờ giữ deadline tuyệt đối; mở lại ngoại tuyến giữ dữ liệu.
- AD04: sửa hạn/hoàn thành/xóa không để thông báo cũ tồn tại. AD08: gửi PendingIntent đã giữ từ tài khoản cũ bị từ chối sau đổi phiên.
- AD10: ActivityScenario.recreate giữ bản nháp, không lưu trùng. Ảnh từ APK trên màn hình nhỏ/cỡ chữ lớn kiểm tra nút lọc và bố cục.

Các JSON trong verification/environment chứa timestamp và oracle. notified_at là mốc app ghi nhận sau lệnh NotificationManager thành công, không phải thời điểm người dùng đọc; nhật ký Doze có thêm dump Notification. Ảnh giao diện dùng dữ liệu giả theo đồng hồ thực, không thay bằng chứng T01–T08 cố định.

## Phạm vi chưa được chứng minh

Chưa thử điện thoại thật, các hãng Samsung/Xiaomi, API khác, Doze tự nhiên qua đêm, mọi mức cỡ chữ, mọi hướng xoay hoặc toàn bộ biến thể đổi giờ hệ thống. NV08–NV10 có kiểm thử luật giao giờ; chưa tự động bấm riêng mọi nhánh dialog Sửa lại/Vẫn lưu. Không gộp 33 mã DL/NV/AD thành đạt toàn bộ và không tạo số liệu hiệu quả học tập chưa đo.

CAP_NHAT_BAO_CAO_StudyMate_4.docx là phần cập nhật kỹ thuật để ghép vào báo cáo gốc, không phải bản đã thay toàn bộ nội dung Word. Giữ các giới hạn kiểm thử ở mục 3.6 khi nộp bài.
