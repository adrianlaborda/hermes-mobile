package com.nousresearch.hermesmobile.features

import com.nousresearch.hermesmobile.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun ModelsPage(c: ParityController) {
    var slot by remember(c) { mutableStateOf<JSONObject?>(null) }
    var moaOpen by remember { mutableStateOf(false) }
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { FeatureCard(stringResource(R.string.ui_92495347c5), "Profile defaults for background tasks. Chat model selections remain scoped to their session.") {
            c.unavailable["auxiliary"]?.let {Text(it)}
            Text("Main: ${c.data["auxiliary"]?.optJSONObject("main")?.optString("provider").orEmpty()} / ${c.data["auxiliary"]?.optJSONObject("main")?.optString("model").orEmpty()}")
            TextButton(onClick={c.confirm("Reset every auxiliary slot to inherit from the main model?") {c.assignModel(json("scope" to "auxiliary","task" to "__reset__","provider" to "auto","model" to "","reasoning_effort" to null))}}) {Text(stringResource(R.string.ui_fc7c61a646))}
        } }
        items(c.data["auxiliary"]?.rows("tasks").orEmpty(),key={it.optString("task")}) { task ->
            FeatureCard(task.optString("task"), "${task.optString("provider")} / ${task.optString("model").ifBlank {"Inherit"}}") {
                Text("Reasoning: ${task.optString("reasoning_effort").takeUnless {it=="null" || it.isBlank()} ?: "Inherit"}",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={slot=task},enabled=!c.busy){Text(stringResource(R.string.ui_6defafa2ca))}
            }
        }
        item { FeatureCard(stringResource(R.string.ui_c30a5a6849), "Reference models generate answers; the aggregator combines them.") {
            c.unavailable["moa"]?.let {Text(it)}
            c.data["moa"]?.let { Text("Default preset: ${it.optString("default_preset")}\nActive: ${it.optString("active_preset")}");TextButton(onClick={moaOpen=true}) {Text(stringResource(R.string.ui_d78fb41c2d))} }
        } }
    }
    slot?.let { task ->
        var provider by remember(task) {mutableStateOf(task.optString("provider","auto"))}
        var model by remember(task) {mutableStateOf(task.optString("model"))}
        var reasoning by remember(task) {mutableStateOf(task.optString("reasoning_effort").takeUnless {it=="null"}.orEmpty())}
        var baseUrl by remember(task) {mutableStateOf(task.optString("base_url"))}
        AlertDialog(onDismissRequest={slot=null},title={Text(task.optString("task"))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(provider,{provider=it},label={Text(stringResource(R.string.ui_a26c87a5ef))})
            OutlinedTextField(model,{model=it},label={Text(stringResource(R.string.ui_9ae0163c83))})
            OutlinedTextField(baseUrl,{baseUrl=it},label={Text(stringResource(R.string.ui_083b94d3d8))})
            ChoiceField(stringResource(R.string.ui_d8211e24e8),reasoning,listOf("","none","minimal","low","medium","high","xhigh","max")){reasoning=it}
            TextButton(onClick={provider="auto";model="";reasoning="";baseUrl=""}){Text(stringResource(R.string.ui_83f1280737))}
        }},confirmButton={TextButton(onClick={c.action("Models") {c.assignModel(json("scope" to "auxiliary","task" to task.optString("task"),"provider" to provider.trim(),"model" to model.trim(),"base_url" to baseUrl.trim(),"reasoning_effort" to reasoning.ifBlank {null}))};slot=null}){Text(stringResource(R.string.ui_1509f561f2))}},dismissButton={TextButton(onClick={slot=null}){Text(stringResource(R.string.ui_19766ed6cc))}})
    }
    if(moaOpen) c.data["moa"]?.let { original -> MoaDialog(original,{moaOpen=false}) { updated ->
        c.action("Models") {
            val latest=c.api("/api/model/moa")
            check(jsonEquivalent(original,latest)) {"MoA was changed by another client. Refresh before saving."}
            c.api("/api/model/moa","PUT",updated)
        };moaOpen=false
    } }
}

/** Structural comparison ignores object ordering but retains array order and value types. */
fun jsonEquivalent(a: Any?, b: Any?): Boolean = when {
    a is JSONObject && b is JSONObject -> a.keySet()==b.keySet() && a.keySet().all {jsonEquivalent(a.opt(it),b.opt(it))}
    a is JSONArray && b is JSONArray -> a.length()==b.length() && (0 until a.length()).all {jsonEquivalent(a.opt(it),b.opt(it))}
    a is Number && b is Number -> a.toDouble()==b.toDouble()
    else -> a==b
}
private fun JSONObject.keySet(): Set<String> = keys().asSequence().toSet()

