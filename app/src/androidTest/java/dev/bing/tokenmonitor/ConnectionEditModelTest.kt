package dev.bing.tokenmonitor

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ConnectionEditModelTest {
    @Test fun nullReplacementKeepsEncryptedCredentialWhileExplicitReplacementChangesIt() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as Application
        val store=HubStore(app,"connection-edit-${UUID.randomUUID()}.bin")
        val original=UUID.randomUUID().toString();val replacement=UUID.randomUUID().toString()
        val body="""{"periods":{"today":{"totalTokens":1000}},"devices":[]}"""
        val secure="https://public.example.invalid";val local="http://192.168.1.1:17321"
        store.save(SavedHub(secure,original,body,1,local))
        val statsAuthentication=AtomicReference<String?>()
        val owner=ViewModelStore()
        val client=HubViewModel.defaultClient().newBuilder().addInterceptor {chain->
            val request=chain.request();val stream=request.url.encodedPath.endsWith("/stream")
            val content=when {request.url.encodedPath.endsWith("/health")->"""{"ok":true,"role":"hub"}""";stream->"event: snapshot\ndata: {\"stats\":$body}\n\n";else->body}
            if(request.url.encodedPath.endsWith("/stats")) statsAuthentication.set(request.header("Authorization"))
            val type=if(stream) "text/event-stream" else "application/json"
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").header("Content-Type",type).body(content.toResponseBody(type.toMediaType())).build()
        }.build()
        lateinit var model:HubViewModel
        fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
        fun await(condition:()->Boolean) {val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!condition() && System.nanoTime()<end) Thread.sleep(10);assertTrue("Connection edit condition not reached",condition())}
        try {
            main {model=HubViewModel(app,store,client);owner.put("connection",model)}
            await {!model.state.value.loading};main {model.setForeground(true)}
            await {!model.state.value.loading && statsAuthentication.get()!=null}
            assertEquals(listOf(secure,local),model.connectionEndpoints())
            main {model.editConnection(secure,"http://192.168.1.2:17321",null)}
            await {!model.state.value.loading && store.load()?.alternate=="http://192.168.1.2:17321"}
            // No secret-bearing assertion diagnostics are generated.
            assertTrue(statsAuthentication.get()=="Bearer $original");assertTrue(store.load()?.credential==original)
            main {model.editConnection(secure,local,replacement)}
            await {!model.state.value.loading && store.load()?.credential==replacement}
            assertTrue(statsAuthentication.get()=="Bearer $replacement");assertEquals(1000L,model.state.value.snapshot?.periods?.get("today")?.tokens)
            assertEquals(listOf(secure,local),model.connectionEndpoints())
        } finally {main {model.setForeground(false);owner.clear()};store.clear();client.dispatcher.cancelAll();client.connectionPool.evictAll()}
    }
}
