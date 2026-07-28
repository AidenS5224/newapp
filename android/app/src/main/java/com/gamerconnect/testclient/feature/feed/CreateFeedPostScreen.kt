package com.gamerconnect.testclient.feature.feed

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.gamerconnect.testclient.data.feed.FeedGame
import com.gamerconnect.testclient.data.feed.FeedImageUpload

@Composable
fun CreateFeedPostScreen(
    onBack: () -> Unit,
    onPublished: () -> Unit,
    modifier: Modifier = Modifier,
    feedViewModel: FeedViewModel = viewModel()
) {
    val uiState = feedViewModel.createPostUiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    var showDiscardDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            feedViewModel.updateSelectedImage(uri.toString())
        }
    }

    val hasDraft =
        uiState.title.isNotBlank() ||
            uiState.text.isNotBlank() ||
            uiState.selectedImageUri != null ||
            uiState.selectedGameId != null

    fun requestBack() {
        if (uiState.isPublishing) {
            return
        }

        if (hasDraft) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    BackHandler {
        requestBack()
    }

    LaunchedEffect(uiState.publishedSuccessfully) {
        if (uiState.publishedSuccessfully) {
            feedViewModel.consumePublishedSuccessfully()
            onPublished()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    requestBack()
                },
                enabled = !uiState.isPublishing
            ) {
                Text(
                    text = "Cancel",
                    color = Color(0xFFB8BFCC)
                )
            }

            Text(
                text = "Create post",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = {
                    val upload = runCatching {
                        uiState.selectedImageUri?.let { selectedUri ->
                            readValidatedFeedImage(
                                context = context,
                                uri = Uri.parse(selectedUri)
                            )
                        }
                    }.onFailure { error ->
                        feedViewModel.showCreatePostError(
                            error.message ?: "Unable to read the selected image."
                        )
                    }.getOrNull()

                    if (uiState.selectedImageUri == null || upload != null) {
                        feedViewModel.publishPost(upload)
                    }
                },
                enabled = !uiState.isPublishing &&
                    uiState.title.isNotBlank() &&
                    (uiState.text.isNotBlank() || uiState.selectedImageUri != null),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.semantics {
                    contentDescription = "Publish"
                }
            ) {
                if (uiState.isPublishing) {
                    CircularProgressIndicator(
                        color = Color.White
                    )
                } else {
                    Text("Publish")
                }
            }
        }

        OutlinedTextField(
            value = uiState.title,
            onValueChange = feedViewModel::updateDraftTitle,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Post title"
                },
            singleLine = true,
            enabled = !uiState.isPublishing,
            placeholder = {
                Text("Title")
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFF374151),
                focusedPlaceholderColor = Color(0xFF9CA3AF),
                unfocusedPlaceholderColor = Color(0xFF9CA3AF)
            )
        )

        GamePicker(
            availableGames = uiState.availableGames,
            selectedGameId = uiState.selectedGameId,
            isLoading = uiState.isLoadingGames,
            isEnabled = !uiState.isPublishing,
            onGameSelected = feedViewModel::updateSelectedGame
        )

        OutlinedTextField(
            value = uiState.text,
            onValueChange = feedViewModel::updateDraftText,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Post text"
                },
            minLines = 5,
            maxLines = 12,
            enabled = !uiState.isPublishing,
            placeholder = {
                Text("Share a highlight, update, or moment...")
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFF374151),
                focusedPlaceholderColor = Color(0xFF9CA3AF),
                unfocusedPlaceholderColor = Color(0xFF9CA3AF)
            )
        )

        SelectedImageSection(
            selectedImageUri = uiState.selectedImageUri,
            isPublishing = uiState.isPublishing,
            onSelectImage = {
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemoveImage = feedViewModel::removeSelectedImage
        )

        if (uiState.errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2A1730)
                )
            ) {
                Text(
                    text = uiState.errorMessage,
                    color = Color(0xFFFFD1DC),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = {
                showDiscardDialog = false
            },
            title = {
                Text("Discard this post?")
            },
            text = {
                Text("Your draft will be lost.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                    }
                ) {
                    Text("Keep editing")
                }
            }
        )
    }
}

@Composable
private fun GamePicker(
    availableGames: List<FeedGame>,
    selectedGameId: String?,
    isLoading: Boolean,
    isEnabled: Boolean,
    onGameSelected: (String?) -> Unit
) {
    var isExpanded by remember {
        mutableStateOf(false)
    }
    val selectedGame = availableGames.firstOrNull { game ->
        game.id == selectedGameId
    }
    val selectedLabel = selectedGame?.name ?: "No game"

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Tag game",
            color = Color(0xFFB8BFCC),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        Box {
            OutlinedButton(
                onClick = {
                    isExpanded = true
                },
                enabled = isEnabled && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Select game"
                    }
            ) {
                Text(
                    text = when {
                        isLoading -> "Loading your games..."
                        availableGames.isEmpty() -> "No profile games yet"
                        else -> selectedLabel
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            DropdownMenu(
                expanded = isExpanded,
                onDismissRequest = {
                    isExpanded = false
                }
            ) {
                DropdownMenuItem(
                    text = {
                        Text("No game")
                    },
                    onClick = {
                        onGameSelected(null)
                        isExpanded = false
                    }
                )

                availableGames.forEach { game ->
                    DropdownMenuItem(
                        text = {
                            Text(game.name)
                        },
                        onClick = {
                            onGameSelected(game.id)
                            isExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedImageSection(
    selectedImageUri: String?,
    isPublishing: Boolean,
    onSelectImage: () -> Unit,
    onRemoveImage: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onSelectImage,
                enabled = !isPublishing,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = "Select image"
                    }
            ) {
                Text("Select image")
            }

            OutlinedButton(
                onClick = onRemoveImage,
                enabled = !isPublishing && selectedImageUri != null,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = "Remove selected image"
                    }
            ) {
                Text("Remove")
            }
        }

        if (selectedImageUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF111A2B)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = selectedImageUri,
                    contentDescription = "Selected post image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

private fun readValidatedFeedImage(
    context: Context,
    uri: Uri
): FeedImageUpload {
    val mimeType = context.contentResolver.getType(uri)
        ?: error("Choose a supported image file.")
    val extension = extensionForMimeType(mimeType)
        ?: error("Choose a JPG, PNG or WebP image.")

    val declaredSize = queryContentSize(
        context = context,
        uri = uri
    )

    require(declaredSize == null || declaredSize <= MAX_FEED_IMAGE_BYTES) {
        "Image must be smaller than 10 MB."
    }

    val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
        stream.readBytes()
    } ?: error("Unable to read the selected image.")

    require(bytes.size <= MAX_FEED_IMAGE_BYTES) {
        "Image must be smaller than 10 MB."
    }

    return FeedImageUpload(
        bytes = bytes,
        mimeType = mimeType,
        extension = extension
    )
}

private fun queryContentSize(
    context: Context,
    uri: Uri
): Long? {
    return context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.SIZE),
        null,
        null,
        null
    )?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (sizeIndex >= 0 && cursor.moveToFirst()) {
            cursor.getLong(sizeIndex)
        } else {
            null
        }
    }
}

private fun extensionForMimeType(
    mimeType: String
): String? {
    return when (mimeType) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> null
    }
}

private const val MAX_FEED_IMAGE_BYTES = 10 * 1024 * 1024
