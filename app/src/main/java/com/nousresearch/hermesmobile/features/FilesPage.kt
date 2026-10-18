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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.nousresearch.hermesmobile.ui.components.EmptyState
import com.nousresearch.hermesmobile.ui.theme.HermesMono
import com.nousresearch.hermesmobile.ui.theme.hermes
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
fun FilesPage(c:ParityController,initialPath:String?=null,sessionId:String?=null,initialDirectory:String?=null,onExit:(()->Unit)?=null) {
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
    // An attachment ref is relative to the chat's working directory, which the gateway records on the session.
    suspend fun located(remote:String):String {
        if(remote.startsWith('/') || remote.startsWith("~"))return remote
        val cwd=runCatching{sessionId?.let{c.api("/api/sessions/${encoded(it)}").optString("cwd")}}.getOrNull()
            ?.takeIf{it.startsWith('/')} ?: runCatching{c.api("/api/fs/default-cwd").optString("cwd")}.getOrDefault("")
        return com.nousresearch.hermesmobile.data.TurnDisplay.resolve(remote,cwd)
    }
    LaunchedEffect(c,initialPath,initialDirectory){withContext(Dispatchers.IO){MobileFiles.clean(context)};if(initialPath!=null)openFile(located(initialPath))else browse(initialDirectory.orEmpty())}
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
    var editPath by remember{mutableStateOf<String?>(null)}
    var deleteTarget by remember{mutableStateOf<JSONObject?>(null)}
    val deleteFailed=stringResource(R.string.i18n_files_delete_failed)
    val parent=listing?.optString("parent")?.takeUnless{it=="null" || it.isBlank()} ?: if(!managed)path.substringBeforeLast('/',"").takeIf{it.isNotBlank()}else null
    // Back closes an open file, then climbs folder by folder (as far as this view started), then leaves the page.
    var startedAt by remember(c){mutableStateOf<String?>(null)}
    LaunchedEffect(listing){if(startedAt==null && listing!=null)startedAt=path}
    // A file opened from the chat is the whole page: closing it goes back to the chat, not to a folder listing.
    fun closePreview(){if(initialPath!=null && onExit!=null)onExit() else {preview=null;exported=null}}
    androidx.activity.compose.BackHandler(enabled=preview!=null){closePreview()}
    androidx.activity.compose.BackHandler(enabled=preview==null && initialPath==null && parent!=null && path.trimEnd('/')!=startedAt.orEmpty().trimEnd('/') && !c.busy){parent?.let{browse(it)}}
    Column(Modifier.fillMaxSize()) {
        // Where the files come from: Hermes' managed files or the profile's workspace (a chat artifact or a project
        // opens straight on its file or folder, so it has no switch).
        if(initialPath==null && initialDirectory==null) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp)) {
            SegmentedButton(selected=managed,onClick={if(!managed){startedAt=null;browse("",true)}},shape=SegmentedButtonDefaults.itemShape(0,2),enabled=!c.busy){Text(stringResource(R.string.ui_19d199c7e1),maxLines=1)}
            SegmentedButton(selected=!managed,onClick={if(managed){startedAt=null;c.action(null){val cwd=c.api("/api/fs/default-cwd").optString("cwd");val result=c.api("/api/fs/list?path=${encoded(cwd)}");listing=result;path=cwd;managed=false;preview=null;exported=null;result}}},shape=SegmentedButtonDefaults.itemShape(1,2),enabled=!c.busy){Text(stringResource(R.string.ui_87bb59ba2f),maxLines=1)}
        }
        val p=preview
        if(p!=null) {
            FilePreview(c,p,managed,exported,onClose={closePreview()},
                onSave={captured->c.action(null){val latest=readPreview(p.optString("path"));check(latest.optString("text")==p.optString("text") && !latest.optBoolean("truncated")){"File changed on the gateway. Reopen before saving."};(if(managed)c.api("/api/files/upload","POST",json("path" to p.optString("path"),"data_url" to "data:text/plain;base64,"+java.util.Base64.getEncoder().encodeToString(captured.toByteArray(Charsets.UTF_8)),"overwrite" to true)) else c.api("/api/fs/write-text","POST",json("path" to p.optString("path"),"content" to captured))).also{preview=JSONObject(p.toString()).put("text",captured)}}},
                onDownload={download(p.optString("path"))},
                onSaveAs={download(p.optString("path")){save.launch(it.displayName)}},
                onOpen={download(p.optString("path")){MobileFiles.share(context,it,true)}},
                onShare={download(p.optString("path")){MobileFiles.share(context,it,false)}},
                onDelete={deleteTarget=p})
            return@Column
        }
        // The folder: up, where we are (tap to type a path), and what can be added here.
        Row(Modifier.fillMaxWidth().padding(start=4.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={parent?.let{browse(it)}},enabled=parent!=null && !c.busy){Icon(Icons.Default.ArrowUpward,stringResource(R.string.ui_77a245c653).trim())}
            Text(path.ifBlank{"/"},Modifier.weight(1f).clip(MaterialTheme.shapes.small).clickable{editPath=path}.padding(vertical=8.dp,horizontal=4.dp),
                style=MaterialTheme.typography.bodySmall.copy(fontFamily=HermesMono),color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.StartEllipsis)
            if(managed){
                IconButton(onClick={showFolder=true},enabled=!c.busy){Icon(Icons.Default.CreateNewFolder,stringResource(R.string.ui_cf28f49ec5))}
                IconButton(onClick={upload.launch(arrayOf("*/*"))},enabled=path.isNotBlank() && !c.busy){Icon(Icons.Default.UploadFile,stringResource(R.string.ui_865e89de78))}
            }
        }
        HorizontalDivider(color=MaterialTheme.hermes.line)
        val entries=listing?.rows("entries").orEmpty()
        if(listing!=null && entries.isEmpty()) {
            EmptyState(Icons.Default.FolderOpen,stringResource(R.string.i18n_files_empty),stringResource(if(managed) R.string.a14_managed_files else R.string.a14_workspace_files))
        } else LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)) {
            items(entries,key={it.optString("path")}){entry->
                val directory=entry.optBoolean("is_directory",entry.optBoolean("isDirectory"))
                val size=entry.takeIf{it.has("size") && !directory}?.let{android.text.format.Formatter.formatShortFileSize(context,it.optLong("size"))}
                // A long press deletes from Hermes' managed files (folders only when empty, as the gateway requires).
                FileRow(entry.optString("name"),directory,size,
                    onClick={if(!c.busy){if(directory)browse(entry.optString("path"))else openFile(entry.optString("path"))}},
                    onLongClick=if(managed)({deleteTarget=entry}) else null)
            }
        }
    }
    editPath?.let{current->
        var value by remember{mutableStateOf(current)}
        AlertDialog(onDismissRequest={editPath=null},title={Text(stringResource(R.string.ui_23a2e8047d))},
            text={OutlinedTextField(value,{value=it},singleLine=true,textStyle=MaterialTheme.typography.bodyMedium.copy(fontFamily=HermesMono),modifier=Modifier.fillMaxWidth())},
            confirmButton={TextButton(onClick={editPath=null;browse(value.trim())}){Text(stringResource(R.string.ui_6cc8519b91))}},
            dismissButton={TextButton(onClick={editPath=null}){Text(stringResource(R.string.ui_19766ed6cc))}})
    }
    deleteTarget?.let{target->
        val directory=target.optBoolean("is_directory",target.optBoolean("isDirectory"))
        AlertDialog(onDismissRequest={deleteTarget=null},
            icon={Icon(Icons.Default.DeleteOutline,null,tint=MaterialTheme.colorScheme.error)},
            title={Text(stringResource(if(directory) R.string.i18n_files_delete_folder else R.string.i18n_files_delete_file))},
            text={Text(target.optString("path").ifBlank{target.optString("name")})},
            confirmButton={TextButton(colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error),onClick={
                deleteTarget=null
                c.action(null){runCatching{c.api("/api/files","DELETE",json("path" to target.optString("path"),"recursive" to false))}.getOrElse{c.message=deleteFailed;json()}.also{if(preview?.optString("path")==target.optString("path")){preview=null;exported=null};refreshListing()}}
            }){Text(stringResource(R.string.ui_e2d0a54968))}},
            dismissButton={TextButton(onClick={deleteTarget=null}){Text(stringResource(R.string.ui_19766ed6cc))}})
    }
    if(showFolder)AlertDialog(onDismissRequest={showFolder=false},title={Text(stringResource(R.string.ui_cf28f49ec5))},text={OutlinedTextField(folder,{folder=it},label={Text(stringResource(R.string.ui_14d34edf50))})},confirmButton={TextButton(enabled=folder.isNotBlank() && '/' !in folder && '\\' !in folder && folder !in listOf(".",".."),onClick={val target=path.trimEnd('/')+"/"+folder;c.action(null){c.api("/api/files/mkdir","POST",json("path" to target)).also { refreshListing() }};showFolder=false;folder=""}){Text(stringResource(R.string.ui_4759498ac2))}},dismissButton={TextButton(onClick={showFolder=false}){Text(stringResource(R.string.ui_19766ed6cc))}})
}

