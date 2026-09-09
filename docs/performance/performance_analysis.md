# Android App 效能分析與卡頓診斷報告

> **專案資訊**：NttuEclass (`tw.edu.irika.nttueclass`)  
> **更新時間**：2026-09-09  
> **診斷工具**：Android Studio Task-Based Profiler (CPU / Method Recording & Flame Chart Analysis)

---

## 1. 專案概況與檢測情境

* **應用程式套件**：`tw.edu.irika.nttueclass`
* **分析場景**：作業與測驗畫面載入、HTML 解析（`TaskHtmlParser`）、列表渲染與系統執行緒追蹤

---

## 2. 卡頓主因診斷 (Root Causes Analysis)

通過 Profiler 的 Flame Chart 與 Call Tree 分析，發現以下三大效能瓶頸：

### 2.1 主執行緒（Main Thread）阻塞與過深堆疊
* **現象**：Flame Chart 中 `main` 執行緒上方出現大量縱向累積的深層呼叫方塊（`doFrame` 階段引發深層運算與迴圈處理），佔用幾乎整個 CPU 採樣時間。
* **原因**：HTML 解析（如 `TaskHtmlParser` / Jsoup DOM 樹建構與 CSS Selector 搜尋）或複雜數據轉換直接或間接影響了 UI 主執行緒的執行時間。

### 2.2 頻繁物件配置與垃圾回收 (Garbage Collection Jank)
* **現象**：解析大量 HTML 列資料（作業、測驗清單）時產生大量短生命週期物件。
* **原因**：在 `TaskHtmlParser.kt` 的 `calculateRemainingHours` 函式中：
  1. 針對每筆資料列的迴圈，重複實例化多個 `java.text.SimpleDateFormat` 物件（`patterns` 陣列迴圈）。
  2. 每次解析皆即時建立 `Regex` 物件進行正則比對。
  * 此舉會在短時間內配置大量記憶體，觸發系統頻繁 GC 造成 UI 停頓。

### 2.3 背景線程與主線程同步鎖定
* **現象**：Top Down 視窗中顯示大量 `arch_disk_io_0` ~ `arch_disk_io_3`（Room / Architecture Components 磁碟 I/O 執行緒）在高頻運作。
* **原因**：當大量資料寫入或讀取資料庫時，若未做好 Dispatcher 隔離，易導致主執行緒在讀取 StateFlow / LiveData 時等待磁碟鎖定。

---

## 3. 具體優化建議與重構行動方案 (Action Items)

### 3.1 最佳化 `TaskHtmlParser.kt` 記憶體配置

避免在迴圈內頻繁建立 `SimpleDateFormat` 與 `Regex`，改為類別層級快取：

```kotlin
object TaskHtmlParser {

    // 快取 SimpleDateFormat 與 Regex，避免在迴圈內重複實例化
    private val datePatterns = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy/MM/dd HH:mm",
        "yyyy-MM-dd",
        "yyyy/MM/dd"
    )
    private val hourRegex = Regex("""(\d+)\s*(?:個)?小時""")
    private val dayRegex = Regex("""(\d+)\s*天""")

    private fun calculateRemainingHours(dueDateTimeStr: String): Long {
        if (dueDateTimeStr.isBlank()) return 0L

        val now = System.currentTimeMillis()
        for (pattern in datePatterns) {
            try {
                val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                sdf.isLenient = false
                val date = sdf.parse(dueDateTimeStr)
                if (date != null) {
                    return (date.time - now) / (1000 * 60 * 60)
                }
            } catch (_: Exception) {}
        }

        val hourMatch = hourRegex.find(dueDateTimeStr)
        if (hourMatch != null) {
            return hourMatch.groupValues[1].toLongOrNull() ?: 0L
        }
        val dayMatch = dayRegex.find(dueDateTimeStr)
        if (dayMatch != null) {
            return (dayMatch.groupValues[1].toLongOrNull() ?: 0L) * 24L
        }

        return 0L
    }
}
```

### 3.2 確保所有解析任務強制在 `Dispatchers.IO` 上執行

在 `EclassRepository.kt` 中，確保所有呼叫 `TaskHtmlParser.parse(...)` 的方法包裹在 `withContext(Dispatchers.IO)` 內：

```kotlin
suspend fun parseTasks(html: String, type: TaskType): List<TaskItem> = withContext(Dispatchers.IO) {
    TaskHtmlParser.parse(html, defaultType = type)
}
```

### 3.3 UI 渲染優化 (Jetpack Compose / LazyColumn)

1. **為列表提供唯一 Key**：`LazyColumn` 中加入 `key = { it.id }` 避免捲動時不必要的項重新建立。
2. **派生狀態 (Derived State)**：對於捲動位置或時間計算，使用 `derivedStateOf { ... }` 減少重組頻率。
