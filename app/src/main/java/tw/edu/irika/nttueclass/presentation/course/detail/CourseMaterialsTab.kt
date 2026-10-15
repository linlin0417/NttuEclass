package tw.edu.irika.nttueclass.presentation.course.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tw.edu.irika.nttueclass.domain.model.CourseMaterial
import tw.edu.irika.nttueclass.domain.model.MaterialDownloadStatus
import tw.edu.irika.nttueclass.domain.model.MaterialType

/**
 * 檔案類型識別圖示與色彩徽章
 */
@Composable
fun FileTypeBadge(
    type: MaterialType,
    extension: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, iconColor, icon) = when (type) {
        MaterialType.PDF -> Triple(Color(0xFFFFEBEE), Color(0xFFD32F2F), Icons.Default.PictureAsPdf)
        MaterialType.SLIDES -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), Icons.Default.Slideshow)
        MaterialType.DOCUMENT -> Triple(Color(0xFFE3F2FD), Color(0xFF1976D2), Icons.Default.Description)
        MaterialType.SPREADSHEET -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Icons.Default.TableChart)
        MaterialType.ARCHIVE -> Triple(Color(0xFFF3E5F5), Color(0xFF7B1FA2), Icons.Default.FolderZip)
        MaterialType.VIDEO -> Triple(Color(0xFFE0F2F1), Color(0xFF00796B), Icons.Default.VideoFile)
        MaterialType.AUDIO -> Triple(Color(0xFFEDE7F6), Color(0xFF512DA8), Icons.Default.AudioFile)
        MaterialType.IMAGE -> Triple(Color(0xFFFBE9E7), Color(0xFFD84315), Icons.Default.Image)
        MaterialType.LINK -> Triple(Color(0xFFE0F7FA), Color(0xFF00838F), Icons.Default.Link)
        MaterialType.OTHER -> Triple(Color(0xFFF5F5F5), Color(0xFF616161), Icons.Default.Description)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 章節標題
 */
@Composable
fun ChapterSectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "$count 項資源",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 個別教材卡片 (色彩識別、進度條、一鍵開啟)
 */
@Composable
fun MaterialItemCard(
    material: CourseMaterial,
    downloadProgress: Float?,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onDeleteCustom: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val isDownloaded = material.isFilePresentOnDisk
    val isDownloading = downloadProgress != null || material.downloadStatus == MaterialDownloadStatus.DOWNLOADING

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 依檔案類型呈現色彩圖示
                FileTypeBadge(type = material.type, extension = material.fileExtension)

                Spacer(modifier = Modifier.width(12.dp))

                // 教材資訊
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = material.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (material.formattedSize.isNotBlank()) {
                            Text(
                                text = material.formattedSize,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (material.uploadDate.isNotBlank()) {
                            Text(
                                text = "·  ${material.uploadDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isDownloaded) {
                            Surface(
                                color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "已下載",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                // 更多動作選單 (重新下載 / 刪除)
                if (isDownloaded || material.id.startsWith("custom_")) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (isDownloaded) {
                                DropdownMenuItem(
                                    text = { Text("重新下載") },
                                    onClick = {
                                        showMenu = false
                                        onDownload()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("刪除本機檔案", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        onDelete()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                            if (material.id.startsWith("custom_")) {
                                DropdownMenuItem(
                                    text = { Text("刪除此自訂項目", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        onDeleteCustom?.invoke() ?: onDelete()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }
                }
            }

            // 下載中即時進度條
            if (isDownloading) {
                Spacer(modifier = Modifier.height(10.dp))
                val progress = downloadProgress ?: 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("正在下載...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isDownloaded) {
                        Button(
                            onClick = onOpen,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("開啟檔案", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onDownload,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (material.formattedSize.isNotBlank()) "下載 (${material.formattedSize})" else "下載教材",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 核心教材專區清單內容 (依章節分組 + 搜尋 + 類型篩選)
 */
@Composable
fun CourseMaterialsTabContent(
    filteredMaterials: List<CourseMaterial>,
    hasAnyMaterials: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedTypeFilter: MaterialType?,
    onTypeFilterSelect: (MaterialType?) -> Unit,
    showOnlyDownloaded: Boolean,
    onToggleOnlyDownloaded: () -> Unit,
    downloadProgressMap: Map<String, Float>,
    onDownloadMaterial: (CourseMaterial) -> Unit,
    onOpenMaterial: (CourseMaterial) -> Unit,
    onDeleteLocalFile: (CourseMaterial) -> Unit,
    onDeleteCustomMaterial: ((CourseMaterial) -> Unit)? = null,
    onSync: () -> Unit,
    onOpenLogin: () -> Unit,
    isLoggedIn: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // 搜尋欄位
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("搜尋教材標題、章節或副檔名") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "清除搜尋")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        // 類型篩選 Chip (橫向捲動)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedTypeFilter == null && !showOnlyDownloaded,
                    onClick = {
                        onTypeFilterSelect(null)
                        if (showOnlyDownloaded) onToggleOnlyDownloaded()
                    },
                    label = { Text("全部") }
                )
            }
            item {
                FilterChip(
                    selected = showOnlyDownloaded,
                    onClick = onToggleOnlyDownloaded,
                    leadingIcon = {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("已下載") }
                )
            }
            items(listOf(MaterialType.PDF, MaterialType.SLIDES, MaterialType.DOCUMENT, MaterialType.ARCHIVE, MaterialType.VIDEO)) { type ->
                FilterChip(
                    selected = selectedTypeFilter == type,
                    onClick = {
                        onTypeFilterSelect(if (selectedTypeFilter == type) null else type)
                    },
                    label = { Text(type.displayName) }
                )
            }
        }

        if (filteredMaterials.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (hasAnyMaterials) "查無符合條件之教材" else "目前尚無教材檔案",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (hasAnyMaterials) "請嘗試更換關鍵字或取消類型篩選。"
                        else if (isLoggedIn) "學校 eClass 伺服器可能尚未上傳本門課教材，或可點擊下方同步刷新。"
                        else "請先登入學號以載入該課程的最新教材與講義檔案。",
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
                            Text("立即從 eClass 同步教材", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onOpenLogin,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("登入帳號載入教材", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // 依章節分組
            val grouped = remember(filteredMaterials) {
                filteredMaterials.groupBy { it.chapterName.ifBlank { "一般講義與課程資源" } }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                grouped.forEach { (chapterTitle, chapterMaterials) ->
                    item {
                        ChapterSectionHeader(
                            title = chapterTitle,
                            count = chapterMaterials.size
                        )
                    }
                    items(chapterMaterials, key = { it.id }) { material ->
                        MaterialItemCard(
                            material = material,
                            downloadProgress = downloadProgressMap[material.id],
                            onDownload = { onDownloadMaterial(material) },
                            onOpen = { onOpenMaterial(material) },
                            onDelete = { onDeleteLocalFile(material) },
                            onDeleteCustom = { onDeleteCustomMaterial?.invoke(material) }
                        )
                    }
                }
            }
        }
    }
}
