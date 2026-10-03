package io.github.theminionooo.tokenmonitor

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectFoundationTest {
    @Test
    fun applicationIdRemainsStable() {
        assertEquals(
            "io.github.theminionooo.tokenmonitor.cloudflare",
            BuildConfig.APPLICATION_ID.removeSuffix(".preview"),
        )
    }
}
