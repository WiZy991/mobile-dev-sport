package com.example.staffapp.ui.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.staffapp.ui.components.StaffListCard
import com.example.staffapp.ui.components.StaffPrimaryButton

data class AssignClientDialogUi(
    val trainingId: String,
    val sessionTitle: String,
    val query: String = "",
    val clients: List<ListCardUi> = emptyList(),
    val booked: List<ListCardUi> = emptyList(),
    val loading: Boolean = false,
    val errorMessage: String? = null,
)

@Composable
fun AssignClientDialog(
    state: AssignClientDialogUi,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBookClient: (Int) -> Unit,
    onCancelBooking: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Запись: ${state.sessionTitle}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.booked.isNotEmpty()) {
                    Text("Уже записаны")
                    state.booked.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(row.title, modifier = Modifier.weight(1f))
                            if (row.meta.isNotBlank()) {
                                TextButton(onClick = { onCancelBooking(row.meta) }) {
                                    Text("Снять")
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = { Text("Поиск клиента") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                StaffPrimaryButton(text = "Найти", onClick = onSearch, enabled = !state.loading)
                state.errorMessage?.let { Text(it) }
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(state.clients, key = { it.clientId ?: it.title }) { client ->
                        StaffListCard(
                            item = client,
                            onClick = { client.clientId?.let(onBookClient) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}
