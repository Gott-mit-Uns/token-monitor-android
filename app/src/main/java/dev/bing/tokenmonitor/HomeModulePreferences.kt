package dev.bing.tokenmonitor

/** Full order and visible membership are independent, as in the pinned renderer. */
internal object HomeModulePreferences {
    val freshVisible=defaultHomeModules.filter {it !in setOf("tools","devices")}
    fun catalog(@Suppress("UNUSED_PARAMETER") p:DisplayPreferences)=defaultHomeModules
    private fun canonical(id:String)=if(id=="trends") "activity" else id
    // Storage indices and legacy records stay unchanged until a home preference is edited.
    private fun storedOrder(p:DisplayPreferences):List<String> {
        val known=defaultHomeModules+if("trends" in p.homeModules || "trends" in p.homeModuleOrder) listOf("trends") else emptyList()
        val raw=p.homeModuleOrder.ifEmpty {if(p.homeModules==freshVisible) defaultHomeModules else p.homeModules}
        return (raw+known).filter {it in known}.distinct()
    }
    fun order(p:DisplayPreferences):List<String> {
        return storedOrder(p).map(::canonical).filter {it in defaultHomeModules}.distinct()
    }
    fun visible(p:DisplayPreferences)=order(p).filter {it in p.homeModules.map(::canonical)}
    fun toggle(p:DisplayPreferences,id:String):DisplayPreferences {
        val full=order(p);if(id !in full) return p
        val shown=visible(p)
        val selected=if(id in shown) shown-id else shown+id
        // The source normalizer resets an all-hidden selection to show all.
        return p.copy(homeModules=full.filter {it in selected}.ifEmpty {full},homeModuleOrder=full)
    }
    fun move(p:DisplayPreferences,id:String,direction:Int):DisplayPreferences {
        if(direction !in listOf(-1,1)) return p
        val full=order(p).toMutableList();val from=full.indexOf(id);val to=from+direction
        if(from<0 || to !in full.indices) return p
        full.add(to,full.removeAt(from))
        return p.copy(homeModules=full.filter {it in visible(p)},homeModuleOrder=full)
    }
    fun reset(p:DisplayPreferences):DisplayPreferences {
        val full=catalog(p);return p.copy(homeModules=full.filter {it in visible(p)},homeModuleOrder=full)
    }
    fun showAll(p:DisplayPreferences):DisplayPreferences {
        val full=order(p);return p.copy(homeModules=full,homeModuleOrder=full)
    }
    fun replace(p:DisplayPreferences,requested:List<String>):DisplayPreferences {
        val selected=requested.map(::canonical).filter {it in defaultHomeModules}.distinct().ifEmpty {defaultHomeModules}
        val changed=p.copy(homeModules=selected)
        val full=(selected+order(changed)).distinct()
        return changed.copy(homeModuleOrder=full)
    }
    fun valid(p:DisplayPreferences)=p.homeModuleOrder.isEmpty() || (p.homeModuleOrder==storedOrder(p) && p.homeModuleOrder.containsAll(p.homeModules))
}
