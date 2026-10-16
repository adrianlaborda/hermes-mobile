package com.nousresearch.hermesmobile.features

import com.nousresearch.hermesmobile.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

private fun configValue(root:JSONObject,path:String):Any? {var value:Any?=root;for(key in path.split('.'))value=(value as? JSONObject)?.opt(key);return value}
private fun configPatch(path:String,value:Any?):JSONObject {val keys=path.split('.');val root=JSONObject();var node=root;for(key in keys.dropLast(1)){val child=JSONObject();node.put(key,child);node=child};node.put(keys.last(),value ?: JSONObject.NULL);return root}

internal fun sensitiveSetting(path: String, schema: JSONObject?): Boolean =
    schema?.optBoolean("secret") == true || schema?.optBoolean("sensitive") == true ||
        schema?.optString("type") in setOf("secret", "password") ||
        path.split('.').any { it.lowercase() in setOf("api_key", "password", "token", "access_token", "refresh_token", "auth_token", "secret", "credentials", "authorization") }

@Composable
fun SettingsPage(c:ParityController){
    var section by remember { mutableIntStateOf(0) }
    var technical by remember { mutableStateOf(false) }
    var query by remember{mutableStateOf("")}
    var editing by remember{mutableStateOf<Pair<String,JSONObject>?>(null)}
    var mcpEditor by remember{mutableStateOf<String?>(null)}
    var mcpOriginal by remember{mutableStateOf<JSONObject?>(null)}
    val fields=c.data["schema"]?.optJSONObject("fields") ?: JSONObject()
    val config=c.data["config"] ?: JSONObject()
    val changedElsewhere=stringResource(R.string.i18n_set_changed_elsewhere)
    val invalidJson=stringResource(R.string.i18n_set_invalid_json)
    val confirmMcp=stringResource(R.string.i18n_set_mcp_confirm)
    val mcpChanged=stringResource(R.string.i18n_set_mcp_changed)
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(R.string.a14_settings, R.string.a14_operations, R.string.a14_usage).forEachIndexed { index, label ->
                FilterChip(section == index, { section = index }, label = { Text(stringResource(label)) })
            }
        } }
        if (section == 0) {
        item{FeatureCard(stringResource(R.string.ui_eb67fa8c89), stringResource(R.string.i18n_set_hint, c.profile)){
            c.unavailable["config"]?.let{Text(shown(it))};c.unavailable["schema"]?.let{Text(shown(it))}
            OutlinedTextField(query,{query=it},label={Text(stringResource(R.string.ui_556e14934a))},modifier=Modifier.fillMaxWidth())
        }}
        items(fields.keys().asSequence().filter{key-> !key.startsWith("desktop.") && !sensitiveSetting(key, fields.optJSONObject(key)) && (key.contains(query,true) || fields.optJSONObject(key)?.optString("description")?.contains(query,true)==true)}.toList(),key={it}){key->
            val field=fields.optJSONObject(key) ?: JSONObject()
            FeatureCard(key.replace('_',' '),field.optString("description")){
                Text(configValue(config,key)?.toString().orEmpty().take(350),style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={editing=key to field}){Text(stringResource(R.string.ui_464c4ffd01))}
            }
        }
        item{FeatureCard(stringResource(R.string.ui_f0a06c9b34), stringResource(R.string.i18n_set_oauth_hint)){
            c.data["mcp"]?.rows("servers").orEmpty().forEach{server->McpOAuthButton(c,server.optString("name"))}
            TextButton(onClick={val servers=config.optJSONObject("mcp_servers") ?: JSONObject();mcpOriginal=JSONObject(servers.toString());mcpEditor=servers.toString(2)}){Text(stringResource(R.string.ui_f81ff93586))}
        }}
        }
        if (section == 1) {
        item{FeatureCard(stringResource(R.string.ui_e28773da31), stringResource(R.string.i18n_set_ops_hint, c.profile)){
            listOf("doctor" to stringResource(R.string.i18n_set_doctor),"security-audit" to stringResource(R.string.i18n_set_audit),"backup" to stringResource(R.string.i18n_set_backup)).forEach{(action,label)->
                TextButton(enabled=!c.busy,onClick={c.fetch("operation"){c.api("/api/ops/$action","POST",JSONObject())}}){Text(label)}
            }
            c.data["operation"]?.let{result->
                Text(result.optString("message").ifBlank { result.optString("status").ifBlank { result.optString("action") } })
                TextButton(onClick = { technical = !technical }) { Text(stringResource(R.string.a14_technical)) }
                if (technical) Text(result.toString(2),style=MaterialTheme.typography.bodySmall)
                val name=result.optString("action")
                if(name.isNotBlank())TextButton(onClick={c.fetch("operation-status"){c.api("/api/actions/${encoded(name)}/status?lines=100")}}){Text(stringResource(R.string.ui_1eb0f9bcb7))}
            }
            c.data["operation-status"]?.let {
                val output = it.optJSONArray("lines")?.let { rows -> (0 until rows.length()).joinToString("\n") { i -> rows.optString(i) } }
                    ?: it.optString("output").ifBlank { it.optString("status") }
                Text(output, style=MaterialTheme.typography.bodySmall)
                if (technical) Text(it.toString(2),style=MaterialTheme.typography.bodySmall)
            }
        }}
        }
        if (section == 2) {
        item{FeatureCard(stringResource(R.string.ui_727030f3ee)) {
            c.unavailable["usage"]?.let { Text(shown(it)) }
            c.data["usage"]?.optJSONObject("totals")?.let { totals ->
                Text(stringResource(R.string.i18n_set_usage_sessions, totals.optLong("total_sessions"), totals.optLong("total_api_calls")))
                Text(stringResource(R.string.i18n_set_usage_tokens, totals.optLong("total_input"), totals.optLong("total_output")))
                Text(stringResource(R.string.i18n_set_usage_cost, "%.4f".format(totals.optDouble("total_estimated_cost",0.0))))
            }
        }}
        items(c.data["usage"]?.rows("by_model").orEmpty()) { model -> FeatureCard(model.optString("model")) {
            Text(stringResource(R.string.i18n_set_usage_model, model.optLong("input_tokens") + model.optLong("output_tokens"), model.optLong("api_calls")))
            Text(stringResource(R.string.i18n_set_usage_cost, "%.4f".format(model.optDouble("estimated_cost",0.0))))
        } }
        }
    }
    editing?.let{(key,schema)->
        val initial=configValue(config,key)
        val field=JSONObject(schema.toString()).put("key",key).put("label",key.replace('_',' ')).put("value",initial ?: "")
        if(field.optString("type")=="select") {field.put("type","enum");field.put("choices",field.optJSONArray("options") ?: JSONArray())}
        SchemaDialog(stringResource(R.string.i18n_set_edit),listOf(field),{editing=null},{values->
            c.action("Settings"){
                val latest=c.api("/api/config")
                check(jsonEquivalent(configValue(latest,key),initial)){changedElsewhere}
                var value=values.opt(key)
                if(schema.optString("type")=="list") value=JSONArray(value?.toString().orEmpty())
                c.api("/api/config","PUT",json("config" to configPatch(key,value)))
            };editing=null
        })
    }
    mcpEditor?.let{initial->TextEditorDialog(stringResource(R.string.i18n_set_mcp_map),initial,{mcpEditor=null},{text->
        val parsed=runCatching{JSONObject(text)}
        if(parsed.isFailure)c.message=invalidJson.format(parsed.exceptionOrNull()?.message.orEmpty())else{
            c.confirm(confirmMcp.format(c.profile)){
                val latest=c.api("/api/config").optJSONObject("mcp_servers") ?: JSONObject()
                check(jsonEquivalent(latest,mcpOriginal)){mcpChanged}
                c.api("/api/mcp/servers","PUT",json("servers" to parsed.getOrThrow()))
            };mcpEditor=null
        }
    })}
}
