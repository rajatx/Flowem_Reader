package com.example.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun GoToPageDialog(
    currentPage: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onPageSelected: (Int) -> Unit
) {
    val total = totalPages.coerceAtLeast(1)
    var textInput by remember(currentPage) { mutableStateOf((currentPage + 1).toString()) }
    var sliderVal by remember(currentPage) { mutableFloatStateOf((currentPage + 1).toFloat()) }

    val pageNumber = textInput.toIntOrNull()
    val isPageValid = pageNumber != null && pageNumber in 1..total

    fun commitAndClose(targetOneBased: Int) {
        val validPage = targetOneBased.coerceIn(1, total)
        onPageSelected(validPage - 1)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("go_to_page_dialog"),
        title = {
            Text(
                text = "Go to Page",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Document has $total pages. Enter a page number to navigate immediately.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { newVal ->
                        // Allow only digits
                        val digits = newVal.filter { it.isDigit() }
                        textInput = digits
                        digits.toIntOrNull()?.let {
                            if (it in 1..total) {
                                sliderVal = it.toFloat()
                            }
                        }
                    },
                    label = { Text("Page (1 - $total)") },
                    placeholder = { Text("e.g. ${(currentPage + 1)}") },
                    isError = textInput.isNotEmpty() && !isPageValid,
                    supportingText = {
                        if (textInput.isNotEmpty() && !isPageValid) {
                            Text("Please enter a page between 1 and $total")
                        } else {
                            val percent = if (total > 0 && isPageValid) {
                                ((pageNumber!!.toFloat() / total) * 100).toInt()
                            } else {
                                ((((currentPage + 1).toFloat() / total) * 100).toInt())
                            }
                            Text("Current position: $percent%")
                        }
                    },
                    trailingIcon = {
                        if (textInput.isNotEmpty()) {
                            IconButton(onClick = { textInput = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            if (isPageValid) {
                                commitAndClose(pageNumber!!)
                            }
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("go_to_page_input")
                )

                // Slider for fine scrubbing
                if (total > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Page ${sliderVal.toInt()} of $total",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${((sliderVal / total) * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = sliderVal,
                            onValueChange = {
                                sliderVal = it
                                textInput = it.toInt().toString()
                            },
                            valueRange = 1f..total.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Quick Navigation Shortcuts
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Quick Jump",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                textInput = "1"
                                sliderVal = 1f
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("First")
                        }

                        TextButton(
                            onClick = {
                                val cur = textInput.toIntOrNull() ?: (currentPage + 1)
                                val next = (cur - 10).coerceAtLeast(1)
                                textInput = next.toString()
                                sliderVal = next.toFloat()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("-10")
                        }

                        TextButton(
                            onClick = {
                                val cur = textInput.toIntOrNull() ?: (currentPage + 1)
                                val next = (cur + 10).coerceAtMost(total)
                                textInput = next.toString()
                                sliderVal = next.toFloat()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+10")
                        }

                        TextButton(
                            onClick = {
                                textInput = total.toString()
                                sliderVal = total.toFloat()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Last")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isPageValid) {
                        commitAndClose(pageNumber!!)
                    }
                },
                enabled = isPageValid,
                modifier = Modifier.testTag("go_to_page_confirm")
            ) {
                Text("Go to Page")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("go_to_page_cancel")
            ) {
                Text("Cancel")
            }
        }
    )
}
