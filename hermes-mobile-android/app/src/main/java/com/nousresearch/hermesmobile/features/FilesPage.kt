package com.nousresearch.hermesmobile.features

import com.nousresearch.hermesmobile.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ExportedFile(val file: File, val mime: String, val displayName: String)

object MobileFiles {
    fun directory(context:Context):File = File(context.cacheDir,"exports").apply {mkdirs()}
    fun clean(context:Context) { directory(context).listFiles()?.filter {System.currentTimeMillis()-it.lastModified()>24*60*60*1000L}?.forEach {it.delete()} }
    fun target(context:Context,name:String):File {
        val safe=name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._ -]"),"_").takeLast(110).ifBlank{"artifact"}
        return File(directory(context),"${UUID.randomUUID()}-$safe")
    }
    fun share(context:Context,export:ExportedFile,open:Boolean) {
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",export.file)
        val intent=Intent(if(open) Intent.ACTION_VIEW else Intent.ACTION_SEND).apply {
            if(open) setDataAndType(uri,export.mime) else {type=export.mime;putExtra(Intent.EXTRA_STREAM,uri)}
            clipData=ClipData.newRawUri("Hermes file",uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent,if(open)"Open artifact" else "Share artifact"))
    }
    suspend fun import(context:Context,uri:Uri):Pair<File,String> = withContext(Dispatchers.IO) {
        var name="upload"
        context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {cursor->if(cursor.moveToFirst())name=cursor.getString(0)}
        val file=target(context,name)
        try {
            context.contentResolver.openInputStream(uri)?.use {source->file.outputStream().use {sink->
                val buffer=ByteArray(32*1024);var total=0L
                while(true){currentCoroutineContext().ensureActive();val n=source.read(buffer);if(n<0)break;total+=n;require(total<=64L*1024*1024){"Mobile upload limit: 64 MB"};sink.write(buffer,0,n)}
            }} ?: error("Document could not be opened")
            require(name.substringAfterLast('/').substringAfterLast('\\') !in listOf(".","..","")) { "Invalid document name" }
            file to name.substringAfterLast('/').substringAfterLast('\\')
        }catch(t:Throwable){file.delete();throw t}
    }
}

