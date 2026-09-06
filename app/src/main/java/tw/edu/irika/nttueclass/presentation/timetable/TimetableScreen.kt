package tw.edu.irika.nttueclass.presentation.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.edu.irika.nttueclass.data.repository.EclassRepository
import tw.edu.irika.nttueclass.domain.model.Period
import tw.edu.irika.nttueclass.domain.model.StandardPeriods
import tw.edu.irika.nttueclass.domain.model.TimetableSlot

@Composable
fun TimetableScreen(
    repository: EclassRepository,
    isLoggedIn: Boolean,
    onOpenLogin: () -> Unit,
    onSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val currentDayOfWeek = remember {
        val day = java.time.LocalDate.now().dayOfWeek.value // 1=Mon .. 7=Sun
        day.coerceIn(1, 5)
    }
    var selectedDay by remember { mutableIntStateOf(currentDayOfWeek) }
    var isWeekView by remember { mutableStateOf(false) }
    var selectedSlotForDetail by remember { mutableStateOf<TimetableSlot?>(null) }

    // 從真實 Room 資料庫訂閱課表資料流 (無任何寫死假資料)
    val slots by repository.getTimetableStream().collectAsState(initial = emptyList())

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // 頂部切換操作欄
        TimetableControlsBar(
            selectedDay = selectedDay,
            onSelectDay = { selectedDay = it },
            isWeekView = isWeekView,
            onToggleView = { isWeekView = !isWeekView },
            onBackToToday = { selectedDay = currentDayOfWeek }
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (slots.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EventBusy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "目前尚無課表資料",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isLoggedIn) "已驗證學生身分，請點擊下方按鈕以同步臺東大學最新課表。" else "尚未登入學生帳號，請先登入以載入個人專屬課表。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    if (isLoggedIn) {
                        Button(
                            onClick = onSync,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("立即同步課表", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onOpenLogin,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("登入帳號載入課表", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            if (isWeekView) {
                WeekTableView(
                    slots = slots,
                    isDark = isDark,
                    onSlotClick = { selectedSlotForDetail = it }
                )
            } else {
                DayTimelineView(
                    dayOfWeek = selectedDay,
                    slots = slots.filter { it.dayOfWeek == selectedDay },
                    isDark = isDark,
                    onSlotClick = { selectedSlotForDetail = it }
                )
            }
        }
    }

    // 課程詳細資訊彈窗
    selectedSlotForDetail?.let { slot ->
        CourseDetailDialog(
            slot = slot,
            isDark = isDark,
            onDismiss = { selectedSlotForDetail = null }
        )
    }
}

@Composable
private fun TimetableControlsBar(
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    isWeekView: Boolean,
    onToggleView: () -> Unit,
    onBackToToday: () -> Unit
) {
    val days = listOf(1 to "週一", 2 to "週二", 3 to "週三", 4 to "週四", 5 to "週五")

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (!isWeekView) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(days) { (dayNum, label) ->
                    val isSelected = selectedDay == dayNum
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectDay(dayNum) },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        } else {
            Text(
                text = "全週總覽 (週一 ~ 週五)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackToToday) {
                Icon(
                    imageVector = Icons.Default.Today,
                    contentDescription = "回到今日",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onToggleView) {
                Icon(
                    imageVector = if (isWeekView) Icons.Default.CalendarViewWeek else Icons.Default.CalendarViewMonth,
                    contentDescription = "切換視圖",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DayTimelineView(
    dayOfWeek: Int,
    slots: List<TimetableSlot>,
    isDark: Boolean,
    onSlotClick: (TimetableSlot) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(StandardPeriods.allPeriods) { period ->
            val slot = slots.find { it.periodNumber == period.periodNumber }
            PeriodRow(
                period = period,
                slot = slot,
                isDark = isDark,
                onSlotClick = onSlotClick
            )
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun PeriodRow(
    period: Period,
    slot: TimetableSlot?,
    isDark: Boolean,
    onSlotClick: (TimetableSlot) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左側節次與時間
        Column(
            modifier = Modifier.width(68.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = period.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = period.startTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Text(
                text = period.endTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 右側課程卡片或空白
        if (slot != null) {
            val colorStyle = TimetableColors.getColorStyle(slot.courseName, isDark)
            Card(
                onClick = { onSlotClick(slot) },
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 1.dp,
                        color = colorStyle.borderColor,
                        shape = RoundedCornerShape(10.dp)
                    ),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colorStyle.backgroundColor
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左側指示條
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(5.dp)
                            .background(colorStyle.indicatorColor)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = slot.courseName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorStyle.textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = slot.classroom,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorStyle.secondaryTextColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "· ${slot.instructor}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorStyle.secondaryTextColor
                            )
                        }
                    }
                }
            }
        } else {
            // 空堂時段
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "無排課",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun WeekTableView(
    slots: List<TimetableSlot>,
    isDark: Boolean,
    onSlotClick: (TimetableSlot) -> Unit
) {
    val days = listOf(1, 2, 3, 4, 5)
    val dayLabels = listOf("一", "二", "三", "四", "五")

    Column(modifier = Modifier.fillMaxSize()) {
        // 表頭
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "節次",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.Center
            )
            dayLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(StandardPeriods.allPeriods) { period ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .border(
                            0.5.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 節次代碼
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = period.periodCode,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 5 天各格
                    days.forEach { dayNum ->
                        val slot = slots.find { it.dayOfWeek == dayNum && it.periodNumber == period.periodNumber }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(
                                    0.25.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                )
                                .padding(1.dp)
                        ) {
                            if (slot != null) {
                                val colorStyle = TimetableColors.getColorStyle(slot.courseName, isDark)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colorStyle.backgroundColor)
                                        .clickable { onSlotClick(slot) }
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = slot.courseName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorStyle.textColor,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseDetailDialog(
    slot: TimetableSlot,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val period = StandardPeriods.getByPeriodNumber(slot.periodNumber)
    val colorStyle = TimetableColors.getColorStyle(slot.courseName, isDark)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = slot.courseName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "上課教室：${slot.classroom}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "授課教師：${slot.instructor}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "節次時段：${period?.label ?: ""} (${period?.timeRange ?: ""})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "確定")
            }
        }
    )
}
