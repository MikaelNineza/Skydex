package com.skydex.app.ui.common

import android.util.Log
import com.skydex.app.data.remote.ApiException
import java.io.IOException

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Content<T>(val data: T) : UiState<T>

    data class Error(val message: String) : UiState<Nothing>
}

/** A short message fit to show the user. Logs the full error, since the message hides the cause. */
fun Throwable.userMessage(): String {
    Log.w("Skydex", "Showing error to user", this)
    return when (this) {
        is ApiException -> message ?: "Server error"
        is IOException -> "Can't reach the Skydex server"
        else -> message ?: "Something went wrong"
    }
}
