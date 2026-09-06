# Google Play 資料安全性問卷填寫指南 (Data Safety Declaration Guide)

於 Google Play Console 上架發布時，必須填寫「資料安全性 (Data Safety)」問卷。請依據本專案之真實資安架構填寫：

---

## 1. 資料收集與分享概觀 (Data Collection & Sharing)

- **您的應用程式是否會收集或分享任何必填的使用者資料類型？**
  - 選項：**是** (Yes)（因使用學號與密碼進行校務系統身分驗證，且包含 Play Billing 交易資訊）。
- **應用程式收集的所有使用者資料是否都在傳輸過程中加密？**
  - 選項：**是** (Yes)（所有網路傳輸均透過 HTTPS TLS 1.3 協議加密，本地端透過 Android KeyStore AES-256-GCM 加密）。
- **使用者是否可以要求刪除其資料？**
  - 選項：**是** (Yes)（使用者可隨時點擊「登出」或解除安裝應用程式，本機資料即刻全數銷毀，且無外部伺服器留存）。

---

## 2. 具體資料項目宣告 (Data Types Breakdown)

### A. 個人資訊 (Personal Info)
- **使用者 ID / 學號 (User IDs)**：
  - **是否收集？**：是 (Collected)
  - **是否分享？**：**否** (Not shared)
  - **處理方式**：短暫處理與本地儲存 (Ephemeral & Stored locally)
  - **收集目的**：應用程式功能 (App functionality) / 帳戶管理 (Account management)
  - **使用者可否選擇不提供？**：否 (登入網路學園以同步個人課表與作業所必需)。

### B. 財務資訊 (Financial Info)
- **使用者購買記錄 (Purchase History)**：
  - **是否收集？**：是 (由 Google Play Billing 自動處理，App 僅讀取 active purchaseTokens 以確認月度或永久贊助徽章)
  - **是否分享？**：**否** (Not shared)
  - **收集目的**：應用程式功能 (App functionality) / 防止詐欺與退款濫用 (Fraud prevention)。

### C. 驗證憑證 (Credentials)
- **密碼 (Passwords)**：
  - **是否收集？**：是 (僅暫存於本地 Android KeyStore EncryptedSharedPreferences，絕不上傳第三方伺服器)
  - **是否分享？**：**否** (Not shared)
  - **收集目的**：帳戶管理 (Account management)。

---

## 3. 追蹤與廣告聲明 (Tracking & Ads)

- **您的應用程式是否使用廣告 ID？**
  - 選項：**否** (No)。本應用程式無廣告，不宣告 `com.google.android.gms.permission.AD_ID` 權限。
- **您的應用程式是否用於追蹤使用者？**
  - 選項：**否** (No)。無任何跨應用程式或跨網站追蹤行為。
