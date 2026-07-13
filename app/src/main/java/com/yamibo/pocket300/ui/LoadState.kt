package com.yamibo.pocket300.ui

internal sealed class LoadState<out T> {
    data object Loading : LoadState<Nothing>()
    data class Ready<T>(val value: T) : LoadState<T>()
    data class Failed(val message: String) : LoadState<Nothing>()
}

