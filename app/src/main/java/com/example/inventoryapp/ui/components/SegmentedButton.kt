package com.example.inventoryapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SegmentedButton(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Row(
        Modifier
            .background(
                color = Color(0xFFF0F0F0),
                shape = CircleShape
            )
            .padding(6.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
            val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.primary
            TextButton(
                onClick = { onSelected(option) },
                shape = CircleShape,
                colors = ButtonDefaults.textButtonColors(
                    containerColor = bgColor,
                    contentColor = textColor
                ),
                modifier = Modifier
                    .padding(horizontal = 4.dp)
            ) {
                Text(option)
            }
        }
    }
}