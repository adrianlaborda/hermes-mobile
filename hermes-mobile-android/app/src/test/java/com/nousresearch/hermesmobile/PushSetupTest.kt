package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.push.FirebaseConfigException
import com.nousresearch.hermesmobile.push.FirebaseRuntime
import com.nousresearch.hermesmobile.push.PushSetup
import com.nousresearch.hermesmobile.push.ServerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PushSetupTest {
    private val googleServices = """{"project_info":{"project_number":"1081","project_id":"demo-proj"},
        "client":[
          {"client_info":{"mobilesdk_app_id":"1:1081:android:other","android_client_info":{"package_name":"org.other.app"}},"api_key":[{"current_key":"KEY-OTHER"}]},
          {"client_info":{"mobilesdk_app_id":"1:1081:android:mine","android_client_info":{"package_name":"io.example.hermes"}},"api_key":[{"current_key":"KEY-MINE"}]}
        ]}"""

    @Test
    fun picksTheClientRegisteredForThisPackage() {
        val config = FirebaseRuntime.parseGoogleServices(googleServices, "io.example.hermes")
        assertEquals("1:1081:android:mine", config.appId)
        assertEquals("KEY-MINE", config.apiKey)
        assertEquals("demo-proj", config.projectId)
        assertEquals("1081", config.senderId)
    }

    @Test
    fun explainsWrongFiles() {
        fun code(json: String, pkg: String = "io.example.hermes") =
            assertThrows(FirebaseConfigException::class.java) { FirebaseRuntime.parseGoogleServices(json, pkg) }.message
        assertEquals("package_missing", code(googleServices, "io.example.unregistered"))
        assertEquals("service_account", code("""{"type":"service_account","project_id":"p"}"""))
        assertEquals("not_google_services", code("""{"hello":1}"""))
        assertEquals("not_json", code("nope"))
    }

    @Test
    fun readsPairingRepliesAndStates() {
        val ready = PushSetup.parsePairing("""{"hermes_android_pairing":1,"profile":"default","plugin_version":"0.4.0","ready":true,
            "secret":"s-0123456789abcdefghijklmnopqrstuvwxyz","api_server":{"host":"0.0.0.0","port":8642,"reachable_from_phone":true},
            "server_key":{"state":"ok","project_id":"demo-proj"}}""")
        assertEquals(ServerState.READY, PushSetup.state(ready, "default"))
        assertEquals(ServerState.WRONG_PROFILE, PushSetup.state(ready, "work"))
        assertEquals("demo-proj", ready.keyProject)
        // The phone reaches the API server on the chat host, whatever address it is bound to.
        assertEquals("http://192.168.1.5:8642", PushSetup.apiBase("http://192.168.1.5:9119", ready))

        val off = PushSetup.parsePairing("""{"hermes_android_pairing":1,"profile":"default","ready":false,"problem":"api_server_disabled",
            "api_server":{"host":"127.0.0.1","port":8642,"reachable_from_phone":false}}""")
        assertEquals(ServerState.API_SERVER_OFF, PushSetup.state(off, "default"))
        val loopback = off.copy(problem = "")
        assertEquals(ServerState.API_SERVER_LOOPBACK, PushSetup.state(loopback, "default"))
        assertEquals(ServerState.SECRET_MISSING, PushSetup.state(ready.copy(ready = false, secret = ""), "default"))
        assertThrows(IllegalArgumentException::class.java) { PushSetup.parsePairing("Unknown command") }
    }

    @Test
    fun hostCandidatesAskTheLaunchProfileFirst() {
        assertEquals(listOf("default", "work"), PushSetup.hostCandidates(listOf("seo", "work", "default"), "seo"))
        assertEquals(listOf("seo"), PushSetup.hostCandidates(listOf("default", "seo"), "default"))
    }
}
