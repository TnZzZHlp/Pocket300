package com.yamibo.pocket300.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadTest {
    @Test
    fun convertsOperationFailureToFailedState() = runBlocking {
        val result = load<Unit> { error("boom") }

        assertEquals(LoadState.Failed("boom"), result)
    }

    @Test(expected = CancellationException::class)
    fun propagatesCoroutineCancellation() {
        runBlocking {
            load<Unit> { throw CancellationException("cancelled") }
        }
    }
}
