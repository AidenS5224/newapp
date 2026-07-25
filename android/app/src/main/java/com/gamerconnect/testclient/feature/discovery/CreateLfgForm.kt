package com.gamerconnect.testclient.feature.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun CreateLfgForm(
    isCreating: Boolean,
    errorMessage: String?,
    creationMessage: String?,
    onCreate: (
        title: String,
        mode: String,
        rankRange: String,
        partySize: String,
        startsAt: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("") }
    var rankRange by remember { mutableStateOf("") }
    var partySize by remember { mutableStateOf("") }
    var startsAt by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = mode,
            onValueChange = { mode = it },
            label = { Text("Mode") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = rankRange,
            onValueChange = { rankRange = it },
            label = { Text("Rank range") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = partySize,
            onValueChange = { partySize = it },
            label = { Text("Party size") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = startsAt,
            onValueChange = { startsAt = it },
            label = { Text("Starts at") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (!creationMessage.isNullOrBlank()) {
            Text(
                text = creationMessage,
                color = Color(0xFF4ADE80)
            )
        }

        Button(
            onClick = {
                onCreate(
                    title,
                    mode,
                    rankRange,
                    partySize,
                    startsAt
                )
            },
            enabled = !isCreating,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Text(
                text = if (isCreating) {
                    "Creating..."
                } else {
                    "Create LFG Post"
                },
                color = Color.White
            )
        }
    }
}

