#!/usr/bin/env python3
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors = []

# UI labels now live in Android resources. Resolve only referenced resource IDs
# for the existing feature-presence checks; do not append the whole catalog.
resource_strings = {}
for resource_file in (ROOT / 'app/src/main/res/values').glob('strings*.xml'):
    for entry in ET.parse(resource_file).getroot().findall('string'):
        resource_strings[entry.get('name')] = ''.join(entry.itertext()).strip('"')
def with_resource_labels(source):
    refs = re.findall(r'R\.string\.(\w+)', source)
    return source + '\n' + '\n'.join(resource_strings.get(key, '') for key in refs)

for xml in ROOT.glob('app/src/main/**/*.xml'):
    try: ET.parse(xml)
    except Exception as e: errors.append(f'{xml}: XML: {e}')

for kt in ROOT.glob('app/src/main/**/*.kt'):
    text = kt.read_text()
    # Crude but useful repository-level guard against common generated/patch corruption.
    for bad in ('if (if (', 'if(if(', '{{onPause', '{{onResume'):
        if bad in text: errors.append(f'{kt}: suspicious token {bad!r}')
    # Strip strings/comments before a simple delimiter balance check.
    s = re.sub(r'""".*?"""', '""', text, flags=re.S)
    s = re.sub(r'"(?:\\.|[^"\\])*"', '""', s)
    s = re.sub(r"'(?:\\.|[^'\\])'", "''", s)
    s = re.sub(r'/\*.*?\*/', '', s, flags=re.S)
    s = re.sub(r'//.*', '', s)
    for op, cl in [('(',')'),('[',']'),('{','}')]:
        depth = 0
        for ch in s:
            if ch == op: depth += 1
            elif ch == cl: depth -= 1
            if depth < 0:
                errors.append(f'{kt}: unmatched {cl}')
                break
        if depth != 0: errors.append(f'{kt}: unbalanced {op}{cl}: {depth}')


# Phase-1 security/protocol invariants.
manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
if 'android:allowBackup="false"' not in manifest:
    errors.append('release manifest must disable Android backup')
if 'android:usesCleartextTraffic="true"' not in manifest:
    errors.append('alpha7 must allow Android cleartext transport so private-LAN HTTP can work')

