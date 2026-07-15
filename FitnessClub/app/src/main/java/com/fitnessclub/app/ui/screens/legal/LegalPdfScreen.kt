package com.fitnessclub.app.ui.screens.legal

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import com.fitnessclub.app.data.config.LegalPdfAsset
import com.fitnessclub.app.data.config.LegalPdfFiles
import com.fitnessclub.app.ui.theme.Primary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalPdfScreen(
    asset: LegalPdfAsset,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    var pageCount by remember { mutableIntStateOf(0) }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(asset) {
        isLoading = true
        error = null
        withContext(Dispatchers.IO) {
            try {
                val file = LegalPdfFiles.resolve(context, asset)
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        pageCount = renderer.pageCount
                        pdfFile = file
                    }
                }
            } catch (_: Exception) {
                error = "Не удалось открыть документ"
                pdfFile = null
                pageCount = 0
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(asset.title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(paddingValues),
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(error!!, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF1A1A1A))
                        TextButton(onClick = onNavigateBack) {
                            Text("Назад")
                        }
                    }
                }
                pdfFile != null && pageCount > 0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items((0 until pageCount).toList(), key = { it }) { pageIndex ->
                            PdfPageImage(
                                file = pdfFile!!,
                                pageIndex = pageIndex,
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        "Документ пуст",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF666666),
                    )
                }
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
        withContext(Dispatchers.IO) {
            try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        renderer.openPage(pageIndex).use { page ->
                            val scale = 2
                            val bmp = Bitmap.createBitmap(
                                page.width * scale,
                                page.height * scale,
                                Bitmap.Config.ARGB_8888,
                            )
                            bmp.eraseColor(AndroidColor.WHITE)
                            page.render(
                                bmp,
                                null,
                                Matrix().apply { setScale(scale.toFloat(), scale.toFloat()) },
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                            )
                            bitmap = bmp.asImageBitmap()
                        }
                    }
                }
            } catch (_: Exception) {
                failed = true
            }
        }
    }

    when {
        bitmap != null -> {
            Image(
                bitmap = bitmap!!,
                contentDescription = "Страница ${pageIndex + 1}",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )
        }
        failed -> {
            Box(
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
        }
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