@Composable
private fun MoaDialog(original: JSONObject,onDismiss:()->Unit,onSave:(JSONObject)->Unit) {
    var document by remember(original) {mutableStateOf(original.toString())}
    val config=remember(document){JSONObject(document)}
    val presets=config.optJSONObject("presets") ?: JSONObject().put("default",JSONObject(original.toString()))
    var selected by remember {mutableStateOf(config.optString("active_preset").ifBlank {config.optString("default_preset","default")})}
    if(!presets.has(selected)) selected=presets.keys().asSequence().firstOrNull() ?: "default"
    var newName by remember {mutableStateOf("")}
    var error by remember {mutableStateOf<String?>(null)}
    val invalid = remember {mutableStateMapOf<String, String>()}
    val preset=presets.optJSONObject(selected) ?: JSONObject()
    fun edit(block:(JSONObject)->Unit) { val copy=JSONObject(document);val ps=copy.optJSONObject("presets") ?: JSONObject().put("default",JSONObject(original.toString()));val p=ps.optJSONObject(selected) ?: JSONObject();block(p);ps.put(selected,p);copy.put("presets",ps);document=copy.toString() }
    FullScreenEditor(onDismissRequest=onDismiss,title={Text(stringResource(R.string.ui_c30a5a6849))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        val names=presets.keys().asSequence().toList()
        ChoiceField(stringResource(R.string.ui_7401a8ec30),selected,names){selected=it}
        ChoiceField(stringResource(R.string.ui_dbb39a06d9),config.optString("default_preset"),names){config.put("default_preset",it);document=config.toString()}
        ChoiceField(stringResource(R.string.ui_96df9db253),config.optString("active_preset"),names){config.put("active_preset",it);document=config.toString()}
        Row {OutlinedTextField(newName,{newName=it},label={Text(stringResource(R.string.ui_75be83ad9a))},modifier=Modifier.weight(1f));TextButton(onClick={if(newName.matches(Regex("[A-Za-z0-9_-]{1,64}")) && !presets.has(newName)){presets.put(newName,JSONObject(preset.toString()));config.put("presets",presets);document=config.toString();selected=newName;newName="";error=null}else error="Choose a unique preset name"}){Text(stringResource(R.string.ui_9fd728c66c))}}
        if(names.size>1) TextButton(onClick={invalid.keys.filter { it.startsWith("$selected:") }.toList().forEach { invalid.remove(it) };presets.remove(selected);val first=presets.keys().next();if(config.optString("default_preset")==selected)config.put("default_preset",first);if(config.optString("active_preset")==selected)config.put("active_preset",first);config.put("presets",presets);selected=first;document=config.toString()}){Text(stringResource(R.string.ui_b9e6e32f8a))}
        Row {Text(stringResource(R.string.ui_92c1cdfdf4),Modifier.weight(1f));Switch(preset.optBoolean("enabled",true),{value->edit {it.put("enabled",value)}})}
        ModelSlotFields("Aggregator",preset.optJSONObject("aggregator") ?: JSONObject()){value->edit {it.put("aggregator",value)}}
        preset.rows("reference_models").forEachIndexed {index,ref ->
            ModelSlotFields("Reference ${index+1}",ref){value->edit {val refs=it.optJSONArray("reference_models") ?: JSONArray();refs.put(index,value);it.put("reference_models",refs)}}
            TextButton(onClick={edit {it.optJSONArray("reference_models")?.remove(index)}}){Text("Remove reference ${index+1}")}
        }
        TextButton(onClick={edit {val refs=it.optJSONArray("reference_models") ?: JSONArray();refs.put(json("provider" to "","model" to "","enabled" to true));it.put("reference_models",refs)}}){Text(stringResource(R.string.ui_2afcd9ea4c))}
        listOf("reference_temperature","aggregator_temperature","reference_timeout").forEach {key ->
            var text by remember(selected,key){mutableStateOf(preset.opt(key)?.takeUnless {it==JSONObject.NULL}?.toString().orEmpty())}
            OutlinedTextField(text,{value->text=value;val number=value.toDoubleOrNull();if(value.isBlank() || (number!=null && number.isFinite() && (key!="reference_timeout" || number>0))) {invalid.remove("$selected:$key");edit {it.put(key,number ?: JSONObject.NULL)}} else invalid["$selected:$key"]="Enter a finite number (timeout must be positive)"},label={Text(key.replace('_',' '))},supportingText={Text(stringResource(R.string.ui_f4236e3ebe))})
        }
        ChoiceField(stringResource(R.string.ui_82824a440d),preset.optString("degraded_reference_policy","loud"),listOf("loud","silent")){value->edit {it.put("degraded_reference_policy",value)}}
        var fanout by remember(selected){mutableStateOf(preset.optString("fanout").takeUnless {it=="null"}.orEmpty().ifBlank {"user_turn"})}
        OutlinedTextField(fanout,{value->fanout=value;if(value in listOf("user_turn","per_iteration") || value.matches(Regex("every_n:[1-9][0-9]*"))){invalid.remove("$selected:fanout");edit {it.put("fanout",value)}}else invalid["$selected:fanout"]="Fanout: user_turn, per_iteration or every_n:N"},label={Text(stringResource(R.string.ui_8bba9a0486))})
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        invalid.forEach { (field, message) -> Text("$field: $message",color=MaterialTheme.colorScheme.error) }
    }},confirmButton={TextButton(enabled=error==null && invalid.isEmpty(),onClick={onSave(JSONObject(document))}){Text(stringResource(R.string.ui_8a1cd5345e))}},dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.ui_19766ed6cc))}})
}

@Composable
private fun ModelSlotFields(title:String,slot:JSONObject,onChange:(JSONObject)->Unit) {
    FeatureCard(title) {
        OutlinedTextField(slot.optString("provider"),{onChange(JSONObject(slot.toString()).put("provider",it))},label={Text(stringResource(R.string.ui_472590ae97))},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(slot.optString("model"),{onChange(JSONObject(slot.toString()).put("model",it))},label={Text(stringResource(R.string.ui_5e2c614c23))},modifier=Modifier.fillMaxWidth())
        ChoiceField(stringResource(R.string.ui_d8211e24e8),slot.optString("reasoning_effort").takeUnless{it=="null"}.orEmpty(),listOf("","none","minimal","low","medium","high","xhigh","max")){value->onChange(JSONObject(slot.toString()).apply {if(value.isBlank())remove("reasoning_effort")else put("reasoning_effort",value)})}
        Row {Text(stringResource(R.string.ui_92c1cdfdf4),Modifier.weight(1f));Switch(slot.optBoolean("enabled",true),{onChange(JSONObject(slot.toString()).put("enabled",it))})}
    }
}
