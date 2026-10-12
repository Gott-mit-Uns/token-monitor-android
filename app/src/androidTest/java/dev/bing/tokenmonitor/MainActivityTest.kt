package dev.bing.tokenmonitor

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test

class MainActivityTest {
    @Test fun actualActivityCreatesDefaultModelAndSurvivesStopResumeAndRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var original: HubViewModel
            scenario.onActivity { activity ->
                original = ViewModelProvider(activity)[HubViewModel::class.java]
                assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            assertFalse(original.state.value.live)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            scenario.onActivity { activity ->
                assertSame(original, ViewModelProvider(activity)[HubViewModel::class.java])
                assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState)
            }
        }
    }
}
