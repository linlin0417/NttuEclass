# NttuEclass 隱私權政策 (Privacy Policy)

**生效日期 (Effective Date)**：2026 年 9 月 6 日  
**最後更新 (Last Updated)**：2026 年 9 月 6 日  

歡迎使用 **NttuEclass 臺東大學網路學園**（以下簡稱「本應用程式」）。本應用程式為國立臺東大學學生社群自主開發之非官方校園行動助理工具。我們極為重視您的個人隱私與資訊安全。為協助您瞭解本應用程式如何收集、處理、使用及保護您的個人資料，請詳細閱讀本隱私權政策（以下簡稱「本政策」）。

---

## 1. 核心隱私原則 (Core Privacy Principles)

本應用程式嚴格恪守**「資料最小化 (Data Minimization)」**與**「完全本地化 (Local-First & Client-Side Only)」**原則：
- **無中繼代理伺服器**：所有校務系統通訊皆由您的行動裝置直接連線至國立臺東大學官方網路學園（https://elearn.nttu.edu.tw），不經由任何第三方伺服器或自建後端代理轉發。
- **無追蹤與無廣告**：本應用程式完全不包含任何第三方廣告追蹤器、分析代碼或使用者行為分析工具（無 Google AdMob、Firebase Analytics、Facebook SDK 等）。
- **硬體級加密保護**：帳號認證憑證一律採用 Android KeyStore 支援之 AES-256-GCM 硬體隔離保護。

---

## 2. 我們收集的資料項目與使用目的 (Data Collected & Purpose of Use)

本應用程式僅收集及處理實現核心校園功能所必需之最少資料：

### A. 個人身分認證資訊 (Personal Credentials)
- **資料項目**：臺東大學學號、校務系統登入密碼。
- **收集與處理方式**：
  - 當您登入時，本應用程式透過安全加密連線（HTTPS TLS 1.3）直接向臺東大學網路學園官方伺服器發起身分驗證。
  - 登入成功後，帳號與密碼加密保存於本機之 `EncryptedSharedPreferences`（透過 Android KeyStore 產生之金鑰與 AES-256-GCM 演算法加密）。
- **使用目的**：維持登入狀態、自動同步個人課表、公告與作業進度。
- **是否對外分享**：**絕對不分享**。除校方官方系統驗證所需外，絕不上傳至任何外部伺服器。

### B. 學術與課程資料 (Academic & Course Data)
- **資料項目**：本學期修課清單、每週課表、教室位置、授課教師名稱、課程公告內容、作業清單與繳交截止時間、測驗時限。
- **收集與處理方式**：自校方網路學園解析後，僅暫存（快取）於本機之 Room SQLite 資料庫。
- **使用目的**：提供離線課表查閱、課前 15 分鐘鬧鐘提醒、作業急迫度截止倒數。
- **是否對外分享**：**否**。僅存在於本機端供您個人查閱。

### C. 數位通行證資訊 (NttuEPass Digital Pass)
- **資料項目**：學號條碼 (Code 128) 及加密動態二維碼 (NttuDataPass ECC-256)。
- **收集與處理方式**：於本機裝置即時演算法動態運算產生。
- **使用目的**：方便使用者於校園圖書館借書或門禁設備感應。
- **是否對外分享**：**否**。

### D. 內購交易與贊助記錄 (In-App Purchases & Sponsorships)
- **資料項目**：Google Play 購買憑證（Purchase Tokens）。
- **收集與處理方式**：本應用程式透過官方 Google Play Billing 程式庫處理自由贊助。本應用程式不接觸、不收集亦不儲存您的信用卡號、銀行帳號或真實姓名等財務敏感資料。
- **使用目的**：驗證贊助狀態以解鎖應用程式徽章與自訂主題。

---

## 3. 系統權限使用說明 (Device Permissions)

本應用程式僅在必要時宣告並請求以下 Android 系統權限：

