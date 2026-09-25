package com.skydex.app.ui.common

import com.skydex.app.data.remote.ApiException
import java.io.IOException

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Content<T>(val data: T) : UiState<T>

    data class Error(val message: String) : UiState<Nothing>
}

/** A short message fit to show the user. */
fun Throwable.userMessage(): String = when (this) {
    is ApiException -> message ?: "Server error"
    is IOException -> "Can't reach the Skydex server"
    else -> message ?: "Something went wrong"
}
