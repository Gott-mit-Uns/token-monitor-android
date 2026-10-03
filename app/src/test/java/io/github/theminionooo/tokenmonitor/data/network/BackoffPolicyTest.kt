package io.github.theminionooo.tokenmonitor.data.network

import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

class BackoffPolicyTest {
    @Test
    fun `delays are jittered and bounded`() {
        val policy = BackoffPolicy(Random(7))

        val first = policy.delayForAttempt(0)
        val later = policy.delayForAttempt(4)
        val capped = policy.delayForAttempt(40)

        assertTrue(first in 800L..1_200L)
        assertTrue(later in 12_800L..19_200L)
        assertTrue(capped in 48_000L..60_000L)
    }
}
