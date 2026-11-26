# CAD Viewer DWG — Ứng dụng Android đọc bản vẽ AutoCAD (.DWG / .DXF)

Ứng dụng Android viết bằng **Kotlin + Jetpack Compose + C++ JNI**, hỗ trợ xem bản vẽ 2D mượt mà trên điện thoại, bật/tắt Layer, đo khoảng cách 2 điểm và bóc tách khối lượng xuất file CSV (mở trực tiếp trong Excel / Google Sheets).

## Tính năng có sẵn
- **Mở trực tiếp file `.dxf` và `.dwg`** từ trình quản lý tệp hoặc chia sẻ từ Zalo / Google Drive.
- **Đọc thuần Kotlin cho `.dxf` (R12 – R2018+)**: giải mã `LAYER`, `LINE`, `LWPOLYLINE` (tính chuẩn cả đoạn cong Bulge), `CIRCLE`, `ARC`, `TEXT`, `MTEXT`, `INSERT` (Block).
- **Hỗ trợ `.dwg` hai lớp**:
  - Đọc ngay phiên bản AutoCAD (`AC1015` – `AC1032`) và trích xuất ảnh xem trước (Preview BMP/PNG) nhúng trong tệp `.dwg`.
  - Sẵn cầu nối C++ JNI (`app/src/main/cpp/dwg_jni.cpp`) liên kết **GNU LibreDWG** (`libredwg.a`) để giải mã vector `.dwg` trực tiếp trên điện thoại mà không cần internet.
- **Xem 2D & Đo đạc**: Phóng to/thu nhỏ 2 ngón, di chuyển bản vẽ, nút thu gọn toàn bản vẽ, công cụ đo khoảng cách 2 điểm.
- **Bóc tách khối lượng & Xuất CSV**: Tính tổng mét dài từng Layer, điều chỉnh đơn vị (`mm`, `cm`, `m`), cộng hệ số hao hụt theo mét dài (%), đếm số lượng Block thiết bị và xuất file `.csv` chuẩn UTF-8 BOM.

## Cách đóng gói thành file `.apk` cài lên điện thoại Android

### Cách 1: Tự động tạo file `.apk` trên GitHub (không cần cài Android Studio)
1. Tạo một kho mã nguồn (repository) mới trên GitHub và tải toàn bộ thư mục dự án này lên.
2. Mở tab **Actions** → chọn **Build Android APK** → bấm **Run workflow**.
3. Sau khoảng 2–3 phút, tải tệp **`CADViewerAndroid-debug-apk`** về điện thoại Android, giải nén và cài đặt tệp `app-debug.apk`.

### Cách 2: Biên dịch trực tiếp bằng Android Studio trên máy tính
1. Mở **Android Studio** → chọn **Open** → trỏ vào thư mục `CADViewerAndroid`.
2. Đợi Gradle đồng bộ xong → vào menu **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
3. Copy tệp `app/build/outputs/apk/debug/app-debug.apk` sang điện thoại để cài đặt.

## Kích hoạt giải mã vector `.dwg` gốc bằng GNU LibreDWG (tùy chọn nâng cao)
Mặc định dự án biên dịch thành công ngay lập tức mà không cần tải thêm thư viện ngoài. Nếu muốn giải mã trực tiếp toàn bộ nét vẽ bên trong file `.dwg` nhị phân mà không cần chuyển sang `.dxf`:
1. Biên dịch chéo thư viện [GNU LibreDWG](https://www.gnu.org/software/libredwg/) bằng Android NDK cho `arm64-v8a` và `armeabi-v7a`.
2. Đặt tệp `libredwg.a` vào `app/src/main/cpp/third_party/libredwg/<ABI>/libredwg.a` và các tệp header vào `app/src/main/cpp/third_party/libredwg/include/`.
3. Biên dịch lại APK — `CMakeLists.txt` sẽ tự động phát hiện và bật cờ `HAVE_LIBREDWG=1`.
