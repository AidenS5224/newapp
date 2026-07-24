package com.gamerconnect.testclient.feature.feed

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
fun FeedScreen(
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
            text = "Feed",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeedTab(
                title = "For You",
                selected = true
            )

            FeedTab(
                title = "Following",
                selected = false
            )

            FeedTab(
                title = "Groups",
                selected = false
            )
        }

        SamplePostCard()
    }
}

@Composable
private fun FeedTab(
    title: String,
    selected: Boolean
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Transparent
            }
        )
    ) {
        Text(
            text = title,
            color = if (selected) Color.White else Color(0xFFB8BFCC),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(
                horizontal = 18.dp,
                vertical = 10.dp
            )
        )
    }
}

@Composable
private fun SamplePostCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0B1220)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "GhostRider",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "2h ago · Apex Legends",
                color = Color(0xFF8D94A3),
                fontSize = 13.sp
            )

            Text(
                text = "Clutched the 1v3 to win the game 🔥",
                color = Color.White,
                fontSize = 16.sp
            )

            Text(
                text = "#ApexLegends #Ranked",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 15.sp
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF111A2B)
                )
            ) {
                Text(
                    text = "Media preview",
                    color = Color(0xFF9CA3AF),
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 70.dp
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "♥ 128",
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Comments 24",
                    color = Color(0xFFB8BFCC)
                )

                Text(
                    text = "Shares 12",
                    color = Color(0xFFB8BFCC)
                )
            }
        }
    }
}