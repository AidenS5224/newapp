package com.gamerconnect.testclient.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp

@Composable
fun ChatScreen(
    conversationId: String,
    conversationTitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var messageText by remember {
        mutableStateOf("")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF111827)
                )
            ) {
                Text(
                    text = "Back",
                    color = Color.White
                )
            }

            Text(
                text = conversationTitle,
                color = Color.White,
                fontSize = 24.sp
            )

            Text(
                text = "Conversation ID: $conversationId",
                color = Color(0xFF8D94A3),
                fontSize = 12.sp
            )

            Text(
                text = "Messages will appear here.",
                color = Color(0xFFB8BFCC)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = {
                    messageText = it
                },
                label = {
                    Text("Message")
                },
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = {
                    messageText = ""
                },
                enabled = messageText.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Send",
                    color = Color.White
                )
            }
        }
    }
}

