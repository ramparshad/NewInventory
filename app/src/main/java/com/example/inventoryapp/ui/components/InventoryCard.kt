package com.example.inventoryapp.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.example.inventoryapp.model.InventoryItem
import com.example.inventoryapp.model.UserRole
import androidx.compose.foundation.clickable

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
    modifier: Modifier = Modifier,
    imageUrls: List<String>,
    onImageClick: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val formattedDate = remember(item.date) {
        if (item.date.isNotEmpty()) item.date else "-"
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
                    onSelectionChange?.let { it(!isSelected) }
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                },
                role = Role.Button
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // --- IMAGES ---
            if (imageUrls.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    imageUrls.forEachIndexed { idx, url ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(url)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Inventory image",
                            modifier = Modifier
                                .size(100.dp)
                                .padding(end = 8.dp)
                                .clickable { onImageClick(idx) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (userRole == UserRole.ADMIN || userRole == UserRole.OPERATOR) {
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
                        if (userRole == UserRole.ADMIN) {
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
            }
            Spacer(Modifier.height(8.dp))
            Text(text = "Serial: ${item.serial}")
            Text(text = "Model: ${item.model}")
            Text(text = "Date: $formattedDate")
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(text = if (item.description.isNotBlank()) item.description else "No description")
                Row {
                    if (userRole == UserRole.ADMIN || userRole == UserRole.OPERATOR) {
                        Button(onClick = onAddTransaction) {
                            Text("Add Transaction")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(onClick = onViewHistory) {
                        Text("History")
                    }
                }
            }
        }
    }
}