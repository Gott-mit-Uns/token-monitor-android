package dev.bing.tokenmonitor

import java.util.Locale
import java.math.BigInteger

/** Desktop clientDisplayPreferences rules, applied only to presentation rows. */
object ToolDisplay {
    private val known = desktopManagedToolIds.toSet()
    private fun id(raw: String) = raw.trim().lowercase(Locale.ROOT)
    fun selected(values: Collection<String>): List<String> = values.map(::id).filter { it in known }.distinct()
    fun normalizedOrder(values: Collection<String>): List<String> = selected(values).let { chosen -> chosen + desktopManagedToolIds.filter { it !in chosen } }
    fun settingsOrder(preferences: DisplayPreferences): List<String> {
        val base = normalizedOrder(preferences.toolOrder)
        if (preferences.toolOrder.isNotEmpty()) return base
        val pins = selected(preferences.pinnedTools)
        return pins + base.filter { it !in pins }
    }
    fun <T> apply(rows: List<Pair<String, T>>, preferences: DisplayPreferences): List<Pair<String, T>> {
        val hidden = selected(preferences.hiddenTools).toSet()
        val visible = rows.filter { id(it.first) !in hidden }
        val priorities = if (preferences.toolOrder.isEmpty()) selected(preferences.pinnedTools) else normalizedOrder(preferences.toolOrder)
        val index = priorities.withIndex().associate { it.value to it.index }
        // Stable sort retains usage order for unpinned or uncatalogued tools.
        return visible.sortedBy { index[id(it.first)] ?: Int.MAX_VALUE }
    }
    fun usageRows(period: Period?, preferences: DisplayPreferences): List<Pair<String, BigInteger?>> {
        if (period == null) return emptyList()
        val rows = (period.tools.keys + period.toolDetails.keys).map { key -> key to period.tools[key]?.let(BigInteger::valueOf) }.sortedByDescending { it.second }
        return apply(rows, preferences)
    }
    fun toggleHidden(preferences: DisplayPreferences, raw: String): DisplayPreferences {
        val tool = id(raw)
        if (tool !in known) return preferences
        val hidden = selected(preferences.hiddenTools).toMutableSet()
        if (!hidden.remove(tool)) hidden.add(tool)
        return preferences.copy(hiddenTools = hidden)
    }
    fun togglePin(preferences: DisplayPreferences, raw: String): DisplayPreferences {
        val tool = id(raw)
        if (tool !in known) return preferences
        val pins = selected(preferences.pinnedTools).toMutableList()
        if (!pins.remove(tool)) pins.add(tool)
        return preferences.copy(pinnedTools = pins, toolOrder = emptyList())
    }
    private fun movableOrder(preferences: DisplayPreferences, raw: String): List<String> =
        if (preferences.toolOrder.isEmpty() && id(raw) in selected(preferences.pinnedTools)) selected(preferences.pinnedTools) else normalizedOrder(preferences.toolOrder)
    fun canMove(preferences: DisplayPreferences, raw: String, direction: Int): Boolean {
        val order = movableOrder(preferences, raw)
        val from = order.indexOf(id(raw))
        return direction in listOf(-1, 1) && from >= 0 && from + direction in order.indices
    }
    fun move(preferences: DisplayPreferences, raw: String, direction: Int): DisplayPreferences {
        if (!canMove(preferences, raw, direction)) return preferences
        val order = movableOrder(preferences, raw).toMutableList()
        val from = order.indexOf(id(raw))
        val tool = order.removeAt(from); order.add(from + direction, tool)
        return if (preferences.toolOrder.isEmpty() && id(raw) in selected(preferences.pinnedTools)) preferences.copy(pinnedTools = order)
        else preferences.copy(toolOrder = order, pinnedTools = emptyList())
    }
}
