package com.mymovie.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mymovie.app.AppContainer
import com.mymovie.app.appContainer
import com.mymovie.app.data.MediaItem
import com.mymovie.app.data.MissingApiKeyException
import com.mymovie.app.data.Page
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val missingKey: Boolean = false) : UiState<Nothing>
}

fun Throwable.toUiError(): UiState.Error = when (this) {
    is MissingApiKeyException ->
        UiState.Error("Add your free TMDB API key in Settings to load movie and series data.", true)
    is HttpException ->
        if (code() == 401) UiState.Error("TMDB rejected the API key. Check it in Settings.", true)
        else UiState.Error("Server error (${code()}). Try again shortly.")
    is IOException -> UiState.Error("No connection. Check your network and try again.")
    else -> UiState.Error(message ?: "Something went wrong.")
}

suspend fun <T> runUi(block: suspend () -> T): UiState<T> = try {
    UiState.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    e.toUiError()
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline build: (AppContainer) -> VM): VM {
    val container = LocalContext.current.appContainer
    return viewModel(key = key, factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = build(container) as T
    })
}

/** Page-by-page loader used by every catalogue grid. */
class Paginator(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val fetch: suspend (Int) -> Page<MediaItem>
) {
    var items by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<UiState.Error?>(null); private set
    var endReached by mutableStateOf(false); private set
    private var page = 0
    private var generation = 0
    private var job: Job? = null

    fun reset() {
        job?.cancel()
        generation++
        items = emptyList(); page = 0; endReached = false; error = null; loading = false
        loadMore()
    }

    fun loadMore() {
        if (loading || endReached) return
        loading = true
        error = null
        val gen = generation
        job = scope.launch {
            try {
                var added = false
                while (!added && !endReached) {
                    val next = page + 1
                    val r = fetch(next)
                    if (gen != generation) return@launch
                    val before = items.size
                    items = (items + r.items).distinctBy { it.key }
                    page = next
                    endReached = next >= minOf(r.totalPages, 500)
                    added = items.size > before
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (gen == generation) error = t.toUiError()
            } finally {
                if (gen == generation) loading = false
            }
        }
    }
}
