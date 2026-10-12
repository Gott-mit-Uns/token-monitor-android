package dev.bing.tokenmonitor

internal object DesktopMenuNavigation {
    fun navigate(enabled:List<Boolean>,current:Int,direction:String,wrap:Boolean=false):Int {
        val indices=enabled.indices.filter {enabled[it]}
        if(indices.isEmpty()) return -1
        if(direction=="first") return indices.first()
        if(direction=="last") return indices.last()
        val step=when(direction) {"previous"->-1;"page-previous"->-10;"page-next"->10;else->1}
        val at=indices.indexOf(current)
        if(at<0) return if(step<0) indices.last() else indices.first()
        val next=if(wrap) (at+step).mod(indices.size) else (at+step).coerceIn(indices.indices)
        return indices[next]
    }
    fun typeahead(labels:List<String>,enabled:List<Boolean>,current:Int,text:String):Int {
        if(text.isEmpty()) return current
        val query=text.lowercase(java.util.Locale.getDefault())
        val repeated=query.all {it==query.first()};val prefix=if(repeated) query.take(1) else query
        for(step in (if(repeated) 1 else 0) until labels.size+(if(repeated) 1 else 0)) {
            val index=(current.coerceAtLeast(0)+step).mod(labels.size)
            if(enabled[index] && labels[index].lowercase(java.util.Locale.getDefault()).startsWith(prefix)) return index
        }
        return current
    }
}
