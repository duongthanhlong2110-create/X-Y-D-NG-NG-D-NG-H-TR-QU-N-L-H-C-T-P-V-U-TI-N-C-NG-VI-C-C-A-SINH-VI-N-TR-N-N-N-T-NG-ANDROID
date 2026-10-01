# StudyMate 4.1 — đồng bộ báo cáo và giao diện

Java + XML + SQLite. versionCode 7, versionName 4.1-report-ui, SQLite schema 5; Android 8.0 trở lên.

## Mở và chạy

1. Giải nén StudyMate-DongBoBaoCao-AndroidStudio.zip.
2. Android Studio → File → Open → chọn **StudyMate-DongBoBaoCao**, thư mục chứa settings.gradle; không chọn thư mục cha outputs.
3. Dùng Gradle JDK 17 hoặc JDK 21 đi kèm Android Studio; cài SDK Platform 35 và Build Tools 34.0.0. Đợi Sync hoàn tất.
4. Chọn cấu hình app, chọn máy ảo/điện thoại, nhấn Run ▶. Cho phép thông báo khi được hỏi.
5. Tài khoản mẫu demo@studymate.vn / Demo123! hoặc đăng ký tài khoản riêng.

Có thể kéo APK vào máy ảo để cài. APK dùng cùng chữ ký debug với các bản bàn giao trước. **Cài cập nhật, không gỡ app hoặc xóa dữ liệu** để giữ tài khoản và nâng cấp dữ liệu. Nếu tự build bằng khóa debug khác, dùng lại khóa của bản đang cài hoặc chạy trên máy ảo riêng; không xử lý lỗi chữ ký bằng cách xóa bài cũ.

## Chức năng

Đăng nhập/đăng ký/đăng xuất; Dashboard; môn học; công việc, trạng thái, tìm kiếm/lọc và ưu tiên; lịch học ngày/tuần, cảnh báo giao giờ; lịch thi; ghi chú; điểm trung bình có trọng số; cài đặt và thông báo nhắc.

Công việc có thể không có deadline. Chọn mốc nhắc chính rồi chạm **Mốc nhắc bổ sung** để chọn nhiều mốc. Các mốc trùng nhau được gộp; Không nhắc ở mốc chính tắt tất cả. Mỗi mốc có dấu đã phát riêng. Những mốc của cùng một công việc cập nhật cùng thẻ Notification.

## Dữ liệu và quy tắc

Sáu bảng chính: users, subjects, tasks, class_schedules, exams, reminders. notes và grades giữ hai chức năng mở rộng. entries là view đọc tổng hợp, không còn là bảng lưu chung. Xem SCHEMA.md và SCHEMA.sql.

Nâng cấp giữ id, tài khoản, liên kết hợp lệ; lưu bản gốc trong legacy_entries_v4. Liên kết môn sai được ghi migration_issues và chuyển sang mục cá nhân. Dữ liệu vi phạm ràng buộc khác gây rollback, giữ nguyên database cũ. Khi gặp lỗi nâng cấp, giữ dữ liệu để kiểm tra, không tự gỡ/cài lại.

Điểm = khẩn cấp (100/70/40/10/0) + priority × 10 + 5 nếu Đang làm. Bỏ việc Hoàn thành. Phá hòa: điểm giảm → deadline tăng (không hạn cuối khi bằng điểm) → id tăng. Dashboard và Ưu tiên dùng chung SmartPlanner.

## Báo cáo và kiểm thử

DOI_CHIEU_BAO_CAO.md chốt cách triển khai; BUILD_REPORT.md và verification chứa bằng chứng thực chạy. File DAK_Duong_Thanh_Long_DongBo_StudyMate_4_1.docx là bản báo cáo đầy đủ đã chỉnh từ tài liệu gốc. Phần khảo sát ghi rõ dữ liệu giả định; cần khảo sát thực tế trước khi khẳng định các tỷ lệ đó.

Chạy logic/build: `gradlew.bat assembleDebug testDebugUnitTest lintDebug`.
Chạy Android trên máy ảo kiểm thử riêng: `gradlew.bat connectedDebugAndroidTest`.
EnvironmentProbe phục vụ kịch bản kiểm thử có điều khiển; mặc định được bỏ qua nếu không có action. Không chạy kiểm thử instrumentation trên thiết bị chứa dữ liệu cá nhân cần giữ.

Thông báo phụ thuộc quyền và cơ chế tiết kiệm pin. Bằng chứng máy ảo API 33 không thay thế kiểm thử trên mọi điện thoại, mọi API hoặc Doze qua đêm.

## Thay đổi bản 4.1

Nhãn deadline ghép nhóm với thời gian còn lại, ví dụ Rất gần hạn · Còn 2 giờ 10 phút. Lý do ưu tiên hiển thị nhóm deadline, mức ưu tiên và trạng thái. Việc đã hoàn thành có nhãn riêng và được loại khỏi Ưu tiên. Bộ kiểm thử bản này: 31 logic + 37 Android = 68 hàm; lint 0 lỗi, 40 cảnh báo. Bằng chứng Doze/reboot và cấu hình phụ thuộc bản 4.0, lưu riêng trong verification/baseline-4.0.