| 權限代碼 | 權限類別 | 具體使用目的說明 |
| :--- | :--- | :--- |
| `android.permission.INTERNET` | 一般權限 | 與臺東大學官方網路學園伺服器進行 HTTPS 資料同步，以及透過 Google Play Billing 進行贊助授權檢驗。 |
| `android.permission.ACCESS_NETWORK_STATE` | 一般權限 | 檢查網路連線可用性，適時切換至離線快取瀏覽模式。 |
| `android.permission.POST_NOTIFICATIONS` | 執行時期權限 (Android 13+) | 發送上課前 15 分鐘提醒、作業截止倒數提醒。使用者可隨時於系統設定中關閉。 |
| `android.permission.RECEIVE_BOOT_COMPLETED` | 一般權限 | 於手機重新開機後，重新向系統 AlarmManager 註冊已設定之課前提醒鬧鐘。 |
| `android.permission.CAMERA` | 執行時期權限 | 僅於開啟隱藏之核銷驗證台 (Hidden Verifier) 時，用於鏡頭即時掃描辨識條碼。本功能不拍攝、不儲存亦不上傳任何相片或視訊串流。 |

---

## 4. 第三方 SDK 與外部服務宣告 (Third-Party SDKs & Services)

本應用程式僅整合經 Google 認證之必要官方函式庫，絕無第三方廣告追蹤：

1. **Google Play Billing Library**：提供安全的應用程式內贊助與交易驗證。
2. **Google Play Integrity API**：驗證應用程式安裝完整性與二進位簽名，防範遭第三方植入惡意程式碼之非官方重打包版本。
3. **第三方廣告與分析**：本應用程式**完全無使用**任何第三方廣告網路（如 AdMob、Unity Ads）及使用者分析工具（如 Firebase Analytics、Adjust、AppsFlyer）。

---

## 5. 資料儲存安全與防護機制 (Data Security)

1. **傳輸安全**：全數網路傳輸強制採用 TLS 1.3 / HTTPS 加密協定，防止中間人攻擊 (MITM)。
2. **本機儲存安全**：敏感帳號憑證儲存於專屬沙盒目錄，並由 Google Android KeyStore 硬體根信任金鑰進行 AES-256-GCM 加密保護。
3. **密碼防鎖定保護**：內建智慧安全機制，主動預防因密碼輸入錯誤達到校方 5 次上限而導致帳號被鎖定 30 分鐘。

---

## 6. 使用者資料控制與刪除權利 (Data Retention & Deletion Rights)

依據中華民國《個人資料保護法》及 Google Play 使用者資料保護政策，您對個人資料享有完整之控制與刪除權利：

- **隨時登出抹除**：您可隨時於應用程式內點擊「個人頭像 $\rightarrow$ 登出帳號」，系統將立即於本機抹除所有學號、加密密碼、Session Cookie 與暫存快取。
- **清除快取**：您可隨時於 Android「設定 $\rightarrow$ 應用程式 $\rightarrow$ NttuEclass $\rightarrow$ 儲存空間」中點選「清除資料」，立即徹底銷毀所有本機資料庫。
- **解除安裝**：解除安裝本應用程式後，Android 系統將自動完整抹除本應用程式存放於裝置內的所有加密資料庫、金鑰容器與設定檔。

---

## 7. 兒童與未成年人隱私 (Children's Privacy)

本應用程式之服務對象為大專院校之學生與教職員工，其設計用途並非針對未滿 13 歲（或您所在司法管轄區規定之最低年齡）之兒童。我們不會刻意收集任何未成年人之個人識別資訊。

---

## 8. 非官方獨立開發聲明 (Disclaimer)

本應用程式為國立臺東大學學生社群自主開發之開源軟體與行動輔助工具，**非國立臺東大學校方官方發行之軟體**。「國立臺東大學」及校徽相關商標權益屬國立臺東大學所有。本應用程式僅作為校務系統之行動端介面呈現與日常課業提醒工具。

---

## 9. 隱私權政策之修訂 (Policy Changes)

隨著 Android 系統版本演進或應用程式功能擴展，我們可能會適時修訂本隱私權政策。最新修訂版本將隨時更新於本專案之公開頁面中。若有重大變更，將於應用程式更新說明中明確公告。

---

## 10. 聯絡我們 (Contact Us)

若您對本隱私權政策、資料處理方式有任何疑問、建議，或欲回報資安問題，歡迎透過以下管道與開發團隊聯繫：

- **開發團隊聯絡信箱**：support@irika.edu.tw
- **主要開發者 GitHub**：[@linin017](https://github.com/linin017)
- **GitHub 專案倉庫**：https://github.com/linin017/NttuEclass