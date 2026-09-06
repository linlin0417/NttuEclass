# NttuEclass 隱私權政策 (Privacy Policy)

**生效日期**：2026 年 9 月 6 日  
**最後更新**：2026 年 9 月 6 日  

歡迎使用 **NttuEclass**（以下簡稱「本應用程式」）。本應用程式由國立臺東大學學生社群獨立開發維護。我們非常重視您的個人隱私與資料安全，請詳閱以下隱私權政策條款。

---

## 1. 資料收集與處理原則

本應用程式致力於落實「資料最小化」與「完全本地化」之最高資安隱私原則：

1. **學號與密碼 (帳號認證憑證)**：
   - 您於登入時輸入之臺東大學學號與密碼，**僅用於直接向校方官方網路學園系統 (https://elearn.nttu.edu.tw) 發送加密 HTTPS 認證請求**。
   - 您的密碼與認證 Token 經由 Google 原生 **Android KeyStore 與 EncryptedSharedPreferences (AES-256-GCM)** 於您的裝置端進行硬體級加密儲存。
   - **本應用程式絕不設置任何外部代理伺服器或中繼站，絕不會將您的帳號、密碼或個人資料傳輸至任何第三方開發者伺服器**。

2. **學籍課表、公告與作業資料**：
   - 應用程式解析自校方網路學園之課表、課程清單、作業繳交期限與系統公告，僅快取於您手機本機之 SQLite (Room) 加密資料庫中，供您離線查閱使用。
   - 本地資料庫資料隨時可透過「登出」功能或於系統應用程式設定中清除快取直接抹除。

3. **數位通行證 (NttuEPass)**：
   - 通行證條碼 (NttuPass 與 NttuDataPass) 所包含之資訊均於本地即時運算產生（包含學號、自訂 Token 與贊助整數），不涉及任何雲端追蹤。

---

## 2. 裝置權限使用說明

本應用程式僅在必要時請求最少限度之 Android 系統權限：

| 權限名稱 | 使用目的 |
| :--- | :--- |
| `android.permission.INTERNET` | 向臺東大學網路學園伺服器同步課表、公告與作業，以及向 Google Play 進行內購與授權查驗。 |
| `android.permission.POST_NOTIFICATIONS` | 於 Android 13+ 發送課前 15 分鐘提醒、作業截止倒數與公告提醒（使用者可隨時於系統關閉）。 |
| `android.permission.RECEIVE_BOOT_COMPLETED` | 於手機重新開機後，自動重新註冊課前提醒 AlarmManager 排程。 |
| `android.permission.CAMERA` | 僅用於管理員解鎖隱藏核銷驗證台時掃描 NttuPass 條碼或 NttuDataPass 二維碼。未開啟核銷台時絕對不會啟用相機。 |

---

## 3. 第三方服務與 SDK 聲明

本應用程式**完全無嵌入任何第三方商業廣告 SDK 或使用者行為追蹤代碼**（如 Google AdMob、Facebook SDK、Firebase Analytics 等）。

本應用程式僅整合以下 Google 官方發布必須之系統服務庫：
- **Google Play Billing Library**：用於處理使用者自由自願性質之應用程式內贊助款項。
- **Google Play Integrity API**：用於檢驗應用程式簽名完整性，防止使用者安裝遭第三方惡意植入木馬之盜版二度打包版本。

---

## 4. 資料儲存期限與刪除權利

- **即時清除**：您可隨時於應用程式內點擊「個人頭像 $\rightarrow$ 確認登出」，系統將立即徹底清除本地存儲之所有學號、加密密碼與網路連線 Session。
- **完全解除安裝**：當您自手機解除安裝本應用程式時，所有本地快取資料庫與加密設定檔將由 Android 系統全數銷毀。

---

## 5. 政策修訂與聯絡方式

本隱私權政策可能隨功能更新或法規要求不定期修訂。修訂後之條款將即時更新於本頁面。

若您對本隱私權政策有任何疑問、建議或資安問題回報，歡迎透過以下方式聯繫開發團隊：
- **開發者信箱**：support@irika.edu.tw
- **GitHub 專案倉庫**：https://github.com/irika/NttuEclass
