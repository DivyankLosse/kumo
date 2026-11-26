package com.ascend.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shared ViewModel helpers: hot state that survives brief config changes, and fire-and-forget writes. */
abstract class BaseViewModel : ViewModel() {
    protected fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    protected fun io(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

fun Double.fmt(decimals: Int = 1): String = "%.${decimals}f".format(this).trimEnd('0').trimEnd('.')
