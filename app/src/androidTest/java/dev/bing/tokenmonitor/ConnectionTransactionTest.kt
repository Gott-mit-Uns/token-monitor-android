package dev.bing.tokenmonitor

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual TLS, HubHttp, SSE and Keystore; two separate public synthetic Hub identities. */
class ConnectionTransactionTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val app=instrumentation.targetContext.applicationContext as Application
    private fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
    private fun await(condition:()->Boolean) {
        val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
        while(System.nanoTime()<end) {if(condition()) return;Thread.sleep(15)}
        throw AssertionError("Public connection transaction condition not reached")
    }
    private inner class Peers:AutoCloseable {
        val oldSecret=UUID.randomUUID().toString();val newSecret=UUID.randomUUID().toString()
        val certificate=HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val serverTls=HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trusted=HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val old=NetworkFixtureHub(oldSecret,serverTls)
        val next=NetworkFixtureHub(newSecret,serverTls,"""{"periods":{"today":{"totalTokens":777}},"devices":[]}""")
        val plain=NetworkFixtureHub(oldSecret)
        val oldAddress="https://127.0.0.1:${old.port}";val newAddress="https://127.0.0.1:${next.port}"
        val local="http://192.168.1.1:${plain.port}"
        val name="transaction-${UUID.randomUUID()}.bin";val file=File(app.noBackupFilesDir,name)
        val commitEntered=CountDownLatch(1);val commitRelease=CountDownLatch(1)
        @Volatile var pauseCommit=false
        @Volatile var failCommit=false
        val store=object:HubStore(app,name) {
            override fun save(record:SavedHub) {
                if(pauseCommit && record.address==newAddress) {
                    commitEntered.countDown()
                    check(commitRelease.await(10,TimeUnit.SECONDS))
                    if(failCommit) throw IOException("Public synthetic persistence failure")
                }
                super.save(record)
            }
        };val owner=ViewModelStore()
        val connection=HubViewModel.defaultClient().newBuilder().sslSocketFactory(trusted.sslSocketFactory(),trusted.trustManager).build()
        lateinit var model:HubViewModel
        init {
            val body="""{"periods":{"today":{"totalTokens":123}},"devices":[]}"""
            store.save(SavedHub(oldAddress,oldSecret,body,1,local,
                """{"daily":[{"date":"2026-10-09","tokens":123}]}""","""{"subscriptions":[]}""",mapOf("public-old" to "Public old alias"),
                """{"ok":true,"value":{"modelAliases":{"public-old":"old"},"modelAliasGrouping":"off"}}""",
                """{"ok":true,"version":1,"revision":1,"value":[{"modelId":"public-old","inputPerM":0}]}""",
                DeviceHistoryParser.project("""{"devices":[]}""",FixedPeriods.signature(HubParser.parse(body)))))
            main {model=HubViewModel(app,store,connection);owner.put("transaction",model)}
            await {!model.state.value.loading}
            main {model.updateNetworkScope(fixtureLocalScope());model.setForeground(true)}
            await {model.state.value.live && !model.state.value.loading && store.load()?.savedAt==model.state.value.savedAt}
        }
        fun assertOld() {
            val state=model.state.value;val saved=store.load()!!
            assertEquals(listOf(oldAddress,local),model.connectionEndpoints())
            assertTrue(saved.address==oldAddress && saved.credential==oldSecret && saved.alternate==local)
            assertEquals(123L,state.snapshot!!.periods.getValue("today").tokens)
            assertEquals(123L,state.history!!.daily.single().tokens)
            assertEquals(mapOf("public-old" to "Public old alias"),state.deviceAliases)
            assertNotNull(state.modelNames);assertNotNull(state.pricing);assertNotNull(state.deviceHistories);assertNotNull(state.subscriptions)
            assertEquals(0,plain.requests.get()) // Failed HTTPS never downgrades to the saved HTTP route.
        }
        override fun close() {
            commitRelease.countDown()
            next.statsGate.get()?.countDown()
            File(app.noBackupFilesDir,name+".new").deleteRecursively()
            main {model.setForeground(false);owner.clear()}
            connection.dispatcher.cancelAll();connection.connectionPool.evictAll()
            old.close();next.close();plain.close();store.clear()
        }
    }
    @Test fun rejectedAuthenticationProtocolAndMalformedDataKeepOldBindingAndStream() {
        Peers().use {p->
            val badSecret=UUID.randomUUID().toString()
            main {p.model.connect(p.newAddress,badSecret,p.local)}
            await {!p.model.state.value.loading && p.model.state.value.connectionFailure==R.string.error_auth}
            await {p.old.streams.get()>=2 && p.model.state.value.live}
            p.assertOld();assertEquals(0,p.old.badAuthentication.get());assertEquals(0,p.next.streams.get())
            assertEquals(R.string.error_connection_change,p.model.state.value.error)
            main {p.model.dismissConnectionFailure()}
            assertNull(p.model.state.value.connectionFailure);assertNull(p.model.state.value.error)
            p.next.status.set(503)
            main {p.model.editConnection(p.newAddress,p.local,p.newSecret)}
            await {!p.model.state.value.loading && p.model.state.value.connectionFailure==R.string.error_unavailable}
            p.assertOld()
            p.next.status.set(200);p.next.statsBody.set("{invalid")
            main {p.model.editConnection(p.newAddress,p.local,p.newSecret)}
            await {!p.model.state.value.loading && p.model.state.value.connectionFailure!=null}
            p.assertOld();assertEquals(0,p.next.streams.get())
        }
    }
    @Test fun verifiedDifferentHubCommitsAllSourcesTogetherAndClearsOldAttribution() {
        Peers().use {p->
            val gate=CountDownLatch(1);p.next.statsGate.set(gate)
            val before=p.file.readBytes()
            main {p.model.connect(p.newAddress,p.newSecret,p.local)}
            assertTrue(p.next.statsEntered.await(5,TimeUnit.SECONDS))
            p.assertOld();assertTrue(before.contentEquals(p.file.readBytes()));assertTrue(p.model.state.value.loading)
            gate.countDown()
            await {!p.model.state.value.loading && p.model.state.value.live && p.store.load()?.address==p.newAddress}
            val state=p.model.state.value;val saved=p.store.load()!!
            assertEquals(listOf(p.newAddress,p.local),p.model.connectionEndpoints());assertTrue(saved.credential==p.newSecret)
            assertEquals(777L,state.snapshot!!.periods.getValue("today").tokens)
            assertNull(state.history);assertNull(state.modelNames);assertNull(state.pricing);assertNull(state.deviceHistories);assertNull(state.subscriptions);assertTrue(state.deviceAliases.isEmpty())
            assertNull(saved.history);assertNull(saved.modelNames);assertNull(saved.pricing);assertNull(saved.deviceHistory);assertNull(saved.subscriptions);assertTrue(saved.deviceAliases.isEmpty())
            assertNull(state.connectionFailure);assertEquals(0,p.plain.requests.get())
        }
    }
    @Test fun backgroundCancelsPendingVerificationAndLateReplyCannotReplaceSavedHub() {
        Peers().use {p->
            val gate=CountDownLatch(1);p.next.statsGate.set(gate);val before=p.file.readBytes()
            main {p.model.connect(p.newAddress,p.newSecret,p.local)}
            assertTrue(p.next.statsEntered.await(5,TimeUnit.SECONDS))
            main {p.model.setForeground(false)}
            await {!p.model.state.value.loading && p.model.state.value.connectionFailure==R.string.error_connection_interrupted}
            val reads=p.old.requests.get()+p.next.requests.get()+p.plain.requests.get()
            gate.countDown();Thread.sleep(400)
            assertEquals(reads,p.old.requests.get()+p.next.requests.get()+p.plain.requests.get())
            assertTrue(before.contentEquals(p.file.readBytes()));p.assertOld();assertFalse(p.model.state.value.live)
            main {p.model.setForeground(true)}
            await {p.old.streams.get()>=2 && p.model.state.value.live}
            p.assertOld();assertEquals(0,p.next.streams.get());assertNotNull(p.model.state.value.connectionFailure)
        }
    }
    @Test fun atomicWriteFailureKeepsOldBindingAndCorrectedRetryCanCommit() {
        Peers().use {p->
            val before=p.file.readBytes();val obstruction=File(app.noBackupFilesDir,p.name+".new")
            assertTrue(obstruction.mkdir());File(obstruction,"public-obstruction").writeText("fixture")
            main {p.model.connect(p.newAddress,p.newSecret,p.local)}
            await {!p.model.state.value.loading && p.model.state.value.connectionFailure==R.string.error_data_save}
            p.assertOld();assertTrue(before.contentEquals(p.file.readBytes()));assertEquals(0,p.next.streams.get())
            assertTrue(obstruction.deleteRecursively())
            main {p.model.connect(p.newAddress,p.newSecret,p.local)}
            await {!p.model.state.value.loading && p.model.state.value.live && p.store.load()?.address==p.newAddress}
            assertEquals(777L,p.model.state.value.snapshot!!.periods.getValue("today").tokens)
            assertTrue(p.store.load()?.credential==p.newSecret)
        }
    }
    @Test fun alternateOnlyEditRetainsDetailsAndCredentialReplacementClearsOldAccountData() {
        Peers().use {p->
            val alternate="http://192.168.1.2:${p.plain.port}"
            main {p.model.editConnection(p.oldAddress,alternate,null)}
            await {!p.model.state.value.loading && p.store.load()?.alternate==alternate && p.model.state.value.live}
            val retained=p.store.load()!!
            assertTrue(retained.credential==p.oldSecret);assertNotNull(retained.history);assertNotNull(retained.modelNames)
            assertEquals(mapOf("public-old" to "Public old alias"),retained.deviceAliases)
            p.old.acceptedCredential.set(p.newSecret)
            p.old.statsBody.set("""{"periods":{"today":{"totalTokens":888}},"devices":[]}""")
            main {p.model.editConnection(p.oldAddress,alternate,p.newSecret)}
            await {!p.model.state.value.loading && p.store.load()?.credential==p.newSecret && p.model.state.value.live}
            val state=p.model.state.value;val saved=p.store.load()!!
            assertEquals(888L,state.snapshot!!.periods.getValue("today").tokens)
            assertNull(state.history);assertNull(state.modelNames);assertNull(state.pricing);assertNull(state.subscriptions);assertNull(state.deviceHistories);assertTrue(state.deviceAliases.isEmpty())
            assertNull(saved.history);assertNull(saved.modelNames);assertNull(saved.pricing);assertNull(saved.subscriptions);assertNull(saved.deviceHistory);assertTrue(saved.deviceAliases.isEmpty())
            assertEquals(0,p.plain.requests.get())
        }
    }

    @Test fun localCommitDefersNetworkReadsAcrossBackgroundAndDefaultNetworkChange() {
        Peers().use {p->
            p.pauseCommit=true
            main {p.model.connect(p.newAddress,p.newSecret,p.local)}
            assertTrue(p.commitEntered.await(5,TimeUnit.SECONDS))
            p.assertOld()
            main {p.model.setForeground(false);p.model.updateNetworkScope(HubNetworkScope(false,false,emptyList(),networkId=2L));p.model.setForeground(true)}
            val reads=p.old.requests.get()+p.next.requests.get();Thread.sleep(250)
            assertEquals(reads,p.old.requests.get()+p.next.requests.get());assertTrue(p.model.state.value.loading)
            p.commitRelease.countDown()
            await {!p.model.state.value.loading && p.model.state.value.live && p.store.load()?.address==p.newAddress}
            assertEquals(777L,p.model.state.value.snapshot!!.periods.getValue("today").tokens)
            assertEquals(1,p.old.streams.get());assertEquals(1,p.next.streams.get());assertEquals(0,p.plain.requests.get())
        }
    }
    @Test fun failedLocalCommitAfterNetworkChangeRestoresAndRevalidatesOnlyOldBinding() {
        Peers().use {p->
            p.pauseCommit=true;p.failCommit=true
            main {p.model.connect(p.newAddress,p.newSecret,p.local)}
            assertTrue(p.commitEntered.await(5,TimeUnit.SECONDS))
            main {p.model.updateNetworkScope(HubNetworkScope(false,false,emptyList(),networkId=2L))}
            val reads=p.old.requests.get()+p.next.requests.get();Thread.sleep(150)
            assertEquals(reads,p.old.requests.get()+p.next.requests.get())
            p.commitRelease.countDown()
            await {!p.model.state.value.loading && p.model.state.value.live && p.model.state.value.connectionFailure==R.string.error_data_save}
            p.assertOld();assertEquals(0,p.next.streams.get());assertEquals(0,p.old.badAuthentication.get())
        }
    }
    @Test fun firstConnectionNetworkChangeCancelsValidationWithoutSavingCandidate() {
        Peers().use {p->
            main {p.model.forget()};await {!p.model.state.value.loading && p.store.load()==null}
            val gate=CountDownLatch(1);p.next.statsGate.set(gate)
            main {p.model.connect(p.newAddress,p.newSecret,p.local)}
            assertTrue(p.next.statsEntered.await(5,TimeUnit.SECONDS))
            main {p.model.updateNetworkScope(HubNetworkScope(false,false,emptyList(),networkId=2L))}
            await {!p.model.state.value.loading && p.model.state.value.connectionFailure==R.string.error_connection_interrupted}
            gate.countDown();Thread.sleep(250)
            assertNull(p.store.load());assertNull(p.model.state.value.snapshot);assertTrue(p.model.connectionEndpoints().isEmpty());assertEquals(0,p.next.streams.get())
        }
    }
    @Test fun authenticationFailureForTheUnchangedCurrentBindingStillBlocksAutomaticRetries() {
        Peers().use {p->
            p.old.acceptedCredential.set(UUID.randomUUID().toString())
            main {p.model.editConnection(p.oldAddress,p.local,null)}
            await {!p.model.state.value.loading && p.model.state.value.error==R.string.error_auth}
            val reads=p.old.requests.get()
            main {p.model.dismissConnectionFailure();p.model.setForeground(false);p.model.setForeground(true);p.model.updateNetworkScope(HubNetworkScope(false,false,emptyList(),networkId=2L))}
            Thread.sleep(250)
            assertEquals(reads,p.old.requests.get());assertFalse(p.model.state.value.live);assertEquals(R.string.error_auth,p.model.state.value.error);p.assertOld()
        }
    }

}
