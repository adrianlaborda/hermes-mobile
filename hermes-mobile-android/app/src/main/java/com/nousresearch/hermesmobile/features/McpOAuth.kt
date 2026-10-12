package com.nousresearch.hermesmobile.features

import android.content.Intent
import com.nousresearch.hermesmobile.data.readLoopbackRequestLine
import com.nousresearch.hermesmobile.data.GatewayUrlPolicy
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.net.InetAddress
import java.net.ServerSocket
import org.json.JSONObject

@Composable
fun McpOAuthButton(c:ParityController,name:String){
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val scope=rememberCoroutineScope()
    var job by remember(c,name){mutableStateOf<Job?>(null)}
    var status by remember(c,name){mutableStateOf("")}
    DisposableEffect(c,name){onDispose{job?.cancel()}}
    TextButton(onClick={
        if(job?.isActive==true){job?.cancel();return@TextButton}
        job=scope.launch{
            val server=withContext(Dispatchers.IO){ServerSocket(0,4,InetAddress.getByName("127.0.0.1"))}
            var flow=""
            try{
                status="Starting…"
                val result=c.repo.gateway.requestObject("mcp.servers.oauth.start",json("name" to name,"profile" to c.profile,"client_redirect_uri" to "http://127.0.0.1:${server.localPort}/callback"),60_000)
                flow=result.optString("session_id")
                val auth=Uri.parse(result.optString("auth_url"));require(auth.scheme=="https" || (auth.scheme=="http" && GatewayUrlPolicy.isPrivateOrLocalHost(auth.host.orEmpty()))){"Unsupported authorization URL"}
                val expectedState=auth.getQueryParameter("state");require(!expectedState.isNullOrBlank()){"OAuth authorization has no state"}
                context.startActivity(Intent(Intent.ACTION_VIEW,auth));status="Complete sign-in in your browser"
                val callback=withTimeout(180_000){withContext(Dispatchers.IO){
                    server.soTimeout=1000
                    var params:JSONObject?=null
                    while(params==null){
                        ensureActive()
                        val client=try{server.accept()}catch(_:java.net.SocketTimeoutException){continue}
                        client.use{socket->socket.soTimeout=3000
                            val line=try { readLoopbackRequestLine(socket.getInputStream()) } catch (_:java.io.IOException) { return@use } catch (_:IllegalArgumentException) { return@use }
                            val uri=Uri.parse("http://127.0.0.1"+line.split(' ').getOrNull(1).orEmpty())
                            val valid=line.startsWith("GET ") && uri.path=="/callback" && uri.getQueryParameter("state")==expectedState
                            val message=if(valid)"Return to Hermes to finish sign-in." else "Invalid OAuth callback."
                            socket.getOutputStream().write("HTTP/1.1 ${if(valid)"200 OK" else "400 Bad Request"}\r\nContent-Type: text/plain\r\nContent-Length: ${message.length}\r\nConnection: close\r\n\r\n$message".toByteArray())
                            if(valid)params=json("name" to name,"profile" to c.profile,"session_id" to flow).apply{listOf("code","state","error","iss").forEach{k->uri.getQueryParameter(k)?.let{put(k,it)}}}
                        }
                    };params
                }}
                lifecycle.currentStateFlow.first{it.isAtLeast(Lifecycle.State.STARTED)}
                c.repo.gateway.awaitReady()
                c.repo.gateway.requestObject("mcp.servers.oauth.callback",callback,60_000)
                withTimeout(60_000){
                    while(true){
                        lifecycle.currentStateFlow.first{it.isAtLeast(Lifecycle.State.STARTED)}
                        val poll=c.repo.gateway.requestObject("mcp.servers.oauth.poll",json("name" to name,"profile" to c.profile,"session_id" to flow),10_000)
                        status=poll.optString("status")
                        if(status=="approved")break
                        check(status!="error"){poll.optString("error_message","MCP sign-in failed")}
                        delay(2000)
                    }
                }
            }catch(e:CancellationException){status="Cancelled";throw e}catch(e:Throwable){status=e.message ?: "OAuth failed"}
            finally{server.close();if(flow.isNotBlank() && status!="approved")withContext(NonCancellable){withTimeoutOrNull(5000){runCatching{c.repo.gateway.requestObject("mcp.servers.oauth.cancel",json("name" to name,"profile" to c.profile,"session_id" to flow),4000)}}}}
        }
    }){Text("$name · ${if(job?.isActive==true)"Cancel sign-in" else "Connect OAuth"}${if(status.isNotBlank())" · $status" else ""}")}
}
