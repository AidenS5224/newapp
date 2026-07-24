package com.gamerconnect.testclient.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GroupsScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Groups",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        GroupCard(
            name = "Rocket Squad",
            game = "Rocket League",
            members = "4 members",
            status = "2 online"
        )

        GroupCard(
            name = "Competitive Crew",
            game = "Apex Legends",
            members = "6 members",
            status = "3 online"
        )

        GroupCard(
            name = "BuildBattlers",
            game = "Fortnite",
            members = "5 members",
            status = "1 online"
        )
    }
}

@Composable
private fun GroupCard(
    name: String,
    game: String,
    members: String,
    status: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0B1220)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = name,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = game,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = members,
                    color = Color(0xFFB8BFCC),
                    fontSize = 13.sp
                )

                Text(
                    text = status,
                    color = Color(0xFF4ADE80),
                    fontSize = 13.sp
                )
            }
        }
    }
}
