package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.data.*
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class GatewayRecoveryTest {
    class Wire : WebSocket.Factory {
        val sockets = CopyOnWriteArrayList<Socket>()
        override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket = Socket(request,listener).also(sockets::add)
    }
    class Socket(private val req:Request,val listener:WebSocketListener):WebSocket {
        val frames=CopyOnWriteArrayList<JSONObject>()
        var cancelled=false
        override fun request()=req
        override fun queueSize()=0L
        override fun send(text:String):Boolean {frames+=JSONObject(text);return !cancelled}
        override fun send(bytes:ByteString)=!cancelled
        override fun close(code:Int,reason:String?):Boolean {cancelled=true;return true}
        override fun cancel(){cancelled=true}
        fun ready(){listener.onMessage(this,"""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"heartbeat":false}}}""")}
        fun failed(){listener.onFailure(this,java.io.IOException("network lost"),null)}
    }
    class Events:HermesGatewayListener {
        val ready=AtomicInteger()
        val auth=AtomicInteger()
        val states=CopyOnWriteArrayList<GatewayConnectionState>()
        override fun onGatewayReady(payload:JSONObject){ready.incrementAndGet()}
        override fun onConnectionState(state:GatewayConnectionState,error:String?){states+=state}
        override fun onAuthenticationExpired(){auth.incrementAndGet()}
    }
    private fun client(w:Wire,e:Events,ticket:suspend()->String?={null},deadline:Long=5000)=HermesGatewayClient(
        GatewayConfig(baseUrl="https://gateway.example",authMode=GatewayAuthMode.NONE),e,ticket,
        socketFactory=w,reconnectDelayMillis={10L},handshakeTimeoutMillis=deadline,clockMillis={System.nanoTime()/1_000_000}
    )
    private suspend fun until(condition:()->Boolean)=withTimeout(3000){while(!condition())delay(5)}

    @Test fun ticketFailureRetriesAndRecoveryClearsFailure()=runBlocking {
        val w=Wire();val e=Events();val tickets=AtomicInteger()
        val c=client(w,e,{if(tickets.incrementAndGet()==1)throw java.io.IOException("offline") else "fresh"})
        try{c.connect();until{w.sockets.size==1};w.sockets[0].ready();until{e.ready.get()==1};assertEquals(2,tickets.get());assertEquals(GatewayConnectionState.CONNECTED,e.states.last())}finally{c.close()}
    }
    @Test fun backgroundDoesNotReconnectAndResumeMintsNewTicket()=runBlocking {
        val w=Wire();val e=Events();val tickets=AtomicInteger();val c=client(w,e,{"ticket-${tickets.incrementAndGet()}"})
        try{
            c.connect();until{w.sockets.size==1};val old=w.sockets[0];old.ready();c.setEnvironment(false,true)
            old.failed();delay(100);assertEquals(1,w.sockets.size);assertTrue(old.cancelled)
            c.setEnvironment(true,true);until{w.sockets.size==2};w.sockets[1].ready()
            assertEquals(2,tickets.get());assertNotEquals(old.request().url,w.sockets[1].request().url)
            old.ready();assertEquals(2,e.ready.get())
        }finally{c.close()}
    }
    @Test fun networkLossWaitsAndNewNetworkInvalidatesOldSocket()=runBlocking {
        val w=Wire();val e=Events();val c=client(w,e)
        try{c.connect();until{w.sockets.size==1};c.setEnvironment(true,false);delay(70);assertEquals(1,w.sockets.size)
            c.setEnvironment(true,true,true);until{w.sockets.size==2};repeat(10){c.setEnvironment(true,true)};assertEquals(2,w.sockets.size)
        }finally{c.close()}
    }
    @Test fun concurrentRetiredCallbacksCannotPoisonReplacement()=runBlocking {
        val w=Wire();val e=Events();val c=client(w,e)
        try {
            c.connect();until{w.sockets.size==1};val old=w.sockets[0];old.ready()
            c.setEnvironment(false,true);c.setEnvironment(true,true);until{w.sockets.size==2}
            coroutineScope {
                launch(Dispatchers.Default){repeat(100){old.failed();old.ready()}}
                launch(Dispatchers.Default){w.sockets[1].ready()}
            }
            assertFalse(w.sockets[1].cancelled);assertEquals(2,w.sockets.size)
            assertEquals(GatewayConnectionState.CONNECTED,e.states.last());assertEquals(2,e.ready.get())
        } finally {c.close()}
    }
    @Test fun missingReadyHandshakeRecovers()=runBlocking {
        val w=Wire();val e=Events();val c=client(w,e,deadline=60)
        try{c.connect();until{w.sockets.size>=2};assertTrue(w.sockets[0].cancelled);w.sockets.last().ready();until{e.ready.get()==1}}finally{c.close()}
    }
    @Test fun terminalAuthenticationStopsRetries()=runBlocking {
        val w=Wire();val e=Events();val tickets=AtomicInteger();val c=client(w,e,{tickets.incrementAndGet();throw HermesHttpException(401,"expired")})
        try{c.connect();until{e.auth.get()==1};delay(100);c.setEnvironment(false,true);c.setEnvironment(true,true);delay(70);assertEquals(1,tickets.get());assertTrue(w.sockets.isEmpty())}finally{c.close()}
    }
    @Test fun rejectedSocketTicketGetsOneFreshTicketBeforeReauthentication()=runBlocking {
        val w=Wire();val e=Events();val tickets=AtomicInteger();val c=client(w,e,{"ticket-${tickets.incrementAndGet()}"})
        try {
            c.connect();until{w.sockets.size==1}
            val first=w.sockets[0]
            first.listener.onFailure(first,java.io.IOException("ticket expired"),Response.Builder().request(first.request()).protocol(Protocol.HTTP_1_1).code(401).message("Expired ticket").build())
            until{w.sockets.size==2};assertEquals(0,e.auth.get());assertEquals(2,tickets.get())
            w.sockets[1].ready();assertEquals(GatewayConnectionState.CONNECTED,e.states.last())
        } finally {c.close()}
    }
    @Test fun disconnectInvalidatesInFlightTicket()=runBlocking {
        val w=Wire();val e=Events();val ticket=CompletableDeferred<String>();val c=client(w,e,{ticket.await()})
        try{c.connect();delay(20);c.disconnect();ticket.complete("stale");delay(70);assertTrue(w.sockets.isEmpty());assertEquals(GatewayConnectionState.DISCONNECTED,e.states.last())}finally{c.close()}
    }
    @Test fun droppedGenerationFailsPendingRpc()=runBlocking {
        val w=Wire();val e=Events();val c=client(w,e)
        try{c.connect();until{w.sockets.size==1};w.sockets[0].ready();val pending=async{runCatching{c.requestObject("session.resume",JSONObject().put("session_id","owned"))}}
            until{w.sockets[0].frames.any{it.optString("method")=="session.resume"}};c.setEnvironment(false,true);assertTrue(pending.await().isFailure)
        }finally{c.close()}
    }
    @Test fun strictCompatibilityRemovesOnlyRejectedOptionalAndKeepsOwnership()=runBlocking {
        val w=Wire();val e=Events();val c=client(w,e)
        try{
            c.connect();until{w.sockets.size==1};val socket=w.sockets[0];socket.ready();val compat=GatewayCompatibility(c)
            val pending=async{compat.requestObject("session.resume",JSONObject().put("session_id","owned").put("profile","work"),linkedMapOf("inline_images" to false,"source" to "mobile"))}
            until{socket.frames.any{it.optString("method")=="session.resume"}};val first=socket.frames.last{it.optString("method")=="session.resume"}
            socket.listener.onMessage(socket,JSONObject().put("id",first.optString("id")).put("error",JSONObject().put("code",4000).put("message","inline_images: Extra inputs are not permitted")).toString())
            until{socket.frames.count{it.optString("method")=="session.resume"}==2};val second=socket.frames.last()
            assertFalse(second.getJSONObject("params").has("inline_images"));assertEquals("owned",second.getJSONObject("params").getString("session_id"));assertEquals("work",second.getJSONObject("params").getString("profile"));assertEquals("mobile",second.getJSONObject("params").getString("source"))
            socket.listener.onMessage(socket,JSONObject().put("id",second.getString("id")).put("result",JSONObject().put("ok",true)).toString());assertTrue(pending.await().getBoolean("ok"))
        }finally{c.close()}
    }
}
