package com.example.inventoryapp.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.inventoryapp.model.InventoryItem
import com.example.inventoryapp.model.UserRole
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InventoryCard(
    item: InventoryItem,
    userRole: UserRole,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddTransaction: () -> Unit,
    onViewHistory: () -> Unit,
    onArchive: () -> Unit = {},
    onSelectionChange: ((Boolean) -> Unit)? = null,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }

    val formattedDate = remember(item.date) {
        if (item.date > 0L) {
            try {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(item.date))
            } catch (e: Exception) {
                item.date.toString()
            }
        } else "-"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize()
            .combinedClickable(
                onClick = {
                    expanded = !expanded
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onClick()
                },
                onLongClick = {
                    showContextMenu = true
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                },
                role = Role.Button
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Display item name
                Text(
                    text = item.name ?: "Unnamed Item",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (userRole == UserRole.ADMIN) {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Archive") },
                            onClick = {
                                showMenu = false
                                onArchive()
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(text = "Serial: ${item.serial}")
            Text(text = "Model: ${item.model ?: "-"}")
            Text(text = "Date: $formattedDate")
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(text = item.description ?: "No description")
                Row {
                    Button(onClick = onAddTransaction) {
                        Text("Add Transaction")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onViewHistory) {
                        Text("History")
                    }
                }
            }
        }
    }
}