gateway = (ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/data/HermesGatewayClient.kt').read_text()
for required in ('client.capabilities', 'open_requests', 'session.events.since', 'gateway.ping', 'activeGeneration'):
    if required not in gateway:
        errors.append(f'gateway protocol invariant missing: {required}')

vm = (ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/HermesViewModel.kt').read_text()
ready_pos = vm.find('withTimeout(25_000)')
save_pos = vm.find('persistActiveConfig(persisted')
if ready_pos < 0 or save_pos < 0 or save_pos < ready_pos:
    errors.append('gateway config must only be persisted after WebSocket ready')
if 'interactionQueue' not in vm:
    errors.append('server requests must be queued, not stored as a single overwriteable prompt')


# Phase-2 sessions/chat invariants.
repo = (ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/data/HermesRepository.kt').read_text()
resume_start = repo.find('suspend fun resumeSession')
resume_end = repo.find('suspend fun submitPrompt', resume_start)
resume = repo[resume_start:resume_end] if resume_start >= 0 and resume_end > resume_start else ''
for required in ('defer_history', 'omit_messages', 'inline_images'):
    if required not in resume:
        errors.append(f'session resume paging invariant missing: {required}')

rest = (ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/data/HermesRestClient.kt').read_text()
for required in ('sessionMessagesPage', 'order=${enc(order)}', 'include_compacted=true', '/api/sessions/search'):
    if required not in rest:
        errors.append(f'session REST invariant missing: {required}')

for required in ('navigationGeneration', 'STREAM_FLUSH_MS', 'loadOlderMessages', 'sessionsHasMore', 'sessionSearchResults'):
    if required not in vm:
        errors.append(f'chat state invariant missing: {required}')
if 'session.history' in vm:
    errors.append('ViewModel must not use full session.history for normal transcript refresh')

# alpha22 split the chat UI into list / thread / composer files; check them as one surface.
chat = '\n'.join((ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/ui/screens' / name).read_text()
                 for name in ('ChatScreen.kt', 'SessionList.kt', 'ChatThread.kt', 'ChatComposer.kt', 'ContextSheet.kt'))
chat = with_resource_labels(chat)
for required in ('Show earlier', 'reverseLayout = true', 'onTogglePin', 'onBranch', 'onRetry', 'SlashSuggestions'):
    if required not in chat:
        errors.append(f'chat UI invariant missing: {required}')

models = '\n'.join((ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/data' / name).read_text()
                   for name in ('Models.kt', 'SessionContext.kt'))
for required in ('lineageRootId', 'display_content', 'codex_message_items', 'displayKind != "hidden"'):
    if required not in models:
        errors.append(f'message/session identity invariant missing: {required}')

# Phase-3 individual Bot Mode invariants.
for required in (
    'canonicalBotLocks', 'already in use', 'follow_profile_config', 'setup.runtime_check',
    'profiles.describe', 'profiles.configure', 'ui_meta_expected_revisions',
    'confirmBotModelSwitch', 'enabled_mcp_servers', 'enabled_toolsets', 'disabled_skills'
):
    if required not in repo:
        errors.append(f'Bot Mode invariant missing: {required}')

for required in ('uiMetaRevisions', 'BotProfileConfig', 'BotSaveResult', 'botPinned', 'botHidden'):
    if required not in models:
        errors.append(f'Bot model invariant missing: {required}')

bot_ui = (ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/ui/screens/BotsScreen.kt').read_text()
bot_ui = with_resource_labels(bot_ui)
bot_editor = (ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/ui/screens/BotEditorDialog.kt').read_text()
bot_editor = with_resource_labels(bot_editor)
for required in ('New side chat', 'Edit bot', 'Delete bot', 'botPinned'):
    if required not in bot_ui:
        errors.append(f'Bot roster UI invariant missing: {required}')
for required in ('SOUL.md', 'Capabilities', 'Filter skills', 'MCP', 'modelProviders'):
    if required not in bot_editor:
        errors.append(f'Bot editor invariant missing: {required}')
if 'botEditorGeneration' not in vm:
    errors.append('Bot editor async work must have an independent generation guard')

# Bot Mode follow-up (alpha4) hosted Group Chats / mentions / avatar assets invariants.
for required in (
    'profiles.get_asset', 'profiles.set_asset',
    'groups.capabilities', 'groups.list', 'groups.create', 'groups.state',
    'groups.log', 'groups.send', 'groups.rename', 'groups.stop',
    'groups.approve', 'groups.retry', 'groups.disband'
):
    if required not in repo:
        errors.append(f'alpha4 group repository invariant missing: {required}')

for required in ('GroupCapabilities', 'GroupRoom', 'GroupEvent', 'GroupPendingAction', 'ActiveGroupRoom'):
    if required not in models:
        errors.append(f'alpha4 group model invariant missing: {required}')
if 'BOT_MENTION_NOTE_MARKER' not in models or 'if (role == "user")' not in models:
    errors.append('Bot mention model-only scaffolding must be stripped from user display only')

for required in ('groupPollingJob', 'groupGeneration', 'avatarAssets', 'withBotMentionContext',
                 'maxOf(latest.cursor, updated.cursor)', 'takeLast(1200)'):
    if required not in vm:
        errors.append(f'alpha4 group ViewModel invariant missing: {required}')

group_ui_path = ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/ui/screens/GroupChatScreen.kt'
if not group_ui_path.exists():
    errors.append('alpha4 GroupChatScreen.kt missing')
else:
    group_ui = group_ui_path.read_text()
    group_ui = with_resource_labels(group_ui)
    for required in ('Choose 2–6 Bots', 'Reply in thread', 'Allow once', 'Deny', 'Retry', 'Disband Group Chat'):
        if required not in group_ui:
            errors.append(f'Group Chat UI invariant missing: {required}')
    if 'capabilities?.driver == true' not in group_ui:
        errors.append('Group Chat creation must be gated on the hosted-room driver')

if 'ActivityResultContracts.GetContent()' not in bot_editor or 'avatarDataUrlFromUri' not in bot_editor:
    errors.append('Bot editor must support bounded avatar asset selection')
primitives = (ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/ui/components/HermesPrimitives.kt').read_text()
primitives = with_resource_labels(primitives)
if 'decodeAvatarDataUrl' not in primitives or 'inSampleSize' not in primitives:
    errors.append('Bot avatars must decode lazily with sampling')


# Phase-4 (alpha5) Routines + messaging control-plane invariants remain required.
build_gradle = (ROOT / 'app/build.gradle.kts').read_text()

for required in (
    '/api/cron/jobs/${enc(id)}/runs', '/api/cron/delivery-targets',
    '/api/cron/blueprints', '/api/cron/blueprints/instantiate',
    'JSONObject().put("updates", draft.toJson(includeClears = true))',
    '/api/messaging/platforms/${enc(platform)}/test',
    '/api/pairing/clear-pending', '/api/webhooks', '/api/gateway/restart'
):
    if required not in rest:
        errors.append(f'alpha5 REST invariant missing: {required}')

for required in (
    'optTimestampMillisOrNull', 'data class CronDraft', 'reasoningEffort', 'noAgent',
    'data class CronDeliveryTarget', 'data class CronRun', 'data class AutomationBlueprint',
    'data class MessagingSetupField', 'data class MessagingPlatform',
    'data class WebhookRoute', 'data class WebhookReceipt', 'data class WebhookDraft'
):
    if required not in models:
        errors.append(f'alpha5 model invariant missing: {required}')

for required in (
    'cronDeliveryTargets', 'cronBlueprints', 'cronRuns', 'loadCronRuns',
    'instantiateCronBlueprint', 'restartMessagingGateway', 'telegramOnboardingPollingJob',
    'webhookReceipt', 'clearPendingPairing'
):
    if required not in vm:
        errors.append(f'alpha5 ViewModel invariant missing: {required}')

cron_ui_path = ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/ui/screens/CronScreen.kt'
messaging_ui_path = ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/ui/screens/MessagingScreen.kt'
if not cron_ui_path.exists():
    errors.append('alpha5 CronScreen.kt missing')
else:
    cron_ui = cron_ui_path.read_text()
    cron_ui = with_resource_labels(cron_ui)
    for required in ('Automation blueprints', 'Run history', 'Reasoning effort', 'Script / no-agent mode', 'Delivery targets'):
        if required not in cron_ui:
            errors.append(f'alpha5 Cron UI invariant missing: {required}')
if not messaging_ui_path.exists():
    errors.append('alpha5 MessagingScreen.kt missing')
else:
    messaging_ui = messaging_ui_path.read_text()
    messaging_ui = with_resource_labels(messaging_ui)
    for required in ('Channels', 'Access', 'Webhooks', 'Clear stored value', 'HMAC secret', 'Restart messaging gateway'):
        if required not in messaging_ui:
            errors.append(f'alpha5 Messaging UI invariant missing: {required}')
    if 'PrimaryTabRow' in messaging_ui:
        errors.append('alpha5 Messaging UI should not require experimental PrimaryTabRow')
if (ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/ui/screens/TelegramScreen.kt').exists():
    errors.append('legacy TelegramScreen.kt should be replaced by generic MessagingScreen.kt')

# Phase-5A (alpha6) auth + multi-gateway routing invariants.
import re as _re
_vc = _re.search(r'versionCode = (\d+)', build_gradle)
_vn = _re.search(r'versionName = "([^"]+)"', build_gradle)
# SemVer with dot-separated pre-release identifiers (0.3.0-beta.1), so tags and release lists sort correctly.
_semver = r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-(alpha|beta|rc)\.[1-9]\d*)?'
if not _vc or int(_vc.group(1)) < 35:
    errors.append('versionCode missing or lower than 35 (0.3.0-beta.1)')
if not _vn or not _re.fullmatch(_semver, _vn.group(1)):
    errors.append('versionName must be SemVer like 0.3.0 or 0.3.0-beta.1')

auth_path = ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/data/GatewayAuth.kt'
if not auth_path.exists():
    errors.append('alpha6 GatewayAuth.kt missing')
else:
    auth = auth_path.read_text()
    for required in (
        '/api/status', '/api/auth/providers', '/auth/password-login',
        '/auth/native/authorize', '/auth/native/token', '/auth/native/refresh',
        'code_challenge_method', 'S256', '127.0.0.1', 'params["state"] == state' ,
        'Set-Cookie'
    ):
        if required not in auth:
            errors.append(f'alpha6 auth invariant missing: {required}')

store_text = (ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/data/GatewayConfigStore.kt').read_text()
for required in (
    'GatewayStoreSnapshot', 'gateway_connections_v2', 'AndroidKeyStore',
    'AES/GCM/NoPadding', 'KEY_LEGACY_GATEWAY', 'selected_profile',
    'refresh_token', 'setActive', 'remove(id:'
):
    if required not in store_text:
        errors.append(f'alpha6 connection-store invariant missing: {required}')
if '.put("password"' in store_text or 'password =' in store_text:
    errors.append('Basic-auth password must never be persisted')

for required in (
    'GatewayAuthMode', 'NativeTokenSet', 'GatewayDiscovery',
    'authFlows', 'native_pkce', 'selectedProfile'
):
    if required not in models:
        errors.append(f'alpha6 auth model invariant missing: {required}')

for required in (
    '/api/auth/ws-ticket', 'Authorization', 'Bearer ', 'Cookie',
    'ensureFreshNativeToken', 'refreshNativeOrThrow', 'mergeResponseCookies', 'onAuthExpired'
):
    if required not in rest:
        errors.append(f'alpha6 authenticated REST invariant missing: {required}')

for required in ('wsTicketProvider', '?ticket=', '?token=', 'activeGeneration'):
    if required not in gateway:
        errors.append(f'alpha6 authenticated WebSocket invariant missing: {required}')

for required in (
    'connectionGeneration', 'discoverConnection', 'connectWithPassword',
    'startNativeSignIn', 'switchConnection', 'forgetConnection',
    'signOutActive', 'requireReauthentication', 'persistActiveConfig', 'GatewayConnectionState.DISCONNECTED',
    'repository !== repo'
):
    if required not in vm:
        errors.append(f'alpha6 ViewModel routing invariant missing: {required}')

connection_ui_path = ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/ui/screens/ConnectionScreen.kt'
more_ui_path = ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/ui/screens/MoreScreen.kt'
if not connection_ui_path.exists():
    errors.append('alpha6 ConnectionScreen.kt missing')
else:
    connection_ui = connection_ui_path.read_text()
    connection_ui = with_resource_labels(connection_ui)
    for required in ('Saved connections', 'Username', 'Password', 'system browser', 'Session token', 'onConnectSaved'):
        if required not in connection_ui:
            errors.append(f'alpha6 connection UI invariant missing: {required}')
if not more_ui_path.exists():
    errors.append('alpha6 MoreScreen.kt missing')
else:
    more_ui = more_ui_path.read_text()
    more_ui = with_resource_labels(more_ui)
    for required in ('Connections', 'onSwitchConnection', 'onSignOut', 'onAddConnection'):
        if required not in more_ui:
            errors.append(f'alpha6 connection-management UI invariant missing: {required}')

# Explicitly guard against reintroducing password persistence in any Kotlin source.
for kt in ROOT.glob('app/src/main/**/*.kt'):
    txt = kt.read_text()
    # vault.save_login's documented wire response contains password in ephemeral UI state.
    # Remove only that exact approved send expression; all other serialization remains forbidden.
    if kt.name == 'HermesPrimitives.kt':
        txt = txt.replace('onAnswer(org.json.JSONObject().put("identifier", identifier).put("password", value).toString())', '')
    if kt.name != 'GatewayAuth.kt' and re.search(r'put\(\s*"password"', txt):
        errors.append(f'{kt}: password persistence/serialization is forbidden')


# Phase-5B (alpha7) LAN connection repair + chat parity invariants.
policy_path = ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/data/GatewayUrlPolicy.kt'
if not policy_path.exists():
    errors.append('alpha7 GatewayUrlPolicy.kt missing')
else:
    policy = policy_path.read_text()
    for required in (
        'fun normalize', 'fun validate', 'isPrivateOrLocalHost',
        'HTTP sin cifrar solo está permitido', '192 && v4[1] == 168'
    ):
        if required not in policy:
            errors.append(f'alpha7 LAN URL policy invariant missing: {required}')
    for client_text, client_name in ((auth if auth_path.exists() else '', 'auth'), (rest, 'REST'), (gateway, 'WebSocket')):
        if 'GatewayUrlPolicy.canonical' not in client_text:
            errors.append(f'alpha7 {client_name} client must enforce GatewayUrlPolicy')
for required in ('hermes serve --host 0.0.0.0 --port 9119', 'GatewayUrlPolicy.canonical', 'GatewayUrlPolicy.defaultLabel'):
    if required not in vm:
        errors.append(f'alpha7 LAN connection invariant missing: {required}')

for required in (
    'image.attach_bytes', 'pdf.attach', 'file.attach', 'data_url',
    'session.usage', 'model.options', 'slash.exec', 'switchSessionModel'
):
    if required not in repo:
        errors.append(f'alpha7 chat repository invariant missing: {required}')

composer_store = ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/data/ChatComposerStore.kt'
if not composer_store.exists():
    errors.append('alpha7 ChatComposerStore.kt missing')
else:
    composer_text = composer_store.read_text()
    for required in ('AndroidKeyStore', 'AES/GCM/NoPadding', 'QueuedPrompt', 'ComposerAttachment'):
        if required not in composer_text:
            errors.append(f'alpha7 encrypted composer invariant missing: {required}')

for required in (
    'chatDraft', 'composerAttachments', 'queuedPrompts', 'reasoningText',
    'liveStatuses', 'artifactSuggestions', 'sessionModelChoices', 'sessionUsage',
    'addComposerAttachments', 'drainQueuedPrompt', 'handleReasoningEvent', 'handleLiveStatus'
):
    if required not in vm:
        errors.append(f'alpha7 ViewModel chat invariant missing: {required}')

for required in (
    'OpenMultipleDocuments', 'AttachmentStrip', 'QueueLane', 'ModelReasoningDialog',
    'ArtifactLane', 'ScheduleSend', 'contextLabel'
):
    if required not in chat:
        errors.append(f'alpha7 chat UI invariant missing: {required}')

# alpha23: instant `/` palette from commands.catalog and the live context window sheet.
for required in ('commands.catalog', 'session.context_breakdown'):
    if required not in repo:
        errors.append(f'alpha23 repository invariant missing: {required}')
for required in ('"session.usage" -> applyUsageEvent', 'SlashCommands.filter', 'slashCatalog'):
    if required not in vm:
        errors.append(f'alpha23 ViewModel invariant missing: {required}')
for required in ('ContextWindowSheet', 'BreakdownBar', 'CompressBlock', 'ComposerContextRing', 'SlashSectionHeader'):
    if required not in chat:
        errors.append(f'alpha23 chat UI invariant missing: {required}')

for required in ('ComposerAttachment', 'QueuedPrompt', 'SessionModelChoice', 'SessionUsage', 'LiveTaskStatus', 'ArtifactSuggestion'):
    if required not in models:
        errors.append(f'alpha7 chat model invariant missing: {required}')

connection_ui = connection_ui_path.read_text() if connection_ui_path.exists() else ''
connection_ui = with_resource_labels(connection_ui)
if 'Advanced connection settings' not in connection_ui or 'Connection name (optional)' not in connection_ui:
    errors.append('alpha7 connection screen must hide name/proxy headers behind advanced settings')


# Phase-5C (alpha8) global/profile-scoped Capabilities core invariants.
capabilities_ui_path = ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/ui/screens/CapabilitiesScreen.kt'
if not capabilities_ui_path.exists():
    errors.append('alpha8 CapabilitiesScreen.kt missing')
else:
    capabilities_ui = capabilities_ui_path.read_text()
    capabilities_ui = with_resource_labels(capabilities_ui)
    for required in ('MODELS(R.string.i18n_caps_models)', 'SKILLS(R.string.i18n_skills)', 'TOOLS(R.string.i18n_caps_tools)',
                     'MCP(R.string.i18n_caps_mcp)', 'KEYS(R.string.i18n_caps_keys)',
                     'Custom endpoints', 'Provider accounts', 'Search Skills Hub', 'Add MCP server', 'Provider and tool credentials'):
        if required not in capabilities_ui:
            errors.append(f'alpha8 Capabilities UI invariant missing: {required}')

for required in (
    '/api/model/info', '/api/model/options', '/api/model/set',
    '/api/skills/content', '/api/skills/hub/official', '/api/skills/hub/install',
    '/api/tools/toolsets', '/api/mcp/servers', '/api/mcp/catalog', '/api/env',
    '/api/providers/custom-endpoints', '/api/providers/oauth'
):
    if required not in rest:
        errors.append(f'alpha8 capabilities REST invariant missing: {required}')

for required in (
    'CapabilitySnapshot', 'CapabilitySkillDocument', 'CapabilitySkill', 'CapabilityToolset',
    'CapabilityMcpServer', 'CapabilityMcpCatalogEntry', 'CapabilityEnvVar',
    'CapabilityCustomEndpoint', 'CapabilityOAuthProvider'
):
    if required not in models:
        errors.append(f'alpha8 capabilities model invariant missing: {required}')

for required in (
    'capabilitiesGeneration', 'refreshCapabilities', 'loadCapabilitiesSnapshot',
    'openCapabilitySkill', 'setCapabilityModel', 'setCapabilitySkillEnabled',
    'setCapabilityToolsetEnabled', 'addCapabilityMcpServer', 'setCapabilityEnvVar',
    'saveCapabilityCustomEndpoint', 'startCapabilityOAuth', 'device_code'
):
    if required not in vm:
        errors.append(f'alpha8 capabilities ViewModel invariant missing: {required}')

if 'generation != capabilitiesGeneration' not in vm or 'uiState.capabilityProfile != profile' not in vm:
    errors.append('alpha8 capability mutations must reject stale profile/generation results')

# alpha8.1 Basic-auth input/session hotfix invariants.
if 'username.trim()' in vm:
    errors.append('alpha8.1 Basic-auth username must be sent exactly as typed')
if '/api/auth/me' not in (auth if auth_path.exists() else '') or 'validatePasswordSession' not in (auth if auth_path.exists() else ''):
    errors.append('alpha8.1 Basic-auth login must validate the minted session with /api/auth/me')
if connection_ui_path.exists():
    connection_ui = connection_ui_path.read_text()
    connection_ui = with_resource_labels(connection_ui)
    for required in ('KeyboardCapitalization.None', 'autoCorrectEnabled = false', 'KeyboardType.Ascii', 'KeyboardType.Password', 'VisibilityOff', 'sent exactly as you type'):
        if required not in connection_ui:
            errors.append(f'alpha8.1 credential-input invariant missing: {required}')

# alpha8.2 gateway-contract drift compatibility invariants.
compat_path = ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/data/GatewayCompatibility.kt'
if not compat_path.exists():
    errors.append('alpha8.2 GatewayCompatibility.kt missing')
else:
    compat = compat_path.read_text()
    for required in ('unsupportedparams', 'missingmethods', '4000', '-32602', '-32601', 'extra inputs are not permitted'):
        if required not in compat.lower():
            errors.append(f'alpha8.2 gateway compatibility invariant missing: {required}')
    if 'validation failures about the value' not in compat.lower():
        errors.append('alpha8.2 compatibility must not downgrade value-validation errors')

if 'compatibility.requestObject' not in resume or '"inline_images" to false' not in resume:
    errors.append('alpha8.2 session.resume must use optional-field compatibility fallback')


# alpha9 model persistence + source-aware Chats + fleet Profiles/RoomLink invariants.
for required in ('config.set', '"key", "model"', ' --session', 'createRoutedGroupRoom', 'groups.peer.invite', 'groups.peer.register', 'groups.peer.revoke'):
    if required not in repo:
        errors.append(f'alpha9 model/RoomLink repository invariant missing: {required}')
for required in ('SessionSourceKind', 'sessionSourceInfo', 'RoutedBot', 'RoomLinkStatus', 'capabilityDigest'):
    if required not in models:
        errors.append(f'alpha9 fleet/source model invariant missing: {required}')
for required in ('sessionModelPins', 'ensureOnNextPrompt', 'refreshBotRoster', 'createManagedProfile', 'renameManagedProfile', 'importManagedProfile'):
    if required not in vm:
        errors.append(f'alpha9 ViewModel invariant missing: {required}')
for required in ('groupSessions', 'sessionSectionLabel', 'SessionSourceAvatar', 'PhoneAndroid', 'DesktopWindows'):
    if required not in chat:
        errors.append(f'alpha9 source-aware Chat UI invariant missing: {required}')
profiles_ui = ROOT / 'app/src/main/java/com/nousresearch/hermesmobile/ui/screens/ProfilesScreen.kt'
if not profiles_ui.exists():
    errors.append('alpha9 ProfilesScreen.kt missing')
else:
    profile_text = profiles_ui.read_text()
    profile_text = with_resource_labels(profile_text)
    for required in ('Create profile', 'Rename profile', 'Export archive', 'Import profile archive'):
        if required not in profile_text:
            errors.append(f'alpha9 Profiles UI invariant missing: {required}')
if 'allowRemote = capabilities?.roomLink?.enabled == true' not in (group_ui_path.read_text() if group_ui_path.exists() else ''):
    errors.append('alpha9 remote Group members must be gated on advertised RoomLink capability')
if 'RemoteGatewaySession' not in repo:
    errors.append('alpha9 RoomLink setup must use an isolated secondary-gateway session')

if errors:
    print('\n'.join(errors)); raise SystemExit(1)
print('Hermes Mobile source structure: OK')
