package com.example.staffapp.ui.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.staffapp.SupportTicketItem
import com.example.staffapp.ui.components.StaffErrorState
import com.example.staffapp.ui.components.StaffListCard
import com.example.staffapp.ui.components.StaffLoadingState
import com.example.staffapp.ui.components.StaffPrimaryButton
import com.example.staffapp.ui.components.StaffSectionTitle
import com.example.staffapp.ui.theme.StaffOnSurfaceVariant
import com.example.staffapp.ui.work.ListCardUi

data class FeedbackScreenState(
    val subject: String = "",
    val message: String = "",
    val category: String = "question",
    val tickets: List<SupportTicketItem> = emptyList(),
    val loading: Boolean = true,
    val sending: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

private val categories = listOf(
    "question" to "Вопрос",
    "suggestion" to "Предложение",
    "technical" to "Техника",
    "billing" to "Оплата",
    "complaint" to "Жалоба",
    "other" to "Другое",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffFeedbackScreen(
    state: FeedbackScreenState,
    onBack: () -> Unit,
    onSubjectChange: (String) -> Unit,
    onMessageChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onSend: () -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Обратная связь") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StaffSectionTitle("Написать в клуб") }
            item {
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    categories.take(3).forEach { (key, label) ->
                        FilterChip(
                            selected = state.category == key,
                            onClick = { onCategoryChange(key) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            item {
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    categories.drop(3).forEach { (key, label) ->
                        FilterChip(
                            selected = state.category == key,
                            onClick = { onCategoryChange(key) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = state.subject,
                    onValueChange = onSubjectChange,
                    label = { Text("Тема") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = state.message,
                    onValueChange = onMessageChange,
                    label = { Text("Сообщение") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )
            }
            item {
                StaffPrimaryButton(
                    text = if (state.sending) "Отправляем..." else "Отправить",
                    onClick = onSend,
                    enabled = !state.sending && state.subject.isNotBlank() && state.message.length >= 5,
                )
            }
            state.statusMessage?.let {
                item { Text(it, color = StaffOnSurfaceVariant) }
            }
            state.errorMessage?.let { item { StaffErrorState(message = it, onRetry = onRefresh) } }
            item { StaffSectionTitle("Мои обращения") }
            if (state.loading) {
                item { StaffLoadingState() }
            } else if (state.tickets.isEmpty()) {
                item { Text("Пока нет обращений.", color = StaffOnSurfaceVariant) }
            } else {
                items(state.tickets, key = { it.id }) { ticket ->
                    StaffListCard(
                        item = ListCardUi(
                            title = ticket.subject,
                            subtitle = ticket.status,
                            meta = ticket.createdAt,
                        ),
                    )
                }
            }
        }
    }
}
