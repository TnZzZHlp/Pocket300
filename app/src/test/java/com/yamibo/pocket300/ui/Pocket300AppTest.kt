package com.yamibo.pocket300.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class Pocket300AppTest {
    @Test
    fun reservesBottomNavigationSpaceOnTopLevelScreen() {
        assertEquals(80.dp, navigationContentBottomPadding(true, 80.dp))
    }

    @Test
    fun releasesBottomNavigationSpaceAsSoonAsDetailScreenOpens() {
        assertEquals(0.dp, navigationContentBottomPadding(false, 80.dp))
    }
}
