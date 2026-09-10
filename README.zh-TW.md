# OpenStar for Android

[English](README.md) | [繁體中文](README.zh-TW.md)

以 [OpenStar Python／Skyfield 引擎](https://github.com/kenktchau-cmyk/OpenStar) 為基礎的開源 Android 星圖。你可以用觸控探索天空，或使用可選的相機加指南針 AR 視圖。

## 取得原始碼

```sh
git clone --recurse-submodules https://github.com/kenktchau-cmyk/OpenStar-Android.git
cd OpenStar-Android
```

如已複製儲存庫但沒有引擎，請執行 `git submodule update --init --recursive`。

## 共用 Python 引擎

`engine/` 是固定至 OpenStar 某個 commit 的 Git submodule。Chaquopy 直接打包 `engine/python/`。`Catalog.calculate()` 呼叫 `StarMapGenerator.positions_json()`，後者再呼叫公開的 `generate_star_map()` 函數。原生 Android 介面繪製這些回傳位置。天文計算修改應在 Python 儲存庫進行，並於本專案驗證後更新 submodule commit。

`app/src/main/assets/stars.tsv` 的星表快照與 `engine/python/data/stars.tsv` 相符。更新引擎星表時，請一併複製至 Android asset 並保留來源標示。DE421 隨引擎附帶。獨立腳本、API 範例、相依套件及測試位於 [OpenStar](https://github.com/kenktchau-cmyk/OpenStar)。

## 功能

一般模式採用**與 AR 相同的透視投影**，背景為深色天空。**單指拖曳**可向左、右、上、下環視；**雙指縮放**可調整至 0.5×–6×。此模式由手指控制方向，不受手機動作或相機控制。方位／高度角讀數、中央十字及地平線協助你辨認方向。**按兩下**或選擇 **Reset map**，可回到朝北、地平線以上 35°、1× 縮放的視角。**Find star** 會將視角轉向所選恆星；地平線以下的恆星仍會隱藏，並標示為位於地平線以下。旋轉螢幕會保留視角及縮放。

使用星圖下方的**小時滑桿**，可按所選經緯度的時區，選擇**指定日期的 00:00–23:00**。標籤隨手指移動，放開後便計算該小時的星空。方向鍵每次調整一小時。按右側 **Live** 可回到目前日期及時間，每 30 秒更新一次。移動滑桿會離開 Live 模式。使用 **Time** 可選擇其他日期或精確分鐘。遇上夏令時間轉換，被跳過的小時會移至下一個有效本地時間；重複的小時則盡可能保留所選時間的 UTC 時差。

觀測時區會按座標自動選擇，並顯示其 IANA 名稱，例如 `Asia/Hong_Kong`。小時滑桿、日期／時間選擇器、Live 及 AR 均使用該時區，包括夏令時間規則。更改地點會保留原本的觀測時刻，並以新地點的本地日期／時間顯示。手動輸入 ISO 時間內的明確 UTC 時差仍會優先採用。初始自動時間會隨所選地點重新格式化，直至你親自修改。

應用程式透過 Chaquopy 17.0.0 附帶 Skyfield 1.54、Python 3.13、NumPy 及 JPL DE421。星空計算可離線進行；地理位置選擇器需要互聯網載入街道圖磚。沒有帳戶或分析追蹤。APK 支援 **Android 8 或以上的 ARM64 裝置**及 x86-64 模擬器，不包含較舊的 32 位元 CPU。NumPy 已重新建置，使用 16 KB 原生對齊及其隨附的 C 數值運算例程；詳見 `vendor/README.md`。

### 在地圖上選擇位置

初始時間／位置表單會要求前景定位權限，預設以**裝置位置**填入座標，亦支援大約位置權限。它會使用最近兩分鐘內的位置，或最多等待 20 秒取得新位置。如權限被拒絕、定位服務關閉或未能取得位置，會清楚標示保留的已儲存／參考座標，讓你手動選擇。輸入座標或點選地圖會取消尚未完成的定位要求。需要重試時可按 **My location**；應用程式不會在背景追蹤位置。

在初始表單按 **Choose on map**，或在星圖下方按 **Location**。點選地理地圖任意位置或拖曳圖釘，再按 **Use this location**。顯示的經緯度會隨圖釘更新。此畫面亦提供 **My location** 及 **Enter coordinates**。返回／取消不會改變原本的觀測地點。所選位置會儲存在裝置上，並同時用於一般星圖及 AR。

位置選擇器附帶 **Leaflet 1.9.4** 及 **Natural Earth 世界輪廓**。OpenStreetMap 透過 HTTPS 提供街道圖磚，不需要 API key，來源標示會一直保留。查看街道圖磚時，所查看的圖磚範圍及你的 IP 位址會傳送至 OpenStreetMap。程式只要求可見圖磚，並使用一般 HTTP 快取，沒有地圖下載或預先擷取功能。離線時仍可使用隨附的粗略世界輪廓及手動座標輸入；請縮小地圖以清楚查看輪廓。專案沒有附帶離線街道細節。街道地圖覆蓋約南緯 85°–北緯 85°；手動座標亦接受兩極位置作星空計算。

### AR 模式

產生星圖後按 **AR**。每次新進入 AR 都會以 **Camera off** 開始：恆星、名稱及星座連線會在深色背景上隨手機移動，不會存取相機。把手機背面朝向天空。開啟 **Camera** 開關後，才會顯示即時相機背景，並在需要時要求權限。再次關閉開關可停止相機，同時繼續探索天空。旋轉螢幕會保留目前開關狀態。

AR 只有兩個主要控制項：**Camera on/off** 開關及 **•••** 選項選單。進入 AR 時會短暫顯示指向說明，沒有常駐說明橫幅。點選恆星標記可在底部查看詳細資料。打開 **•••** 可設定紅色夜間模式、星座連線、恆星名稱、恆星密度、重設對準，以及查看關於／開源授權。相同選單亦包含 **Live now**、**Calibrate**、**AR details** 及 **Back to map**。時間、位置、相機狀態及指南針讀數只放在 **AR details**，避免遮擋天空。Android 返回鍵亦會回到一般觸控星圖。

AR 使用星圖已選擇的位置及時間。夜間模式、密度、名稱及星座連線設定由兩個模式共用：在 AR 所作的修改會在旋轉後保留，返回一般星圖時亦會套用。如要對準實際天空，請選擇目前位置及 **••• → Live now**。在 **AR details** 中，歷史或未來時間會標示為 **TIME PREVIEW**。

指南針會按觀測位置修正至真北。**••• → Calibrate** 會說明八字校準動作，並提供 ±30° 方位微調。**Reset alignment** 會清除微調及所選恆星的醒目標示，天空仍會隨手機移動。這是相機加指南針的近似對準疊加畫面，不會辨認相機畫面內的恆星，也不使用 ARCore 追蹤。磁場干擾、感應器精度、相機視野估算及鏡頭變形均可能影響對準。實際戶外準確度仍需在實體手機上檢查。

相機畫面只用於即時預覽，不會拍照、錄影或上傳。方向追蹤需要方向感應器，或加速度計加指南針。後置相機屬可選：拒絕相機權限或沒有相機硬件時，仍可使用指南針星空視圖。AR 離開前景時會停止相機及感應器監聽。在取得相機 metadata 前，無相機視圖會沿螢幕長邊採用 60 度視野。

## 建置

使用 Android Studio 開啟此目錄。需要 JDK 17 或 21、Android SDK platform 36，以及本機 Python 3.13 解譯器。本儲存庫固定使用 Gradle 9.1.0、Android Gradle Plugin 9.0.1 及 Chaquopy 17.0.0。

讓 Android Studio 設定 SDK 路徑，或設定 `ANDROID_HOME`。在 Git 忽略的 `local.properties` 檔案指定 Python 執行檔：

```properties
openstar.buildPython=C\:/path/to/Python313/python.exe
```

請使用正斜線、跳脫 Windows 磁碟機代號的冒號，並省略引號。亦可將 `OPENSTAR_BUILD_PYTHON` 設為執行檔路徑，它會優先於 `local.properties`。建置用解譯器必須是 Python **3.13**，以符合 Android 內嵌的執行環境。桌面版 OpenStar 使用獨立的 Python 3.10–3.12 相依要求，請勿把桌面版 NumPy wheel 裝進 Android 建置環境。

Windows：

```powershell
.\gradlew.bat assembleDebug lintDebug
```

macOS／Linux：

```sh
sh gradlew assembleDebug lintDebug
```

首次建置需要互聯網下載建置相依套件。APK 輸出至 `app/build/outputs/apk/debug/app-debug.apk`，使用 debug 簽署供本機測試。支援 Android 8 或以上 ARM64 裝置及 x86-64 模擬器。`vendor/wheels/` 內的自訂 NumPy wheel 是相容 16 KB 記憶體分頁所必需；來源及重製步驟見 [vendor/README.md](vendor/README.md)。

## 座標及範圍

- 使用 Skyfield 計算恆星視方向：`(earth + wgs84.latlon(...)).at(t).observe(stars).apparent().altaz()`。
- HYG v4.1 提供 8,870 筆有 HIP 識別碼、星等 ≤6.5 的星表資料。隨附子集包含 J2000 赤經／赤緯，不包含自行、視差或徑向速度。
- Skyfield 提供歲差／章動及視方向修正；大氣折射已停用。這些位置用作星表展示，並非精密望遠鏡指向方案。
- 支援日期為 **2000–2050 年**，位於隨附 DE421 的範圍內。內置時間尺度資料避免額外下載；未來閏秒及地球自轉預測的時效取決於隨附 Skyfield 資料表。
- Python SVG 輸出的圓周代表幾何地平線，中心為天頂，北方朝上、東方在左。Android 一般模式及 AR 則使用透視星空視圖。兩者均不模擬地形、日光、雲層或光害。
- 初始 ISO 時間轉換成確切時刻後，Android 日期會使用觀測地點的時區。Android 假設觀測高度為零；Python API 支援高度輸入。
- 此版本顯示恆星及八組原創星座／星群連線指引，包括相機加指南針 AR 疊加畫面，不包含行星或月球。
- 應用程式介面為英文。紅色模式會改變主星圖及控制項，Android 對話框仍使用系統主題。搜尋功能為個別星圖點提供可存取的文字替代方式。

## 驗證

這次原始碼拆分的建置檢查及既有裝置驗證見 [VALIDATION.md](VALIDATION.md)。請在共用引擎的儲存庫目錄，以桌面版相依套件執行引擎測試：

```sh
cd engine
python -m pip install -r requirements.txt
python -m unittest discover -s tests -v
```

Java 幾何及日曆測試位於 `tests/java/`；離線地理時區測試為 `tests/TimeZoneBoundaryLookupTest.java`。建置後可檢查原生對齊：

```sh
python tools/check_native_alignment.py app/build/outputs/apk/debug/app-debug.apk
```

## 授權

原創 Android 程式碼、圖示及星座連線指引採用 **MIT**，詳見 [LICENSE](LICENSE)。共用 Python 引擎採用 MIT，其資料另有授權。HYG 星表採用 **CC BY-SA 4.0**；改編的時區資料庫採用 **ODbL 1.0**。重新散佈資料時，請保留來源標示並遵守授權條款。完整來源見 [NOTICE.txt](NOTICE.txt)、[licenses/](licenses/) 及應用程式內的關於選單。時區資料庫來源及重建步驟見 [vendor/timezones/README.md](vendor/timezones/README.md)。
