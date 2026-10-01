# Kết quả thực chạy StudyMate 4.1

versionCode 7; versionName 4.1-report-ui; schema SQLite 5. APK SHA256: 4fdd0085a108f109b117cd0490c84133332e10ce241ca77e0fff8c46ebbfec64.

| Kiểm tra | Kết quả |
|---|---|
| assembleDebug, assembleDebugAndroidTest | BUILD SUCCESSFUL |
| testDebugUnitTest | 31/31 đạt |
| AndroidJUnitRunner, AOSP Android 13/API 33 | 37/37 đạt; 55,286 giây |
| lintDebug | 0 lỗi, 40 cảnh báo |

Tổng 68 hàm kiểm thử khác nhau. 37 Android gồm 36 hồi quy của bản 4.0 và DeadlinePresentationTest kiểm tra nhãn, lý do, các nhóm deadline và trạng thái hoàn thành. EnvironmentProbe là bộ hỗ trợ kịch bản, không tính vào tổng này. Log thực chạy ở verification; BUILD_ID.json ghi dấu SHA256 source và APK. Instrumentation chạy trực tiếp qua AndroidJUnitRunner, không tuyên bố đã chạy Gradle connectedDebugAndroidTest.

Môi trường: Windows, JBR 21, Gradle 8.13, AGP 8.7.3, Java 17 source/target, SDK 35, Build Tools 34.0.0. Máy ảo API 33 x86_64 1080×1920 density420 RAM2048.

## Bằng chứng kế thừa có giới hạn

verification/baseline-4.0 chứa nguyên nhật ký và định danh APK 4.0: 31 logic, 36 Android trên hai cấu hình API 33; lượt deep IDLE cưỡng bức 902 giây, phát chậm 89,496 giây; reboot, force-stop, quyền, múi giờ, offline. Các phép đo này KHÔNG phải lượt đo lại trên APK 4.1. Bản 4.1 chỉ đổi Ui.java, thêm kiểm thử và tăng phiên bản; lược đồ, luật tính điểm và bộ nhắc giữ nguyên.

Chưa kiểm chứng điện thoại thật, API khác, Doze tự nhiên qua đêm, mọi cỡ chữ/hướng xoay hoặc mọi biến thể 33 mã DL/NV/AD trong Word. Không đồng nhất 68 hàm với toàn bộ tình huống thiết kế. 40 cảnh báo lint được giữ đầy đủ để tiếp tục cải thiện.