/** One folder entry, in the list style of Chats and Bots. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(name:String,directory:Boolean,size:String?,onClick:()->Unit,onLongClick:(()->Unit)?) {
    Row(Modifier.fillMaxWidth().combinedClickable(onClick=onClick,onLongClick=onLongClick).padding(horizontal=16.dp,vertical=10.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(if(directory)MaterialTheme.hermes.accentTint else MaterialTheme.colorScheme.surfaceContainerHigh,RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){
            Icon(if(directory)Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,null,Modifier.size(18.dp),
                tint=if(directory)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)){
            Text(name,style=MaterialTheme.typography.bodyLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(size!=null)Text(size,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(directory)Icon(Icons.Default.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** An open file: its name and actions on top, then its text (editable when complete) or image. */
@Composable
private fun FilePreview(
    c:ParityController,p:JSONObject,managed:Boolean,exported:ExportedFile?,onClose:()->Unit,onSave:(String)->Unit,
    onDownload:()->Unit,onSaveAs:()->Unit,onOpen:()->Unit,onShare:()->Unit,onDelete:()->Unit
) {
    val context=LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start=4.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onClose){Icon(Icons.Default.Close,stringResource(R.string.ui_19766ed6cc))}
            Column(Modifier.weight(1f)){
                Text(p.optString("path").substringAfterLast('/'),style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(listOf(android.text.format.Formatter.formatShortFileSize(context,p.optLong("byteSize")).takeIf{p.has("byteSize")},p.optString("mimeType")).filterNotNull().filter{it.isNotBlank()}.joinToString(" · "),
                    style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
            }
            IconButton(onClick=onOpen,enabled=!c.busy){Icon(Icons.AutoMirrored.Filled.OpenInNew,stringResource(R.string.ui_ed077f3d81))}
            IconButton(onClick=onShare,enabled=!c.busy){Icon(Icons.Default.Share,stringResource(R.string.ui_29887a5ff9))}
            var menu by remember{mutableStateOf(false)}
            Box{
                IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,null)}
                DropdownMenu(menu,{menu=false}){
                    DropdownMenuItem(text={Text(stringResource(R.string.ui_d6eafe8235))},leadingIcon={Icon(Icons.Default.Download,null)},onClick={menu=false;onDownload()})
                    DropdownMenuItem(text={Text(stringResource(R.string.ui_06359c03b0))},leadingIcon={Icon(Icons.Default.SaveAlt,null)},onClick={menu=false;onSaveAs()})
                    if(managed)DropdownMenuItem(text={Text(stringResource(R.string.ui_e2d0a54968),color=MaterialTheme.colorScheme.error)},leadingIcon={Icon(Icons.Default.DeleteOutline,null,tint=MaterialTheme.colorScheme.error)},onClick={menu=false;onDelete()})
                }
            }
        }
        HorizontalDivider(color=MaterialTheme.hermes.line)
        // Images and PDFs show themselves, as in Desktop; other binaries wait for an explicit download.
        LaunchedEffect(p){val mime=p.optString("mimeType").ifBlank{if(p.optString("path").endsWith(".pdf",true))"application/pdf" else ""}
            if(p.optBoolean("binary") && exported==null && (mime.startsWith("image/") || mime=="application/pdf") && p.optLong("byteSize")<16L*1024*1024)onDownload()}
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(!p.optBoolean("binary")){
                var text by remember(p){mutableStateOf(p.optString("text"))}
                val truncated=p.optBoolean("truncated")
                if(truncated)Text(stringResource(R.string.ui_c39d0fc75b),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(text,{text=it},readOnly=truncated,textStyle=MaterialTheme.typography.bodySmall.copy(fontFamily=HermesMono),modifier=Modifier.fillMaxWidth().heightIn(min=220.dp))
                if(!truncated && text!=p.optString("text"))Button(onClick={onSave(text)},enabled=!c.busy){Text(stringResource(R.string.ui_793017feec))}
            }else if(exported==null)Text(stringResource(R.string.ui_c2236b2c86),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            val bitmap by produceState<android.graphics.Bitmap?>(null,exported){
                value=withContext(Dispatchers.IO){exported?.takeIf{it.mime.startsWith("image/") && it.file.length()<16*1024*1024}?.let{e->com.nousresearch.hermesmobile.data.ImageDecode.file(e.file.path,1600)}}
            }
            exported?.let{DocumentPreview(it)}
            bitmap?.let{Image(it.asImageBitmap(),null,Modifier.fillMaxWidth().heightIn(max=420.dp))}
        }
    }
}
