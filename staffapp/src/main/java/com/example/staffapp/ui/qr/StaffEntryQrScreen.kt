package com.example.staffapp.ui.qr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.staffapp.RentalClubOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffEntryQrScreen(
    staffUserId: Int,
    rentalActive: Boolean,
    blockedMessage: String?,
    onBack: () -> Unit,
    entryQrFormat: String? = null,
    hallLabel: String? = null,
    paidRentalClubs: List<RentalClubOption> = emptyList(),
    activeClubId: Int? = null,
    onSelectClub: ((Int) -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Проход в зал") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            StaffEntryQrCard(
                staffUserId = staffUserId,
                rentalActive = rentalActive,
                blockedMessage = blockedMessage,
                entryQrFormat = entryQrFormat,
                hallLabel = hallLabel,
                paidRentalClubs = paidRentalClubs,
                activeClubId = activeClubId,
                onSelectClub = onSelectClub,
            )
        }
    }
}
