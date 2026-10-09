package io.github.theminionooo.tokenmonitor.fork

import io.github.theminionooo.tokenmonitor.data.storage.DesktopOptions
import org.junit.Assert.*
import org.junit.Test

class PresentationPreferencesCodecTest {
    @Test fun validHub2PreferencesRoundTripWithoutChangingStorageKeys() {
        val value=DesktopOptions(currency="CNY",usdRate=7.0,modelAliases=mapOf("m" to "alias"),hiddenTools=setOf("tool"),hiddenModels=setOf("model"),pinnedTools=setOf("codex"),pinnedModels=setOf("gpt"),toolOrder=listOf("codex","dsh"),modelOrder=listOf("gpt"),homeActivityMetric="cost",homeActiveDays="all",contextRemaining=true,pinnedAccounts=setOf("a"),accountOrder=listOf("a"),hiddenAccounts=setOf("b"),hiddenQuotaWindows=setOf("w"),interfaceFont="system",displayFont="mono",modelGrouping="prefix",currencyRates=mapOf("USD" to 1.0,"CNY" to 7.0))
        assertEquals(value,PresentationPreferencesCodec.decode(PresentationPreferencesCodec.encode(value)))
    }
    @Test fun aMalformedFieldDoesNotResetOtherPreferences() {
        val value=PresentationPreferencesCodec.decode("""{"currency":"CNY","rate":7,"interfaceFont":[],"aliases":{"m":"alias","invalid":[]},"toolOrder":["codex",{},"codex","dsh"],"contextRemaining":true,"hiddenAccounts":["b"],"rates":{"CNY":7,"HKD":[],"TWD":4.2}}""")
        assertEquals("CNY",value.currency);assertEquals(7.0,value.usdRate,0.0);assertEquals("mono",value.interfaceFont)
        assertEquals(mapOf("m" to "alias"),value.modelAliases);assertEquals(listOf("codex","dsh"),value.toolOrder);assertTrue(value.contextRemaining)
        assertEquals(setOf("b"),value.hiddenAccounts);assertEquals(mapOf("CNY" to 7.0,"TWD" to 4.2),value.currencyRates)
    }
    @Test fun invalidRateFallsBackToSameCurrencyAndPreservesFontAndPins() {
        val value=PresentationPreferencesCodec.decode("""{"currency":"CNY","rate":0,"rates":{"CNY":7.1},"interfaceFont":"system","pinnedTools":["codex"]}""")
        assertEquals(7.1,value.usdRate,0.0);assertEquals("system",value.interfaceFont);assertEquals(setOf("codex"),value.pinnedTools)
    }
    @Test fun unknownAndOldPreferencesKeepDefaultsAndKnownValues() {
        assertEquals(DesktopOptions(),PresentationPreferencesCodec.decode(null))
        assertEquals(DesktopOptions(),PresentationPreferencesCodec.decode("invalid-json"))
        val value=PresentationPreferencesCodec.decode("""{"rate":7,"currency":"CNY","future":{},"aliases":{"m":"n"},"tools":["codex"]}""")
        assertEquals("CNY",value.currency);assertEquals(mapOf("m" to "n"),value.modelAliases);assertEquals(setOf("codex"),value.hiddenTools);assertEquals("mono",value.interfaceFont)
    }
    @Test fun validationIsSharedWithLoadingAndRejectsInvalidChoices() {
        assertNull(PresentationPreferencesCodec.normalized(DesktopOptions(usdRate=Double.NaN)))
        assertNull(PresentationPreferencesCodec.normalized(DesktopOptions(homeActivityMetric="unknown")))
        assertNull(PresentationPreferencesCodec.normalized(DesktopOptions(homeActiveDays="unknown")))
        assertNull(PresentationPreferencesCodec.normalized(DesktopOptions(displayFont="unknown")))
        val value=PresentationPreferencesCodec.normalized(DesktopOptions(currencyRates=mapOf("XXX" to 7.0,"CNY" to -1.0),toolOrder=listOf("codex","codex")))!!
        assertEquals(mapOf("USD" to 1.0),value.currencyRates);assertEquals(listOf("codex"),value.toolOrder)
    }
    @Test fun missingForeignRateUsesUsdWithoutDiscardingOtherPreferences() {
        val value=PresentationPreferencesCodec.decode("""{"currency":"CNY","rate":0,"interfaceFont":"system","pinnedTools":["codex"]}""")
        assertEquals("USD",value.currency);assertEquals(1.0,value.usdRate,0.0)
        assertEquals("system",value.interfaceFont);assertEquals(setOf("codex"),value.pinnedTools)
    }
}
