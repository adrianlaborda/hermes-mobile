package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.data.*
import com.nousresearch.hermesmobile.features.jsonEquivalent
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PolicyTest {
    @Test fun oauthCallbackLineIsBoundedBeforeAllocation(){
        val valid = "GET /callback?state=s&code=c HTTP/1.1\r\nignored".byteInputStream()
        assertEquals("GET /callback?state=s&code=c HTTP/1.1",readLoopbackRequestLine(valid))
        assertTrue(runCatching { readLoopbackRequestLine("x".repeat(9000).byteInputStream()) }.isFailure)
    }

    @Test fun privateAddressPolicyAcceptsOnlyLiteralsAndLocalNames(){
        listOf("10.1.2.3","172.16.1.2","192.168.1.4","::1","fd00::1","fe80::1","hermes.local").forEach{assertTrue(it,GatewayUrlPolicy.isPrivateOrLocalHost(it))}
        listOf("fcpayments.example","fdaa.example","fe80.example","10.foo.1.2.3","172.32.0.1","8.8.8.8","2001:4860::8888","256.1.1.1").forEach{assertFalse(it,GatewayUrlPolicy.isPrivateOrLocalHost(it))}
    }
    @Test fun credentialsAndPublicCleartextAreRejected(){
        listOf("http://fcpayments.example","http://8.8.8.8","https://user:password@example.com","https://example.com?token=secret").forEach{assertTrue(it,runCatching{GatewayUrlPolicy.canonical(it)}.isFailure)}
        assertEquals("https://example.com",GatewayUrlPolicy.canonical("example.com"))
    }
    @Test fun backoffIsBoundedAndJittered(){
        for(attempt in listOf(0,1,5,100,Int.MAX_VALUE)){
            val low=ReconnectPolicy.delayMillis(attempt,0.0);val high=ReconnectPolicy.delayMillis(attempt,1.0)
            assertTrue(low>=500);assertTrue(high<=30_000);assertTrue(high>low)
        }
    }
    @Test fun configConflictComparisonPreservesNestedSlotDetails(){
        val a=JSONObject("""{"presets":{"a":{"aggregator":{"provider":"p","model":"m","reasoning_effort":"high"},"reference_models":[{"enabled":false,"model":"r"}],"reference_timeout":null}}}""")
        val b=JSONObject(a.toString());assertTrue(jsonEquivalent(a,b));b.getJSONObject("presets").getJSONObject("a").getJSONObject("aggregator").put("reasoning_effort","low");assertFalse(jsonEquivalent(a,b))
    }
    @Test fun rotatedTokenExpiresWithSkew(){
        val tokens=NativeTokenSet(accessToken="test",refreshToken="test",expiresAt=1000)
        assertFalse(tokens.needsRefresh(900));assertTrue(tokens.needsRefresh(950))
    }
}