@Composable
fun FilesPage(c:ParityController,initialPath:String?=null,sessionId:String?=null,initialDirectory:String?=null) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var path by remember(c){mutableStateOf("")}
    var managed by remember(c){mutableStateOf(initialPath==null && initialDirectory==null)}
    var listing by remember(c){mutableStateOf<JSONObject?>(null)}
    var preview by remember(c){mutableStateOf<JSONObject?>(null)}
    var exported by remember(c){mutableStateOf<ExportedFile?>(null)}
    var folder by remember{mutableStateOf("")}
    var showFolder by remember{mutableStateOf(false)}
    var requestGeneration by remember(c){mutableLongStateOf(0L)}
    fun browse(next:String,useManaged:Boolean=managed) {
        if(c.busy)return
        val generation=++requestGeneration
        c.action(null){
            val result=if(useManaged)c.api("/api/files"+(if(next.isBlank())"" else "?path=${encoded(next)}")) else c.api("/api/fs/list?path=${encoded(next)}")
            check(!result.has("error")){result.optString("error")}
            if(generation==requestGeneration){listing=result;path=result.optString("path",next);managed=useManaged;preview=null;exported=null}
            result
        }
    }
    suspend fun refreshListing() {
        if(listing == null)return
        listing = if(managed)c.api("/api/files?path=${encoded(path)}") else c.api("/api/fs/list?path=${encoded(path)}")
    }
    suspend fun readPreview(remote:String):JSONObject {
        if(!managed)return c.api("/api/fs/read-text?path=${encoded(remote)}")
        val known = listing?.rows("entries")?.firstOrNull { it.optString("path") == remote }
        if (known != null && known.optLong("size") > 2L * 1024 * 1024) {
            return json("path" to remote,"binary" to true,"byteSize" to known.optLong("size"),"mimeType" to known.optString("mime_type","application/octet-stream"))
        }
        val result=c.api("/api/files/read?path=${encoded(remote)}")
        return withContext(Dispatchers.Default) {
            val data=result.optString("data_url")
            require(";base64," in data && data.length<=48*1024*1024) { "Unsupported file preview" }
            val bytes=java.util.Base64.getDecoder().decode(data.substringAfter(','))
            val mime=result.optString("mime_type","application/octet-stream")
            val binary=mime.startsWith("image/") || mime.startsWith("audio/") || mime.startsWith("video/") || mime=="application/pdf" || bytes.take(4096).any { it==0.toByte() }
            val limit=minOf(bytes.size,256*1024)
            json("path" to remote,"mimeType" to mime,"byteSize" to bytes.size,"binary" to binary,"truncated" to (limit<bytes.size),"text" to if(binary)"" else String(bytes,0,limit,Charsets.UTF_8))
        }
    }
    fun openFile(remote:String) {
        if(c.busy)return
        val generation=++requestGeneration
        c.action(null){
            // Validate session ownership first; only routes declaring session_id receive it.
            if(sessionId!=null)c.repo.rest.sessionMessagesPage(sessionId,c.profile,limit=1)
            val result=try { readPreview(remote) }
            catch(e:com.nousresearch.hermesmobile.data.HermesHttpException) {
                if(e.status !in listOf(413,415)) throw e
                json("binary" to true,"path" to remote,"mimeType" to "application/octet-stream")
            }
            if(generation==requestGeneration){preview=JSONObject(result.toString()).put("path",remote);exported=null}
            result
        }
    }
    fun download(remote:String,then:(ExportedFile)->Unit={}) {
        c.action(null){
            val file=MobileFiles.target(context,remote)
            val mime=c.repo.rest.downloadFile(c.profile,remote,sessionId,file,managed)
            ExportedFile(file,mime,remote.substringAfterLast('/').ifBlank { "artifact" }).also{exported=it;then(it)}
            json("ok" to true)
        }
    }
    LaunchedEffect(c,initialPath,initialDirectory){withContext(Dispatchers.IO){MobileFiles.clean(context)};if(initialPath!=null)openFile(initialPath)else browse(initialDirectory.orEmpty())}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->
        val file=exported?.file
        if(uri!=null && file!=null)scope.launch{runCatching{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{out->file.inputStream().use{it.copyTo(out)}} ?: error("Could not save document")}}.onFailure{c.message=it.message}}
    }
    val upload=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){
        val parent=path
        c.action(null){
            val (file,name)=MobileFiles.import(context,uri)
            try{c.repo.rest.uploadFile(c.profile,parent.trimEnd('/')+"/"+name,file,context.contentResolver.getType(uri) ?: "application/octet-stream").also { refreshListing() }}finally{file.delete()}
        }
    }}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{FeatureCard(stringResource(R.string.ui_abc7e98928), "Profile: ${c.profile}"+(sessionId?.let{" · Session: ${it.take(12)}"} ?: "")){
            Row{
                TextButton(enabled=!c.busy,onClick={browse("",true)}){Text(stringResource(R.string.ui_19d199c7e1))}
                TextButton(enabled=!c.busy,onClick={c.action(null){val cwd=c.api("/api/fs/default-cwd").optString("cwd");val result=c.api("/api/fs/list?path=${encoded(cwd)}");listing=result;path=cwd;managed=false;preview=null;result}}){Text(stringResource(R.string.ui_87bb59ba2f))}
            }
            Text(stringResource(if(managed) R.string.a14_managed_files else R.string.a14_workspace_files),style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(path,{path=it},label={Text(stringResource(R.string.ui_23a2e8047d))},modifier=Modifier.fillMaxWidth())
            Row{TextButton(enabled=!c.busy,onClick={browse(path)}){Text(stringResource(R.string.ui_6cc8519b91))};TextButton(enabled=managed && !c.busy,onClick={showFolder=true}){Text(stringResource(R.string.ui_cf28f49ec5))};TextButton(enabled=managed && path.isNotBlank() && !c.busy,onClick={upload.launch(arrayOf("*/*"))}){Text(stringResource(R.string.ui_865e89de78))}}
        }}
        val p=preview
        if(p!=null){
            item{FeatureCard(p.optString("path").substringAfterLast('/'),"${p.optLong("byteSize")} bytes · ${p.optString("mimeType")}"){
                if(!p.optBoolean("binary")){
                    var text by remember(p){mutableStateOf(p.optString("text"))}
                    val truncated=p.optBoolean("truncated")
                    if(truncated)Text(stringResource(R.string.ui_c39d0fc75b))
                    OutlinedTextField(text,{text=it},readOnly=truncated,modifier=Modifier.fillMaxWidth().heightIn(min=180.dp,max=380.dp))
                    TextButton(enabled=!truncated && !c.busy && text!=p.optString("text"),onClick={val captured=text;c.action(null){val latest=readPreview(p.optString("path"));check(latest.optString("text")==p.optString("text") && !latest.optBoolean("truncated")){"File changed on the gateway. Reopen before saving."};(if(managed)c.api("/api/files/upload","POST",json("path" to p.optString("path"),"data_url" to "data:text/plain;base64,"+java.util.Base64.getEncoder().encodeToString(captured.toByteArray(Charsets.UTF_8)),"overwrite" to true)) else c.api("/api/fs/write-text","POST",json("path" to p.optString("path"),"content" to captured))).also{preview=JSONObject(p.toString()).put("text",captured)}}}){Text(stringResource(R.string.ui_793017feec))}
                }else Text(stringResource(R.string.ui_c2236b2c86))
                val bitmap by produceState<android.graphics.Bitmap?>(null,exported){
                    value=withContext(Dispatchers.IO){exported?.takeIf{it.mime.startsWith("image/") && it.file.length()<16*1024*1024}?.let{e->
                        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(e.file.path,bounds)
                        val options=BitmapFactory.Options().apply{inSampleSize=((maxOf(bounds.outWidth,bounds.outHeight)+1599)/1600).coerceAtLeast(1)}
                        BitmapFactory.decodeFile(e.file.path,options)
                    }}
                }
                exported?.let { DocumentPreview(it) }
                bitmap?.let{Image(it.asImageBitmap(),"File preview",Modifier.fillMaxWidth().heightIn(max=300.dp))}
                Row{
                    TextButton(enabled=!c.busy,onClick={download(p.optString("path"))}){Text(stringResource(R.string.ui_d6eafe8235))}
                    TextButton(enabled=!c.busy,onClick={download(p.optString("path")){save.launch(it.displayName)}}){Text(stringResource(R.string.ui_06359c03b0))}
                }
                Row{
                    TextButton(enabled=!c.busy,onClick={download(p.optString("path")){MobileFiles.share(context,it,true)}}){Text(stringResource(R.string.ui_ed077f3d81))}
                    TextButton(enabled=!c.busy,onClick={download(p.optString("path")){MobileFiles.share(context,it,false)}}){Text(stringResource(R.string.ui_29887a5ff9))}
                    TextButton(enabled=managed && !c.busy,onClick={c.confirm("Permanently delete ${p.optString("path")}?"){c.api("/api/files","DELETE",json("path" to p.optString("path"),"recursive" to false)).also{preview=null;exported=null;refreshListing()}}}){Text(stringResource(R.string.ui_e2d0a54968))}
                }
            }}
        }
        val parent=listing?.optString("parent")?.takeUnless{it=="null" || it.isBlank()} ?: if(!managed)path.substringBeforeLast('/',"").takeIf{it.isNotBlank()}else null
        if(parent!=null)item{TextButton(onClick={browse(parent)},enabled=!c.busy){Icon(Icons.Default.FolderOpen,null);Text(stringResource(R.string.ui_77a245c653))}}
        items(listing?.rows("entries").orEmpty(),key={it.optString("path")}){entry->
            val directory=entry.optBoolean("is_directory",entry.optBoolean("isDirectory"))
            OutlinedCard(onClick={if(!c.busy){if(directory)browse(entry.optString("path"))else openFile(entry.optString("path"))}},modifier=Modifier.fillMaxWidth()){
                Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Icon(if(directory)Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,null)
                    Column(Modifier.weight(1f)){Text(entry.optString("name"));if(entry.has("size"))Text("${entry.optLong("size")} bytes",style=MaterialTheme.typography.labelSmall)}
                    if(directory && managed)IconButton(enabled=!c.busy,onClick={c.confirm("Delete empty folder ${entry.optString("name")}?"){c.api("/api/files","DELETE",json("path" to entry.optString("path"),"recursive" to false)).also { refreshListing() }}}){Icon(Icons.Default.DeleteOutline,"Delete empty folder")}
                }
            }
        }
    }
    if(showFolder)AlertDialog(onDismissRequest={showFolder=false},title={Text(stringResource(R.string.ui_cf28f49ec5))},text={OutlinedTextField(folder,{folder=it},label={Text(stringResource(R.string.ui_14d34edf50))})},confirmButton={TextButton(enabled=folder.isNotBlank() && '/' !in folder && '\\' !in folder && folder !in listOf(".",".."),onClick={val target=path.trimEnd('/')+"/"+folder;c.action(null){c.api("/api/files/mkdir","POST",json("path" to target)).also { refreshListing() }};showFolder=false;folder=""}){Text(stringResource(R.string.ui_4759498ac2))}},dismissButton={TextButton(onClick={showFolder=false}){Text(stringResource(R.string.ui_19766ed6cc))}})
}
