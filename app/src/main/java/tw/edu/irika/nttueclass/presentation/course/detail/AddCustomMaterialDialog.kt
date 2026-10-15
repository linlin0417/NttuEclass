package tw.edu.irika.nttueclass.presentation.course.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tw.edu.irika.nttueclass.domain.model.CourseMaterial
import tw.edu.irika.nttueclass.domain.model.MaterialDownloadStatus
import tw.edu.irika.nttueclass.domain.model.MaterialType

/**
 * 手動新增自訂教材/筆記對話框
 */
@Composable
fun AddCustomMaterialDialog(
    courseId: String,
    onDismiss: () -> Unit,
    onSave: (CourseMaterial) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var chapterName by remember { mutableStateOf("") }
    var downloadUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增課程教材 / 筆記項目") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("教材標題 *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = chapterName,
                    onValueChange = { chapterName = it },
                    label = { Text("所屬章節 / 週次 (選填)") },
                    placeholder = { Text("例如：第 3 週講義、期中複習") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = downloadUrl,
                    onValueChange = { downloadUrl = it },
                    label = { Text("線上連結或下載網址 (選填)") },
                    placeholder = { Text("https://...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    val ext = if (title.contains(".")) title.substringAfterLast(".") else "txt"
                    onSave(
                        CourseMaterial(
                            id = "custom_${System.currentTimeMillis()}",
                            courseId = courseId,
                            title = title.trim(),
                            chapterName = chapterName.trim().ifBlank { "自訂筆記與教材" },
                            downloadUrl = downloadUrl.trim(),
                            fileExtension = ext,
                            type = CourseMaterial.inferTypeFromExtension(ext),
                            uploadDate = "今天",
                            downloadStatus = MaterialDownloadStatus.NOT_DOWNLOADED
                        )
                    )
                },
                enabled = title.isNotBlank()
            ) {
                Text("新增")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
