package com.fitnessclub.app.ui.screens.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.fitnessclub.app.BuildConfig
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.config.Brand
import com.fitnessclub.app.data.config.LegalPdfAsset
import com.fitnessclub.app.data.repository.ClubRepository
import com.fitnessclub.app.ui.components.BrandHeader
import com.fitnessclub.app.ui.theme.Primary
import com.fitnessclub.app.ui.theme.PrimaryVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val clubRepository: ClubRepository,
) : ViewModel() {
    var bannerUrl by mutableStateOf<String?>(null)
        private set
    var legalText by mutableStateOf<String?>(null)
        private set
    var supportEmail by mutableStateOf<String?>(null)
        private set
    var supportPhone by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            when (val r = clubRepository.getClubInfo()) {
                is ApiResult.Success -> {
                    bannerUrl = r.data.welcomeBannerUrl?.trim()?.takeIf { it.isNotEmpty() }
                    legalText = r.data.welcomeLegalText?.trim()?.takeIf { it.isNotEmpty() }
                    supportEmail = r.data.email.trim().takeIf { it.isNotEmpty() }
                    supportPhone = r.data.phone.trim().takeIf { it.isNotEmpty() }
                }
                else -> Unit
            }
        }
    }
}

@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
    onOpenLegalPdf: (LegalPdfAsset) -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val banner = viewModel.bannerUrl
    val customLegal = viewModel.legalText

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(Primary, PrimaryVariant, Color(0xFFB33A12))),
                ),
        )
        if (banner != null) {
            AsyncImage(
                model = banner,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to Color(0xCC1A0A00),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            BrandHeader(brandName = Brand.name, subtitle = null)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Primary,
                ),
            ) {
                Text("Продолжить", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            val legal = welcomeLegalAnnotatedString(customLegal)
            ClickableText(
                text = legal,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(0.9f),
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.fillMaxWidth(),
                onClick = { offset ->
                    legal.getStringAnnotations("PDF", offset, offset).firstOrNull()?.let { tag ->
                        LegalPdfAsset.fromAnnotation(tag.item)?.let(onOpenLegalPdf)
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    val mail = viewModel.supportEmail
                    val phone = viewModel.supportPhone
                    val intent = when {
                        !mail.isNullOrBlank() -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$mail"))
                        !phone.isNullOrBlank() -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                        else -> null
                    }
                    intent?.let { context.startActivity(it) }
                },
            ) {
                Text("Обратиться в поддержку", color = Color.White.copy(0.92f))
            }
            Text(
                text = "Версия ${BuildConfig.VERSION_NAME}",
                color = Color.White.copy(0.7f),
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun welcomeLegalAnnotatedString(custom: String?) = buildAnnotatedString {
    append(
        custom?.takeIf { it.isNotBlank() }
            ?: "Нажимая «Продолжить», вы соглашаетесь с ",
    )
    if (custom.isNullOrBlank()) {
        pushStringAnnotation("PDF", LegalPdfAsset.USER_AGREEMENT.name)
        withStyle(SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold)) {
            append("политиками и документами")
        }
        pop()
        append(" (пользовательское соглашение и политика конфиденциальности).")
    } else {
        append(" ")
        pushStringAnnotation("PDF", LegalPdfAsset.USER_AGREEMENT.name)
        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
            append("Пользовательское соглашение")
        }
        pop()
        append(" · ")
        pushStringAnnotation("PDF", LegalPdfAsset.PRIVACY_POLICY.name)
        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
            append("Политика конфиденциальности")
        }
        pop()
    }
}
