package com.example.staffapp.ui.legal

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.staffapp.legal.LegalPdfFiles
import com.example.staffapp.legal.StaffLegalPdf
import com.example.staffapp.ui.theme.StaffPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** PdfRenderer не потокобезопасен — один рендер на файл за раз. */
private val pdfFileLocks = ConcurrentHashMap<String, Mutex>()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalPdfScreen(
    doc: StaffLegalPdf,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    var pageCount by remember { mutableIntStateOf(0) }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(doc) {
        loading = true
        error = null
        pageCount = 0
        pdfFile = null
        withContext(Dispatchers.IO) {
            try {
                val file = LegalPdfFiles.resolve(context, doc)
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        pageCount = renderer.pageCount
                        pdfFile = file
                    }
                }
            } catch (e: Exception) {
                error = e.message?.takeIf { it.isNotBlank() } ?: "Не удалось открыть документ"
            } finally {
                loading = false
            }
        }
    }

    BackHandler(onBack = onNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(doc.title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StaffPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF2F2F2))
                .padding(padding),
        ) {
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = StaffPrimary,
                )
                error != null -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onNavigateBack) { Text("Назад") }
                }
                pdfFile != null && pageCount > 0 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items((0 until pageCount).toList(), key = { it }) { pageIndex ->
                        PdfPageImage(file = pdfFile!!, pageIndex = pageIndex)
                    }
                }
                else -> Text(
                    "Документ пуст",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF666666),
                )
            }
        }
    }
}

@Composable
private fun PdfPageImage(
    file: File,
    pageIndex: Int,
) {
    var bitmap by remember(file, pageIndex) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(file, pageIndex) { mutableStateOf(false) }

    LaunchedEffect(file, pageIndex) {
        bitmap = null
        failed = false
        val rendered = withContext(Dispatchers.IO) {
            renderPdfPage(file, pageIndex)
        }
        if (rendered != null) {
            bitmap = rendered.asImageBitmap()
        } else {
            failed = true
        }
    }

    when {
        bitmap != null -> Image(
            bitmap = bitmap!!,
            contentDescription = "Страница ${pageIndex + 1}",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
        failed -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Не удалось загрузить страницу ${pageIndex + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF666666),
            )
        }
        else -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = StaffPrimary)
        }
    }
}

private suspend fun renderPdfPage(file: File, pageIndex: Int): Bitmap? {
    val lock = pdfFileLocks.getOrPut(file.absolutePath) { Mutex() }
    return lock.withLock {
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return@withLock null
                    renderer.openPage(pageIndex).use { page ->
                        // 1.5x: читаемо и не раздувает память на длинных офертах
                        val scale = 1.5f
                        val bmp = Bitmap.createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888,
                        )
                        bmp.eraseColor(AndroidColor.WHITE)
                        page.render(
                            bmp,
                            null,
                            Matrix().apply { setScale(scale, scale) },
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                        )
                        bmp
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
