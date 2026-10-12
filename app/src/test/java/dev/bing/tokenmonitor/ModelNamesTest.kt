package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class ModelNamesTest {
    @Test fun emptySharedRulesPreserveRawNames() {
        val rules = ModelNamesParser.parse("""{"ok":true,"value":null}""")
        assertEquals(mapOf("vendor/model" to BigInteger.TEN), rules.project(mapOf("vendor/model" to 10L)))
    }
    @Test fun duplicateAndPrefixModesHonorExplicitOneStepAliases() {
        val models = mapOf("vendor/Model.One" to 2L, "model-one" to 3L, "solo/model-two" to 4L)
        val duplicates = ModelNames(emptyMap(), "duplicates").project(models)
        assertEquals(BigInteger.valueOf(5), duplicates["model-one"])
        assertEquals(BigInteger.valueOf(4), duplicates["solo/model-two"])
        val explicit = ModelNames(mapOf("model-one" to "Friendly", "Friendly" to "No-chain"), "prefix").project(models)
        assertEquals(BigInteger.valueOf(5), explicit["Friendly"])
        assertEquals(BigInteger.valueOf(4), explicit["model-two"])
    }
    @Test fun groupingNeverOverflowsLongAndDoesNotMutateSources() {
        val models = mapOf("vendor/model" to Long.MAX_VALUE, "model" to Long.MAX_VALUE)
        val projected = ModelNames(emptyMap(), "duplicates").project(models)
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO), projected["model"])
        assertEquals(2, models.size)
    }
}
