package dev.bing.tokenmonitor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class BackgroundImageState(val bitmap: Bitmap? = null, val opacity: Int = 28, val busy: Boolean = true, val error: Boolean = false)

internal class BackgroundImageModel(application: Application) : AndroidViewModel(application) {
    private val store = BackgroundImageStore(application)
    private val writes = Mutex()
    @Volatile private var generation = 0
    private var png: ByteArray? = null
    private var committedOpacity = 28
    private val mutable = MutableStateFlow(BackgroundImageState())
    val state: StateFlow<BackgroundImageState> = mutable
    init {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { store.load().let { it to it.png?.let(BackgroundImageDecoder::decode) } } }
            if (generation == 0) result.fold({ (record, bitmap) ->
                png = record.png; committedOpacity = record.opacity; mutable.value = BackgroundImageState(bitmap, record.opacity, busy = false)
            }, { mutable.value = BackgroundImageState(busy = false, error = true) })
        }
    }
    fun select(uri: Uri?) {
        if (uri == null) return
        mutate {
            require(uri.scheme == "content")
            val bytes = BackgroundImageDecoder.read(requireNotNull(getApplication<Application>().contentResolver.openInputStream(uri)))
            val bitmap = BackgroundImageDecoder.decode(bytes)
            BackgroundImageRecord(state.value.opacity, BackgroundImageDecoder.png(bitmap)) to bitmap
        }
    }
    fun previewOpacity(value: Int) {
        if (!state.value.busy) mutable.value = state.value.copy(opacity = BackgroundImageRules.opacity(value))
    }
    fun saveOpacity() = mutate { BackgroundImageRecord(state.value.opacity, png) to state.value.bitmap }
    fun clear() = mutate { BackgroundImageRecord(state.value.opacity) to null }
    override fun onCleared() { generation++; super.onCleared() }
    private fun mutate(load: () -> Pair<BackgroundImageRecord, Bitmap?>) {
        val ticket = ++generation
        mutable.value = state.value.copy(busy = true, error = false)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                check(ticket == generation)
                val next = load()
                writes.withLock { if (ticket == generation) store.save(next.first) }
                next
            } }
            if (ticket == generation) result.fold({ (record, bitmap) ->
                png = record.png; committedOpacity = record.opacity; mutable.value = BackgroundImageState(bitmap, record.opacity, busy = false)
            }, { mutable.value = state.value.copy(opacity = committedOpacity, busy = false, error = true) })
        }
    }
}
