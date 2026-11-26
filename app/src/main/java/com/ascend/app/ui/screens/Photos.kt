package com.ascend.app.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ascend.app.data.local.PhotoEntity
import com.ascend.app.domain.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Progress photos live only in app-private storage (filesDir/photos) and are never shared or uploaded. */
object PhotoStore {
    fun dir(context: Context) = File(context.filesDir, "photos").apply { mkdirs() }

    suspend fun importFrom(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val name = "${UUID.randomUUID()}.jpg"
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input -> File(dir(context), name).outputStream().use { input.copyTo(it) } }
            name
        }.getOrNull()
    }

    fun delete(context: Context, fileName: String) { File(dir(context), fileName).delete() }

    suspend fun thumbnail(context: Context, fileName: String, target: Int = 360): ImageBitmap? = withContext(Dispatchers.IO) {
        val f = File(dir(context), fileName)
        if (!f.exists()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoStrip(photos: List<PhotoEntity>, onAdd: (String) -> Unit, onDelete: (PhotoEntity) -> Unit) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Uri?>(null) }
    var confirm by remember { mutableStateOf<PhotoEntity?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { pending = it }
    LaunchedEffect(pending) {
        pending?.let { uri -> PhotoStore.importFrom(context, uri)?.let(onAdd); pending = null }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedCard(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.size(width = 96.dp, height = 128.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AddAPhoto, "Add private photo") }
            }
        }
        items(photos, key = { it.id }) { p ->
            var bmp by remember(p.fileName) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(p.fileName) { bmp = PhotoStore.thumbnail(context, p.fileName) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(width = 96.dp, height = 128.dp).clip(RoundedCornerShape(16.dp))
                        .combinedClickable(onClick = {}, onLongClick = { confirm = p }, onLongClickLabel = "Delete photo"),
                ) {
                    bmp?.let { Image(it, "Progress photo ${Dates.of(p.date).format(Dates.shortFmt)}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        ?: Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {}
                }
                Text(Dates.of(p.date).format(Dates.shortFmt), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    confirm?.let { p ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Delete this photo?") },
            text = { Text("It is removed from this device permanently.") },
            confirmButton = { TextButton(onClick = { PhotoStore.delete(context, p.fileName); onDelete(p); confirm = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}
