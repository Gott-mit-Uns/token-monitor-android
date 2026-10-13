package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class TokenDigitRollTest {
    @Test fun stablePrefixGroupedCarryAndReverseKeepAtMostTwoLayers() {
        val roll=TokenDigitRoll("1,099")
        assertTrue(roll.retarget("1,100",true,true))
        assertEquals(listOf(RollingGlyph('1',0f,0f)),roll.columns[0])
        assertEquals(listOf(RollingGlyph(',',0f,0f)),roll.columns[1])
        assertEquals(-1f,roll.columns[2][0].to,0f)
        roll.frame(.3f)
        val dominant=roll.columns.map {it.minByOrNull {g->kotlin.math.abs(g.position(roll.progress))}}
        val positions=dominant.map {it?.position(roll.progress)}
        roll.retarget("1,090",false,true)
        dominant.forEachIndexed {i,g->if(g!=null && g.char!='0') {
            val retained=roll.columns[i].firstOrNull {it.char==g.char}
            if(retained!=null) assertEquals(positions[i]!!,retained.from,0.0001f)
        }}
        assertTrue(roll.columns.all {it.size<=2})
        roll.frame(1f);assertFalse(roll.running);assertEquals("1,090",roll.target)
    }
    @Test fun lengthChangeLongMaximumMissingAndRepeatedUpdatesHaveBoundedState() {
        val roll=TokenDigitRoll("999")
        roll.retarget("1,000",true,true);assertEquals(5,roll.length)
        repeat(300) {roll.frame(.15f);roll.retarget((Long.MAX_VALUE-it).toString(),false,true);assertTrue(roll.columns.all {it.size<=2})}
        assertTrue(roll.length<=19)
        roll.retarget("—",false,false);assertFalse(roll.running);assertEquals("—",roll.target)
        roll.retarget("0",true,false);assertEquals("0",roll.target);assertFalse(roll.running)
    }
    @Test fun modesAndNoOpNeverInventWork() {
        assertEquals("roll",TokenNumberAnimationMode.normalize("future"));assertEquals("count",TokenNumberAnimationMode.normalize("count"))
        val roll=TokenDigitRoll("100")
        assertFalse(roll.retarget("100",true,true));assertFalse(roll.running)
        roll.retarget("200",true,true);roll.settle();assertEquals("200",roll.target);assertFalse(roll.running)
        val p=DisplayPreferences(appearance=AppearancePreferences(showLiveDot=false,showToolIcons=false),main=MainDisplayPreferences(titleIconOnly=true))
        val q=DisplayChange.TokenNumberAnimation("count").apply(p)
        assertEquals(p,q.copy(appearance=q.appearance.copy(tokenNumberAnimation="roll")))
    }
}
