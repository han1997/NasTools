package com.nastools.app.presentation.media

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.nastools.app.data.database.entity.NasConfigEntity
import com.nastools.app.data.media.RemoteMediaDataSourceFactory
import com.nastools.app.data.network.StorageAdapter
import com.nastools.app.data.network.StorageAdapterFactory
import com.nastools.app.data.repository.NasConfigRepository
import com.nastools.app.util.RemoteMedia
import com.nastools.app.util.RemoteMediaItem
import com.nastools.app.util.RemoteMediaType
import com.nastools.app.util.RemotePath
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MediaViewerUiState(
    val configName: String = "",
    val current: RemoteMediaItem? = null,
    val items: List<RemoteMediaItem> = emptyList(),
    val currentIndex: Int = -1,
    val imageBytes: ByteArray? = null,
    val isLoading: Boolean = false,
    val isImageLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val hasPrevious: Boolean get() = currentIndex > 0
    val hasNext: Boolean get() = currentIndex >= 0 && currentIndex < items.lastIndex
}

@HiltViewModel
class MediaViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val configRepository: NasConfigRepository,
    private val adapterFactory: StorageAdapterFactory
) : ViewModel() {
    private val _uiState = MutableStateFlow(MediaViewerUiState())
    val uiState: StateFlow<MediaViewerUiState> = _uiState.asStateFlow()

    private var config: NasConfigEntity? = null
    private val initialConfigId: String = savedStateHandle.get<String>("configId").orEmpty()
    private val initialPath: String = Uri.decode(savedStateHandle.get<String>("path").orEmpty())

    init {
        load(initialConfigId, initialPath)
    }

    fun previous() {
        val state = _uiState.value
        if (!state.hasPrevious) return
        selectIndex(state.currentIndex - 1)
    }

    fun next() {
        val state = _uiState.value
        if (!state.hasNext) return
        selectIndex(state.currentIndex + 1)
    }

    fun retry() {
        val current = _uiState.value.current
        if (current == null) {
            load(initialConfigId, initialPath)
        } else {
            selectPath(current.path)
        }
    }

    @UnstableApi
    fun videoDataSourceFactory(item: RemoteMediaItem): RemoteMediaDataSourceFactory? {
        val currentConfig = config ?: return null
        return RemoteMediaDataSourceFactory(
            adapter = adapterFactory.create(currentConfig),
            remotePath = item.path,
            contentLength = item.size
        )
    }

    private fun load(configId: String, path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val loadedConfig = configRepository.getById(configId)
                    ?: throw IOException("NAS config not found")
                config = loadedConfig
                val adapter = adapterFactory.create(loadedConfig)
                val normalizedPath = RemotePath.normalize(path)
                val parent = RemotePath.parent(normalizedPath)
                val directoryItems = withContext(Dispatchers.IO) {
                    adapter.list(parent).mapNotNull(RemoteMedia::toMediaItem)
                }
                val fallbackItems = if (directoryItems.any { it.path == normalizedPath }) {
                    directoryItems
                } else {
                    buildFallbackItem(adapter, normalizedPath)?.let { listOf(it) }.orEmpty()
                }
                if (fallbackItems.isEmpty()) throw IOException("Unsupported media file")

                val index = fallbackItems.indexOfFirst { it.path == normalizedPath }
                    .takeIf { it >= 0 } ?: 0
                _uiState.update {
                    it.copy(
                        configName = loadedConfig.name,
                        items = fallbackItems,
                        currentIndex = index,
                        current = fallbackItems[index],
                        imageBytes = null,
                        isLoading = false,
                        errorMessage = null
                    )
                }
                loadImageIfNeeded(fallbackItems[index])
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isImageLoading = false,
                        errorMessage = error.message ?: "Failed to open media"
                    )
                }
            }
        }
    }

    private fun selectIndex(index: Int) {
        val item = _uiState.value.items.getOrNull(index) ?: return
        _uiState.update {
            it.copy(
                currentIndex = index,
                current = item,
                imageBytes = null,
                errorMessage = null
            )
        }
        loadImageIfNeeded(item)
    }

    private fun selectPath(path: String) {
        val index = _uiState.value.items.indexOfFirst { it.path == path }
        if (index >= 0) selectIndex(index)
    }

    private fun loadImageIfNeeded(item: RemoteMediaItem) {
        if (item.type != RemoteMediaType.Image) return

        viewModelScope.launch {
            val currentConfig = config
            if (currentConfig == null) {
                _uiState.update { it.copy(errorMessage = "NAS config not loaded") }
                return@launch
            }
            _uiState.update { it.copy(isImageLoading = true, errorMessage = null) }
            runCatching {
                val adapter = adapterFactory.create(currentConfig)
                val knownSize = item.size ?: withContext(Dispatchers.IO) {
                    adapter.stat(item.path)?.size
                }
                if (knownSize != null && knownSize > MAX_IMAGE_BYTES) {
                    throw IOException("Image is too large for preview")
                }
                val bytes = withContext(Dispatchers.IO) {
                    adapter.download(item.path)
                }
                if (bytes.size > MAX_IMAGE_BYTES) {
                    throw IOException("Image is too large for preview")
                }
                bytes
            }.onSuccess { bytes ->
                if (_uiState.value.current?.path == item.path) {
                    _uiState.update {
                        it.copy(imageBytes = bytes, isImageLoading = false, errorMessage = null)
                    }
                }
            }.onFailure { error ->
                if (_uiState.value.current?.path == item.path) {
                    _uiState.update {
                        it.copy(
                            imageBytes = null,
                            isImageLoading = false,
                            errorMessage = error.message ?: "Failed to load image"
                        )
                    }
                }
            }
        }
    }

    private suspend fun buildFallbackItem(adapter: StorageAdapter, path: String): RemoteMediaItem? {
        val type = RemoteMedia.typeForName(RemotePath.basename(path)) ?: return null
        val stat = withContext(Dispatchers.IO) { adapter.stat(path) }
        if (stat?.isDirectory == true) return null
        return RemoteMediaItem(
            path = path,
            name = RemotePath.basename(path),
            type = type,
            size = stat?.size
        )
    }

    companion object {
        private const val MAX_IMAGE_BYTES = 50L * 1024L * 1024L
    }
}
