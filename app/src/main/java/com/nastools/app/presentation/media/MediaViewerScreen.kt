package com.nastools.app.presentation.media

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nastools.app.util.RemoteMediaItem
import com.nastools.app.util.RemoteMediaType
import com.nastools.app.presentation.components.NasEmptyState
import com.nastools.app.presentation.components.NasScaffold
import com.nastools.app.presentation.components.NasTopAppBar

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MediaViewerScreen(
    viewModel: MediaViewerViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val current = uiState.current

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
        }
    }

    NasScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            NasTopAppBar(
                title = current?.name ?: "Media",
                subtitle = uiState.configName,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::retry, enabled = current != null) {
                        Icon(Icons.Default.Refresh, "Retry")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    uiState.isLoading -> CircularProgressIndicator()
                    current == null -> MediaError(message = uiState.errorMessage ?: "Media not found", onRetry = viewModel::retry)
                    current.type == RemoteMediaType.Image -> ImagePreview(uiState)
                    current.type == RemoteMediaType.Video -> VideoPreview(
                        item = current,
                        viewModel = viewModel,
                        externalRetryNonce = uiState.retryNonce
                    )
                }
            }

            MediaStepper(
                currentName = current?.name.orEmpty(),
                indexLabel = if (uiState.currentIndex >= 0) {
                    "${uiState.currentIndex + 1} / ${uiState.items.size}"
                } else {
                    ""
                },
                hasPrevious = uiState.hasPrevious,
                hasNext = uiState.hasNext,
                onPrevious = viewModel::previous,
                onNext = viewModel::next
            )
        }
    }
}

@Composable
private fun ImagePreview(uiState: MediaViewerUiState) {
    val bytes = uiState.imageBytes
    when {
        uiState.isImageLoading -> CircularProgressIndicator()
        bytes == null -> MediaError(
            message = uiState.errorMessage ?: "Image preview unavailable",
            onRetry = null
        )
        else -> {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(bytes)
                    .crossfade(true)
                    .build(),
                contentDescription = uiState.current?.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPreview(
    item: RemoteMediaItem,
    viewModel: MediaViewerViewModel,
    externalRetryNonce: Int
) {
    val context = LocalContext.current
    val dataSourceFactory = remember(item.path, item.size) {
        viewModel.videoDataSourceFactory(item)
    }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var playerError by remember(item.path, externalRetryNonce) { mutableStateOf<String?>(null) }
    var retryNonce by remember(item.path, externalRetryNonce) { mutableStateOf(0) }

    if (dataSourceFactory == null) {
        MediaError(message = "Video source unavailable", onRetry = null)
        return
    }

    DisposableEffect(item.path, dataSourceFactory, externalRetryNonce, retryNonce) {
        val exoPlayer = ExoPlayer.Builder(context).build()
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playerError = error.message ?: "Video playback failed"
            }
        }
        exoPlayer.addListener(listener)
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(Uri.parse("nastools://media/${Uri.encode(item.path)}")))
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        player = exoPlayer

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            if (player === exoPlayer) player = null
        }
    }

    val currentPlayer = player
    if (playerError != null) {
        MediaError(
            message = playerError ?: "Video playback failed",
            onRetry = {
                playerError = null
                retryNonce += 1
            }
        )
    } else if (currentPlayer == null) {
        CircularProgressIndicator()
    } else {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = true
                    this.player = currentPlayer
                }
            },
            update = { playerView ->
                playerView.player = currentPlayer
            }
        )
    }
}

@Composable
private fun MediaStepper(
    currentName: String,
    indexLabel: String,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onPrevious, enabled = hasPrevious) {
            Icon(Icons.AutoMirrored.Filled.NavigateBefore, "Previous media")
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                currentName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (indexLabel.isNotBlank()) {
                Text(
                    indexLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onNext, enabled = hasNext) {
            Icon(Icons.AutoMirrored.Filled.NavigateNext, "Next media")
        }
    }
}

@Composable
private fun MediaError(
    message: String,
    onRetry: (() -> Unit)?
) {
    NasEmptyState(
        icon = Icons.Default.BrokenImage,
        title = "Cannot open media",
        message = message,
        action = if (onRetry != null) {
            {
                Button(onClick = onRetry) {
                    Icon(Icons.Default.VideoFile, null, modifier = Modifier.size(18.dp))
                    Text("Retry", modifier = Modifier.padding(start = 8.dp))
                }
            }
        } else {
            null
        }
    )
}
