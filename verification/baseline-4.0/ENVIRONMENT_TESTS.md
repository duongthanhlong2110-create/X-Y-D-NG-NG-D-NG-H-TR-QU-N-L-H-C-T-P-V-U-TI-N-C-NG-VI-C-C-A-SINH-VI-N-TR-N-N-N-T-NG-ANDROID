# Chạy lại các biến thể Android

Chỉ sử dụng máy ảo thử nghiệm riêng: bộ kiểm thử đăng xuất, tạo tài khoản giả, thay đổi quyền, mạng, pin và khởi động lại máy ảo. Không dùng trên điện thoại hoặc máy ảo đang lưu bài cần giữ. Mỗi script yêu cầu --serial để tránh chọn nhầm thiết bị.

1. Build `gradlew.bat assembleDebug assembleDebugAndroidTest`.
2. Cài bằng `adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk` và APK trong `app/build/outputs/apk/androidTest/debug`.
3. `python tools/test_android_states.py --serial SERIAL` chạy quyền, foreground/background, tắt màn hình, force-stop, reboot, đổi múi giờ và ngoại tuyến trên API 33.
4. `python tools/test_doze.py --serial SERIAL` quan sát 15 phút deep IDLE, lấy notified_at và dấu handled_at. Ngưỡng đặt trước lượt đo là 15 phút. Android không được hứa đúng từng giây.
5. Chạy các lớp JUnit bằng Android Studio hoặc `gradlew.bat connectedDebugAndroidTest`. EnvironmentProbe mặc định bị bỏ qua; hai script truyền action riêng.

Các file JSON ghi oracle due/id, timestamp, biến thể và SHA256 APK đang được kiểm tra. `notified_at` là thời điểm app ghi nhận sau khi gọi NotificationManager thành công; không phải thời điểm người dùng đọc thông báo. Kiểm tra thêm thẻ Notification/ảnh màn hình khi đo.

Các máy ảo trong lần bàn giao: Android 13/API33 x86_64, 1080×1920 density420 RAM2048 và 720×1280 density320 RAM1536. Máy nhỏ được chạy bộ kiểm thử với font_scale=1.3. RotationTest dùng ActivityScenario.recreate để kiểm tra lưu trạng thái, không tự suy ra mọi biến thể xoay thiết bị đều đạt.

Nếu ngắt script giữa chừng, khôi phục máy ảo thử bằng `adb -s SERIAL shell dumpsys deviceidle unforce`, `dumpsys battery reset`, `input keyevent 224`, `settings put system font_scale 1.0`, `settings put system user_rotation 0` và bật lại mạng trong Settings. Script không thay đổi cấu hình máy thật hoặc thiết bị khác serial được chọn.
