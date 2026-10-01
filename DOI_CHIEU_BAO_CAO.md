# Đối chiếu báo cáo — StudyMate 4.1

Báo cáo đầy đủ: DAK_Duong_Thanh_Long_DongBo_StudyMate_4_1.docx. Java/XML/SQLite, không Kotlin, Compose, MVVM hay AI.

| Yêu cầu | Hiện thực / bằng chứng |
|---|---|
| Tài khoản email | LoginActivity, PasswordHasher, DatabaseTest |
| Sáu bảng lõi | users, subjects, tasks, class_schedules, exams, reminders; SCHEMA.md/sql, NormalizedDatabaseTest |
| Chức năng mở rộng | notes, grades; ghi chú và điểm trong Tiện ích |
| Bốn nhóm deadline | Quá hạn; 0–24h Rất gần hạn; >24–72h Sắp đến hạn; >72h Còn thời gian; thêm Chưa đặt deadline và Đã hoàn thành |
| Điểm và phá hòa | 100/70/40/10/0 + priority×10 +5 Đang làm; điểm giảm, hạn tăng, id tăng; loại Hoàn thành |
| Nhãn và lý do | Ui.bindTask; DeadlinePresentationTest |
| Hoàn thành / xóa | Hủy nhắc và Notification; bộ phát đọc lại dữ liệu; EnvironmentLifecycleTest, ReminderIntegrationTest |
| Sửa hạn / mở lại | Reset dấu liên quan, đối soát revision; ReportDatabaseTest |
| Trùng lịch | ScheduleRules, lựa chọn Sửa lại / Vẫn lưu; ScheduleReminderTest |
| Kiểm thử | 68 hàm đạt trên 4.1; số đo môi trường 4.0 được ghi riêng |

Word đã thay các sơ đồ và ảnh app. Số liệu khảo sát vẫn chỉ là giả định minh họa, không được trình bày thành khảo sát thật. Phạm vi chưa thử nằm ở mục 3.6 và BUILD_REPORT.md.
