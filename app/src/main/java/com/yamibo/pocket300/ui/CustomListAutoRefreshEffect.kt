package com.yamibo.pocket300.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yamibo.pocket300.data.CustomListAutoRefreshScheduler
import com.yamibo.pocket300.data.CustomListDatabase
import com.yamibo.pocket300.data.CustomListRefreshMode
import com.yamibo.pocket300.data.CustomListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CustomListAutoRefreshEffect() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val database = remember(context) { CustomListDatabase.getInstance(context) }
    val repository = remember(database) { CustomListRepository(database, api.search) }
    val scheduler = remember(database, repository) {
        CustomListAutoRefreshScheduler(
            loadLists = { withContext(Dispatchers.IO) { database.getLists() } },
            refresh = { list -> repository.refresh(list, CustomListRefreshMode.REGULAR) },
        )
    }
    val scope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner, scheduler) {
        var refreshJob: Job? = null

        fun startRefreshing() {
            if (refreshJob?.isActive != true) {
                refreshJob = scope.launch { scheduler.refreshContinuously() }
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> startRefreshing()
                Lifecycle.Event.ON_STOP -> refreshJob?.cancel()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            startRefreshing()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            refreshJob?.cancel()
        }
    }
}
