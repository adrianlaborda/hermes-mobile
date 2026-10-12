# Hermes Mobile · 0.2.0-alpha26

**The context ring fills for reopened chats.** The gateway measures context occupancy only after a turn has run in its process, so a chat reopened from the list, or any chat after a gateway restart, reported no window and the composer ring stayed at "–" until the next answer. Between turns the app now also asks `session.context_breakdown` (measured when available, otherwise estimated from prompt, tools and transcript), as Desktop's gauge does. During a turn the live `session.usage` ticks still drive the ring. Usage is cleared when you switch chats, so a ring never shows another chat's figure. A session whose agent the gateway has not built yet still shows "–" until its first turn. Unit-tested. Not yet checked on a device. Version code 32.

# Hermes Mobile · 0.2.0-alpha25

**Notifications with real content, your lock-screen choice, and every profile.** FCM still carries only an opaque ID. When the push arrives, the app fetches the event from your server within a few seconds and shows its real title and text, for example the chat title and an excerpt of the answer. If the server is unreachable, it posts a generic notice and replaces it once the content arrives. A switch in **Notifications** decides whether that content shows on the lock screen (on by default; off keeps a generic public version). A turn notice for the chat you are reading is skipped. The Notifications screen gains a profile picker: each profile pairs separately, and the app binds plugin commands to that profile's most recent conversation without creating new chats. Generic server texts are localized. Requires the `hermes-android` plugin 0.5.0, which adds chat titles and answer excerpts, `notify_turns` and per-profile setup from chat. Version code 31.

# Hermes Mobile · 0.2.0-alpha24

**Bring-your-own-Firebase notifications with guided setup.** The APK no longer needs Firebase identifiers at build time: users import their own project's `google-services.json` in **Notifications**, and Firebase starts with it at runtime. The Notifications screen is now a step-by-step setup:
1. **Server plugin check.** The app asks the server through `/android-pair`. If anything is missing, **Ask Hermes** opens a chat with the request prefilled.
2. **Import the Firebase config.**
3. **Upload the server key.** It goes to the server through `/android-credentials` over the authenticated connection, never through the chat or the model.
4. **Notification permission.**
5. **One-tap pairing.** The secret is fetched over the authenticated connection; nothing is copied by hand.

Paired phones get **Send test**. The manual URL + secret form remains as a fallback. The push endpoint follows the chat's transport rule (HTTP only on private networks) and is prefilled as the chat host on port 8642. The plugin was renamed to `hermes-android` (route `/api/platforms/hermes_android/events`). The default applicationId is now `io.github.adrianlaborda.hermesmobile` (overridable with `-Phermes.applicationId`), so this installs as a new app. Requires the `hermes-android` plugin 0.4.0. Version code 30. See [UX_ALPHA24.md](UX_ALPHA24.md), [VALIDATION_ALPHA24.md](VALIDATION_ALPHA24.md) and [docs/NOTIFICATIONS.md](docs/NOTIFICATIONS.md).

# Hermes Mobile · 0.2.0-alpha23

**Instant `/` palette and a live context window.** Typing `/` lists every command grouped by category (skills last), and the list narrows locally on each keystroke from the gateway's `commands.catalog`, with prefix/alias/description/fuzzy matching and highlighted matches. The composer's context ring now moves live during a turn (`session.usage` events), is always visible and is colored by pressure. Tapping it opens a Hermes Go-style context sheet: big ring, used/max/free tokens, stacked category breakdown with shares, session usage tiles (tokens, cache hits, tok/s, latency, compressions), context files and a pressure-aware *Compress now*. Home is again the first drawer entry and the start page. Back returns to Home, and the app reopens on the page and chat you left if Android killed it in the background. Unit-tested; not yet run on a device. Version code 29. See [UX_ALPHA23.md](UX_ALPHA23.md) and [VALIDATION_ALPHA23.md](VALIDATION_ALPHA23.md).

# Hermes Mobile · 0.2.0-alpha22

**Desktop parity and polish.** Assistant answers get a toolbar (copy, share, read aloud, retry, branch); `/` opens Desktop's command and skill popover backed by the gateway, with output shown in a sheet; chat rows swipe to pin/archive and long-press for actions; the app opens on Chats and Home is now a dashboard; images open full-screen with zoom and no duplicated captions; Markdown tables render correctly; tablets get a two-pane list + conversation; remaining chat/dialog literals moved to English/Spanish resources; haptics and predictive back for the drawer. Validated on an API 36 emulator against an isolated gateway; physical-device confirmation pending. Version code 28. See [UX_ALPHA22.md](UX_ALPHA22.md), [VALIDATION_ALPHA22.md](VALIDATION_ALPHA22.md) and [REVIEW_ALPHA22.md](REVIEW_ALPHA22.md).

# Hermes Mobile · 0.2.0-alpha21

**Chat scroll loop fixed structurally.** The transcript is now a reverse-layout list that rests on the latest message by itself; all automatic "follow" scrolling (the source of the jump-back loop) is gone, and Markdown/image rows keep their size when scrolled back into view. Adds a jump-to-latest pill with new-message count, automatic paging of older history, a Desktop-style composer card with model · reasoning pill and context ring, a drawer with recent conversations, a chats list grouped by recency (Pinned/Today/Yesterday/7 days/Older + per-platform channels), instant new chat and a readable snackbar. Removes the alpha20 debug trace. Validated on an API 36 emulator against an isolated gateway with synthetic long sessions; physical-device confirmation pending. Version code 27. See [UX_ALPHA21.md](UX_ALPHA21.md), [VALIDATION_ALPHA21.md](VALIDATION_ALPHA21.md) and [REVIEW_ALPHA21.md](REVIEW_ALPHA21.md).

# Hermes Mobile · 0.2.0-alpha20

Diagnostic-only update for the user-reported alpha19 loop during fast downward history flings. Debug builds emit `HermesScrollTrace` records with numeric geometry and scroll/effect events; release builds emit no trace. Idle and fling layout changes are sampled at up to 20 Hz; actual follow decisions and `scrollToLatest` calls are not throttled. This is instrumentation, not a fix. No physical reproduction or runtime trace-format/rate validation is claimed. Version code 26. See [UX_ALPHA20.md](UX_ALPHA20.md), [VALIDATION_ALPHA20.md](VALIDATION_ALPHA20.md) and [REVIEW_ALPHA20.md](REVIEW_ALPHA20.md).

# Hermes Mobile · 0.2.0-alpha19

Chat list and initial-position state now use the durable conversation identity (profile + stored session ID), not the gateway runtime session handle. Reconnecting/resuming the same conversation should therefore not reset the transcript position; saveable position initialization avoids overriding a saved history location. The user confirmed alpha18 still jumped to latest while reading completed/idle chats. Runtime-ID churn is a source-supported candidate, not a device-confirmed event cause; route re-entry and missed follow tracking remain alternatives. No device validation is claimed. Version code 25. See [UX_ALPHA19.md](UX_ALPHA19.md), [VALIDATION_ALPHA19.md](VALIDATION_ALPHA19.md) and [REVIEW_ALPHA19.md](REVIEW_ALPHA19.md).

# Hermes Mobile · 0.2.0-alpha18

Chat transcript auto-follow now tracks scroll progress and per-session follow intent, so observed user scrolling into history disables follow while new rows append; returning to the tail resumes it. Opening or switching sessions retains latest-message initialization. Automated JVM/build checks pass, but no device validation was performed and the reported jump-to-beginning was not reproduced, so this is not a claim that the literal symptom is conclusively fixed. Version code 24. See [UX_ALPHA18.md](UX_ALPHA18.md), [VALIDATION_ALPHA18.md](VALIDATION_ALPHA18.md) and [REVIEW_ALPHA18.md](REVIEW_ALPHA18.md) for scope and evidence.

# Hermes Mobile · 0.2.0-alpha14

Navigation and shared UI refresh, project workspaces and their chats, local chat folders, searchable model sheets, real context breakdown, server health and logs. Version code 20. See `UX_ALPHA14.md` for exact scope, compatibility, validation and remaining work. This release does not claim complete Hermes Go/Desktop parity.

# Hermes Mobile · 0.2.0-alpha13

Connection and shared visual refresh using the original Desktop icon; persistent light/dark/system appearance; compact chat filters; Chats-tab return-to-list; corrected nullable activity grouping and a bounded pinned preceding-message preview while scrolling activity. Existing audio playback and compact composer retained. Version code 19. See `UX_ALPHA13.md` for design scope and validation.

# Inline audio · 0.2.0-alpha12.3

Generated speech can now be played directly in the chat with play/pause, seeking and timing. The player is visible outside the collapsed tool activity and supports official TTS file results and local Markdown audio links. Downloads are explicit, bounded and profile/session scoped; playback pauses in the background. See `AUDIO_CHAT_ALPHA12_3.md` for scope and validation. Version code 18; prior chat UX improvements retained.

# Tool activity UX · 0.2.0-alpha12.2

Consecutive reasoning/tool-only transcript rows now appear as one collapsed activity row. Tapping it opens a scrollable sheet with individual steps and expandable details. Visible assistant updates, final answers, user messages and timeline notices retain their ordering outside activity groups. Streaming/error tool status uses the same compact presentation; completed live status is not repeated underneath the durable transcript. English/Spanish labels replace repeated Reasoning / Show details blocks.

Version code 17. See `TOOLING_UX_ALPHA12_2.md`. The compact composer and keyboard inset changes from alpha12.1 are retained. Historical release notes follow.

# Chat UX revision · 0.2.0-alpha12.1

- Compact composer: actions menu, expanding text input (up to four lines), and a contextual microphone/send button. Stop remains visible during a running response; queue sending and steering remain available.
- Attachments, dictation with an existing draft, and read-aloud of the latest assistant response are available from the + menu. Recording/playback status appears only while relevant, with explicit stop/cancel.
- Activity, artifacts and queued-message management scroll with the conversation instead of consuming fixed space above the editor.
- Hide bottom navigation while the software keyboard is visible. Consume Scaffold insets before the chat applies IME padding, avoiding duplicated bottom spacing. Group chats also apply IME padding.
- Voice work is scoped to the active profile/session and cancelled on disposal. Drafts, uploads, routing and backend contracts are retained.
- New composer labels are available in English and Spanish. Version code 16.

See `CHAT_UX_ALPHA12_1.md` for validation and remaining physical-device checks. Historical alpha12 notes follow.

# Hermes Mobile (Android)

Android remote client for **Hermes Agent**. The app deliberately contains **no local Hermes/Python backend**: all agent execution, tools, memory, sessions, profiles, cron and messaging stay on the connected remote Hermes gateway.

The implementation follows the current upstream `NousResearch/hermes-agent` contracts used by Hermes Desktop:

- REST for profiles, stored sessions, cron, messaging, Telegram onboarding and pairing.
- `tui_gateway` JSON-RPC over `WebSocket /api/ws` for live chat/session control and streaming events.
- `client.capabilities {server_requests:true}` after `gateway.ready`, so approvals / clarify / sudo / secret prompts can be answered from Android.
- Profile-backed Bot Mode: a Bot is a Hermes profile and opens its canonical **Bot Chat**.


## Alpha12 highlights

Version **0.2.0-alpha12**, versionCode **15**. Continues the delivered alpha11.
The optional notification plugin was checked against official Hermes commit
`f15c19c25f7409e749d6b38d5232f8969b78325e`; existing chat feature contracts retain
the alpha10/11 baseline, with their regression tests.

- **Optional self-hosted push:** BYO Firebase, per-connection/profile registration,
  Keystore-encrypted secrets, runtime notification permission, private notices,
  authenticated inbox, bounded WorkManager recovery, and stored-session navigation.
  The companion Hermes Assistant plugin 0.2.0 uses official extension points,
  without a Hermes fork. See [notification setup](docs/NOTIFICATIONS.md).
- **Richer answers/artifacts:** aligned native tables, copyable code, local KaTeX
  display-math and Mermaid previews, bounded on-demand inline gateway-image previews,
  authenticated artifact links, paged PDF preview and local audio/video playback.
  Existing open/save/share uses SAF/FileProvider. Model HTML is not executed.
- **Language:** Spanish/English Android resource catalogs for navigation and common
  screens/actions; system or explicit app language. Technical backend messages,
  provider-defined schema labels and some advanced dynamic descriptions remain
  server-supplied or untranslated; this is not a claim of exhaustive localization.
- **Mobile review:** adaptive navigation labels, one reconnection banner, protected
  notification pairing screen, profile ownership through notification retrieval,
  immediate local sign-out removal and best-effort server revocation.
- **Validation:** [VALIDATION_ALPHA12.md](VALIDATION_ALPHA12.md) records the exact
  builds, tests and limits. [REVIEW_ALPHA12.md](REVIEW_ALPHA12.md) records the review.

The supplied APK has **no Firebase project configured**. To enable push, build it
with your project's public Android identifiers and configure your own server-side
service account. No credentials, private keys or hosted relay are included.

## Alpha11 highlights (historical)

Version `0.2.0-alpha11`, versionCode **14**. Continues alpha10; the backend reference remains commit `47676981f55de91231fdeef2f0eec47e7c209e78` (not a claim about every later upstream revision).

- **Readable chat:** native CommonMark rendering for bold/italic, headings, ordered/nested lists, quotes, links, inline/fenced code, tables and strikethrough. Parsing runs off the UI thread; tables/code scroll horizontally and text can be selected. Used in individual and group chats. Raw HTML is not executed and remote Markdown images are represented by their alternative text rather than fetched automatically. This is not a Mermaid/LaTeX/HTML browser renderer.
- **Clean transcript:** typed protocol blocks, metadata and encoded SQLite sidecars are projected into visible text. Analysis/tool-call envelopes are not printed as assistant answers. Real JSON supplied as ordinary answer text or fenced code is preserved. Reasoning and tool output start collapsed. Empty technical rows no longer terminate history pagination on gateways without explicit pagination counts.
- **Interactions:** clarify choices, multiple selection, custom answers, replayed locked answers and the actual Desktop answer encoding; explicit remote-vault login form; scrollable long questions/commands; approval scope follows gateway flags. Sensitive prompts use secure dialog windows and remain ephemeral.
- **Android share-in:** receive text/links and up to six documents, 25 MiB each. Incoming files are bounded copies in private app storage. Choose/open a conversation and explicitly add content to its draft; nothing is automatically sent. Existing drafts are appended to, not replaced. Discard deletes staged files; a cold-start cleanup removes abandoned copies older than 24 hours while retaining stored draft/queue references.
- **Review fixes:** immediate draft-save scheduling on background, stable streaming identity, reasoning-only streaming visibility, history-aware scroll target, keyboard insets, reconnection banner, credential masking in generic settings and indexed Star Map relationship lookups.

See [VALIDATION_ALPHA11.md](VALIDATION_ALPHA11.md), [REVIEW_ALPHA11.md](REVIEW_ALPHA11.md) and [DEVICE_TEST_PLAN_ALPHA11.md](DEVICE_TEST_PLAN_ALPHA11.md). Compilation and JVM tests do not replace a connected-device/gateway trial.

## Alpha10 highlights

Reference backend: `NousResearch/hermes-agent` commit `47676981f55de91231fdeef2f0eec47e7c209e78`. Wire contracts were inspected in that source tree. See [CONTRACTS_ALPHA10.md](CONTRACTS_ALPHA10.md) for routes and ownership rules.

- **Recovery:** foreground/network-aware reconnect, fresh one-use tickets, serialized rotating-token refresh, bounded jittered retries, handshake deadline, negotiated heartbeat, stale-socket and stale-resume fencing. Android suspension does not imply expired authentication. Drafts, queued messages and session model pins survive transport replacement. A rejected socket ticket receives one fresh-ticket retry before requiring sign-in.
- **Memory / Learning:** provider selection, declared settings and supported OAuth; curator status/pause/run; searchable Star Map list with relationships and node detail/edit/delete. Learning mutations include the selected profile in their declared request body. Concurrent edits are checked before saving.
- **Plugins:** gateway catalog, installed/runtime status, install/toggle/catalog update/remove, declared settings and context engine selection. Expanded update capabilities require explicit consent. Android does not execute a package's Desktop renderer half.
- **Models:** auxiliary slots with model/provider/reasoning/inherit/reset; complete MoA preset editor for aggregators, references, enabled state, default/active preset, temperatures, timeout, degraded policy and fanout. Existing session-scoped model pinning is retained.
- **Files:** gateway managed-file browsing, folder creation, bounded upload, text editing and deletion; profile workspace browsing/text editing/download; image preview and Android open/share/save via SAF and FileProvider. Artifacts retain the chat profile/session. Managed files and profile workspaces use their distinct backend contracts; host mutations are not sent to an SSH workspace by accident.
- **Voice:** Android microphone permission, temporary AAC recording, real STT/TTS requests and leases, playback and read aloud. Recording stops in the background; audio is deleted after use and orphaned audio is removed on a cold start. GPT-Live is explicitly represented by the supported chained fallback in this release.
- **Settings:** backend-declared profile settings, MCP OAuth and complete-map editing with stale-write checks, usage summaries, diagnostics/security-audit/backup actions.
- **UI:** official Nous blue/neutral palette, light/dark surfaces, compact cards, navigation rail on larger displays, mirrored directional icons and modern clipboard support. Alpha9 chat grouping by source remains intact.
- **Security:** public HTTP remains rejected; private-host detection now requires a valid IP literal rather than an `fc`/`fd` hostname prefix. Credential-bearing requests do not follow redirects. OAuth callback reads and file/JSON transfers are bounded; exported content uses temporary read grants.

### Practical limits

- Transfer caps: 64 MiB streamed upload/download, 8 MiB legacy JSON upload, 48 MiB JSON response. Text editing is refused for truncated previews.
- `/api/files*` manages gateway-host paths. `/api/fs*` can target an SSH workspace, but the inspected contract does not expose SSH upload/mkdir/delete. Those host mutations are offered only in Managed files.
- Memory-provider OAuth may open the browser on the gateway computer, as defined by that provider. MCP/native gateway OAuth uses the Android system browser where supported.
- Missing optional methods/endpoints show an unsupported-version state. Validation/authentication errors do not silently remove routing fields. Strict-schema alpha8.2 RPC downgrade is retained.
- Read-before-write conflict checks are best effort where the backend has no revision/CAS contract; they cannot eliminate a write occurring between the final read and write.
- Full Desktop equivalence still needs end-to-end verification against a real gateway and Android device. Build success does not establish runtime or visual parity.

## Core features retained through v0.2.0-alpha12

### Chats
- Read the gateway's unified cross-profile conversation list.
- Filter by profile or show all profiles.
- Resume a conversation created by Desktop/TUI/other Hermes surfaces.
- Create a new conversation under any profile.
- Live token streaming through `message.delta` / `message.complete`.
- Live tool activity (`tool.start`, `tool.generating`, `tool.complete`).
- Preserve the event-envelope `session_id`, so concurrent live sessions cannot leak stream events into the foreground transcript.
- Reconnect automatically after Android sleep/network changes and resume the active durable session.
- Stop a running turn (`session.interrupt`).
- Steer a running turn (`session.steer`).
- Handle server-side approval, single/batch clarify and secret/sudo prompts using the gateway's peer-to-peer JSON-RPC request contract.


### Sessions/chat hardening added in v0.2.0-alpha2
- Tail-first transcript hydration: open only the latest 120 durable rows, then load older pages on demand.
- `session.resume` uses `defer_history`, `omit_messages` and `inline_images=false`; REST owns transcript paging.
- Reconnect/history publication waits on the gateway replay barrier so REST cannot race ahead of missed live events.
- Durable `row_id` identities are retained for transcript deduplication and live-to-stored reconciliation.
- `display_kind=hidden` and non-display system rows are suppressed; timeline display kinds render as markers.
- Responses-style `display_content` / `codex_message_items` text is projected instead of dumping raw JSON where possible.
- Streaming deltas are coalesced (~32 ms) outside the global UI state, reducing whole-app recomposition and string churn.
- Auto-follow stops when the user scrolls away from the bottom.
- Foreground navigation/search/refresh generations reject stale async results.
- Session search, pagination, rename, pin (using compression lineage root), archive, read/unread, delete and whole-session branch are available.
- Manual context compression is available for idle sessions.
- Sending while a turn is busy now respects the gateway's `streaming` / `queued` / `steered` / `redirected` result instead of being client-blocked.
- Chat completion refreshes chat/session state only; Cron/Telegram are no longer re-fetched after every answer.
- Normal chats no longer inherit Bot Mode's `follow_profile_config=true`; canonical Bot Chat keeps that behavior.

### Bots — individual Bot Mode core completed in v0.2.0-alpha3
- List only profiles carrying `ui_meta["hermes-bots"]` as the Bot roster; hidden bots stay out of the normal roster and pinned bots sort first.
- Create a profile-backed Bot, optionally cloning another profile, while sharing the gateway auth pool like current Desktop Bot Mode.
- Canonical `Bot Chat` identity follows the current name-registry contract: exact `Bot Chat` lookup, hidden session, `follow_profile_config`, eager `session.title`, **single-flight creation** and adopt-before-mint on cross-client title races. No client-side chat pointer is stored.
- Creation probes `setup.runtime_check`; the one-time introduction is submitted only when the new profile has a usable runtime (or as the narrow compatibility persistence fallback for older gateways).
- Open the canonical Bot Chat or create an ordinary side chat with the same bot profile.
- Edit Bot Mode title/appearance metadata with per-key `ui_meta_expected_revisions` CAS, preventing a stale Android editor from overwriting a newer Desktop/mobile write.
- Edit description and `SOUL.md`.
- Read the gateway model inventory and change provider/model, including Hermes' expensive/data-policy confirmation handshake.
- Enable/disable installed Skills, toolsets and configured/catalog MCP entries through the consolidated `profiles.describe` / `profiles.configure` contract.
- Delete non-default bots from the gateway with confirmation.

### Bots — hosted Group Chats, mentions and avatar assets added in v0.2.0-alpha4
- Use the gateway-owned `groups.*` protocol for durable Group Chats instead of reproducing the old Desktop-side room scheduler on Android.
- Discover `groups.capabilities`, list existing hosted rooms and create same-gateway rooms with **2–6** Bot profiles.
- The gateway owns room identity, log and driver state in `shared-state.db`; Android can leave the screen or close while the hosted driver continues.
- Read the monotonic room log by `seq`, keep a bounded recent mobile tail and poll sequentially without letting stale responses move the cursor backwards.
- Send new room discussions and replies using the room's durable `thread_id`; member `@handle` chips target the same mention identities understood by the hosted Discussion policy.
- Render member/user messages plus meaningful failed/deferred/cancelled room status without manufacturing local turn state.
- Surface exact hosted-room approvals and retries from `groups.state`; approvals use the gateway's restricted `once` / `deny` choices and preserve `member_id`, `task_id`, `execution_generation` and `request_id`.
- Stop room work, rename rooms and permanently disband/tombstone a room through the official RPCs.
- Normal Bot/chat composers now resolve local `@bot` references the same way as Desktop's mention middleware: Android adds model-only identity context and leaves `message_agent` as the sole bot-to-bot delivery path.
- Profile avatars use `profiles.get_asset` / `profiles.set_asset`, are fetched lazily for visible rows, decoded with sampling, and selected photos are resized/compressed before the gateway's 2 MB limit. Avatar bytes never ride `ui_meta`.
- Because the hosted protocol currently has no `groups.update_members`, room membership is intentionally immutable after creation instead of being faked by deleting/recreating a room.

### Routines / Cron — completed in v0.2.0-alpha5
- Unified list across profiles, profile-aware mutations and search.
- Create and **edit** scheduled jobs from natural schedule strings or cron expressions.
- Pause/resume, trigger now and delete while preserving the owning profile on every mutation.
- Rich details: state, schedule, delivery, model/provider pins, skills, repeat progress, next/last run and last error.
- Run-history lookup through the remote gateway, with graceful fallback when an older gateway does not expose the history endpoint.
- Delivery-target discovery from Hermes instead of hard-coding Telegram/local; raw target syntax remains available for advanced destinations.
- Automation blueprint discovery and dynamic instantiation forms.
- Advanced job fields exposed by the current gateway: model/provider pin, reasoning effort, skills, pre-run script, no-agent mode, workdir and repeat count.
- New and legacy timestamp compatibility: ISO-8601 and epoch values are both accepted.

### Channels / Telegram / Webhooks — completed in v0.2.0-alpha5
- Replaces the Telegram-only surface with Hermes' generic **Channels** catalog, scoped to the selected profile.
- Render each platform's server-advertised setup fields rather than embedding a fixed credential form. This covers Telegram and the other platforms exposed by the connected gateway.
- Secret fields are never rehydrated into the UI; blank keeps the existing value and an explicit clear action removes it.
- Enable/disable and connection test for every advertised platform.
- Respect Hermes' `hot_served` response after configuration changes and expose an explicit, confirmed remote messaging-gateway restart for changes that need one.
- Telegram guided onboarding: start, safe deep-link launch, bounded status polling, apply owner allowlist and cancel.
- Pairing administration: pending codes, approve, approved users, revoke and clear pending requests.
- Webhook administration: list, create, enable/disable and delete dynamic subscriptions. The one-time route URL/HMAC secret returned at creation is shown for copying and deliberately **not persisted** by Android.
- Optional newer surfaces (run history, delivery targets, blueprints and webhooks) degrade safely when connected to an older compatible gateway.

### Authentication + multiple gateways — completed in v0.2.0-alpha6
- **URL-first onboarding**: Android probes public `/api/status` and `/api/auth/providers` before asking for credentials, then exposes only the authentication modes advertised by that gateway.
- Password providers such as `basic` use `/auth/password-login`. The password is transient and is **never persisted**; only the resulting authenticated session cookies are stored.
- OAuth/OIDC-capable gateways advertising `native_pkce` use the RFC 8252 native flow: S256 PKCE, high-entropy `state`, system browser, one-shot `127.0.0.1` loopback callback, `/auth/native/token`, and rotating `/auth/native/refresh` tokens.
- Native access/refresh tokens, Basic-auth session cookies, legacy session tokens, proxy headers and per-connection profile selection are stored inside the AES-GCM Android-Keystore encrypted connection registry.
- Current auth-gated gateways mint a fresh single-use `/api/auth/ws-ticket` for **every WebSocket connect/reconnect**; only older token-only gateways fall back to `/api/ws?token=…`.
- A 401/terminal native-refresh failure clears the stale auth material for that connection and returns to capability-aware sign-in rather than retrying forever.
- Session token remains available as an **advanced compatibility path**, not the primary login UX.
- Multiple remote gateways can be saved, switched and forgotten independently. Each keeps its own URL, authentication state and selected Hermes profile.
- Switching gateways resets connection-owned UI/cache state and increments a connection generation so late REST/WS results from the previous gateway cannot overwrite the new gateway's state.
- The alpha1-alpha5 single encrypted gateway record migrates automatically into the multi-connection registry.
- Optional access-proxy headers (for example Cloudflare Access) are supported, excluding transport/Hermes-managed auth headers.
- Public gateways require HTTPS/WSS. Alpha7 relaxes the transport policy specifically for private/local LAN destinations so remote self-hosted Hermes on RFC1918 addresses can be used in release builds.

### LAN connection repair + Chat parity — added in v0.2.0-alpha7
- Gateway onboarding is now **URL-first** in the normal path. Connection name and access-proxy headers are hidden under Advanced settings; the name defaults to the gateway hostname.
- Inputs such as `192.168.1.10:9119` are normalized automatically. Private/loopback/link-local destinations may use HTTP/WS; public hosts still require HTTPS/WSS.
- Android cleartext transport is enabled at the platform layer solely so runtime validation can permit RFC1918/LAN gateways. Every app connection path still rejects public cleartext URLs before constructing REST/WebSocket clients.
- Connection-refused diagnostics explicitly call out Hermes' loopback-only default and the remote bind requirement (`hermes serve --host 0.0.0.0 --port 9119`).
- Per-conversation drafts and the mobile pending-send queue are persisted **AES-GCM encrypted with Android Keystore**. Messages typed while a turn is running are queued locally, can be reordered/edited/removed, and drain sequentially after the prior turn completes.
- Remote-safe attachments use `image.attach_bytes`, `pdf.attach` and `file.attach` with local Android bytes/data URLs; the gateway never receives an Android filesystem path. Up to 6 attachments are staged per turn with a 25 MB per-file client cap.
- Live transcript handling now includes reasoning/thinking deltas, `tool.progress`, browser/subagent/background/status events and expandable tool activity.
- Session model inventory, per-session model switching, reasoning effort and `session.usage` context/tokens are exposed from the chat surface.
- Successful tool completions are scanned for gateway artifact paths and surfaced as lightweight artifact suggestions (tap to copy the remote path); full remote artifact download/preview remains a later file-browser surface.
- The tail refresher no longer forces `running=false`, preventing a late history refresh from racing the next queued turn.



### Basic-auth login hotfix — v0.2.0-alpha8.1
- Username and password fields now use credential-specific Android keyboard options: no autocorrect, no automatic capitalization, ASCII username entry and password keyboard semantics.
- The username is sent to Hermes **exactly as typed**; the client no longer trims or otherwise rewrites it before `POST /auth/password-login`.
- Password visibility can be toggled locally to verify the exact characters before sign-in. The password is still never persisted.
- A successful password POST is not trusted on its own: Hermes Mobile immediately verifies the newly minted cookie session through protected `GET /api/auth/me` before proceeding to the normal protected REST proof, one-time WS ticket and `gateway.ready` handshake.

### Capabilities — added in v0.2.0-alpha8
- Adds a profile-scoped **Capabilities** surface for the active remote gateway. Every read/write is fenced by connection + capability generation so a stale response from another gateway/profile cannot repaint the current surface.
- **Models / Providers:** current default model, provider/model inventory, global profile default switching, provider OAuth account state, and custom OpenAI-compatible/Anthropic/Codex-style endpoints with server-side validation, activation and deletion.
- **Keys:** server-advertised credential registry from `/api/env`; only redacted/set state is read. New secret values exist only in transient Compose state and are sent directly to the gateway. Messaging-managed credentials stay on the richer Channels surface.
- **Skills:** installed list, enable/disable, full remote `SKILL.md` view, official/Hub browse/search, install/uninstall and update actions.
- **Tools / Toolsets:** list, readiness/configuration state, enable/disable, provider selection and provider credential entry using the gateway-advertised schema.
- **MCP:** configured server list, HTTP/stdio add, enable/disable, test/discovered tool count, remove, catalog browse and catalog install with required environment secrets. MCP runs on the gateway, never on Android.
- Older gateways degrade per subsection: unsupported APIs disable only the corresponding Capabilities area instead of breaking the rest of the app.
- Provider `device_code` OAuth is intentionally not initiated from Android in this alpha because current Hermes has an upstream late-authorization/profile-routing race after cancel/expiry; API-key setup or Desktop/CLI remains available for those providers. PKCE provider flows remain supported.
- Full in-place MCP map editing is intentionally not reconstructed from `McpServerSummary`: the summary omits secret-bearing/full config fields, so Android uses the safe structured add/remove/toggle/test APIs rather than risking destructive replacement.


## UI / UX

The mobile shell intentionally follows the Hermes Desktop design language rather than stock Android cards:

- chat remains the home surface;
- flat lists, whitespace and subtle hairlines instead of card-in-card layouts;
- Hermes dark/light palette with restrained gold accent;
- compact profile/bot roster and status dots;
- the same product nouns: Chats, Bots, Routines, Channels, Gateway;
- tool activity stays attached to the transcript;
- background events update state without stealing the foreground conversation;
- short functional transitions and native Android touch targets.

The current launcher/hero mark is a temporary Hermes `H` squircle. The upstream Nous `nous-girl` brand asset is intentionally not copied into this independent project yet; replacing it is isolated to `HermesBrandMark` / `ic_hermes.xml`.

## Gateway compatibility

This build targets the current Hermes `hermes serve`/Desktop gateway API as of **2026-09-30**. It is a remote UI, not a second agent runtime.

Authentication is capability-driven: Basic/password, native OAuth/OIDC PKCE and legacy session-token gateways are supported as advertised by `/api/status` and `/api/auth/providers`. Auth-gated WebSockets use fresh single-use `/api/auth/ws-ticket` tickets when the gateway exposes them.

## Project structure

```text
app/src/main/java/com/nousresearch/hermesmobile/
├── MainActivity.kt
├── HermesMobileApp.kt
├── HermesViewModel.kt
├── data/
│   ├── GatewayConfigStore.kt       # Android Keystore encrypted connection config
│   ├── HermesRestClient.kt         # REST surface
│   ├── HermesGatewayClient.kt      # WebSocket JSON-RPC surface
│   ├── HermesRepository.kt         # Hermes-specific orchestration / Bot semantics
│   └── Models.kt
└── ui/
    ├── components/HermesPrimitives.kt
    ├── screens/
    │   ├── ConnectionScreen.kt
    │   ├── CapabilitiesScreen.kt
    │   ├── ChatScreen.kt
    │   ├── BotsScreen.kt
    │   ├── BotEditorDialog.kt
    │   ├── GroupChatScreen.kt
    │   ├── CronScreen.kt
    │   ├── MessagingScreen.kt
    │   └── MoreScreen.kt
    └── theme/
```

## Android toolchain

- AGP `9.4.1`
- Gradle `9.6.0`
- Kotlin / Compose compiler plugin `2.4.20`
- Compose BOM `2026.09.00`
- `compileSdk 37`, `targetSdk 36`, `minSdk 26`
- Java 17 bytecode
- OkHttp `5.5.0`

Alpha10 uses AGP built-in Kotlin and the matching Compose compiler plugin. Release builds enable R8 and resource shrinking.

## Build

Open the directory in Android Studio with Android SDK 37 installed and sync the project.

The complete Gradle 9.6.0 wrapper is included. Use JDK 21 and Android SDK 37 (platform package `platforms;android-37.0`). Set your own SDK path in `local.properties`, or use `ANDROID_HOME`.

```bash
python3 scripts/verify_source.py
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

The release APK is unsigned. Supply your own signing configuration for distribution; no signing key or local credentials are included. The debug APK is suitable for manual validation. See `VALIDATION_ALPHA12.md` for the actual checks performed on this snapshot.

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## First connection

1. Expose the same Hermes remote gateway that Hermes Desktop can connect to.
2. Enter only its base URL first, for example `https://hermes.example.com`, plus access-proxy headers if the deployment requires them.
3. Android reads `/api/status` and `/api/auth/providers`.
4. If Hermes advertises the Basic/password provider, sign in with **username + password**. The password is not saved.
5. If Hermes advertises an OAuth/OIDC session provider with `native_pkce`, tap the provider and complete sign-in in the system browser.
6. Use **Session token** only from the advanced compatibility section when connecting to a legacy/token-only gateway.
7. After REST auth and `gateway.ready` both succeed, the connection is persisted and normal Chats/Bots/Routines/Channels load from that remote gateway.

## Deliberate alpha limitations

Alpha10 implements the requested remote control surfaces with the adaptations listed above. It is not certified as complete Desktop parity. See `PARITY_PHASES.md` for the remaining gaps and `VALIDATION_ALPHA10.md` for test evidence.

Very old auth-gated gateways without `native_pkce` must use a supported password/session-token path; Android does not embed a credential-capturing WebView. Existing hosted-group membership stays frozen because the inspected backend has no member-update contract. Direct RoomLink setup from alpha9 remains available; advanced replica promotion diagnostics are still a gap.

## Parity roadmap / phased hardening

### Phase 1 — protocol + transport hardening (v0.2.0-alpha1)

Implemented in this source snapshot:

- Android backup is disabled. Cleartext transport is permitted by the Android manifest only because dynamic LAN IPs cannot be expressed as a static Network Security Config allowlist; runtime URL validation permits HTTP/WS only for private/loopback/link-local hosts and rejects public cleartext endpoints.
- Gateway credentials remain AES-GCM encrypted with an Android Keystore key; corrupted/unrestorable ciphertext is reported instead of silently looking like "no configuration".
- A connection is persisted only after `/api/status` **and** the real WebSocket `gateway.ready` handshake succeed.
- WebSocket callbacks are generation-scoped: stale callbacks from an old socket cannot mutate a replacement connection.
- Reconnect scheduling is single-flight and bounded (1s → 30s backoff).
- Hermes protocol heartbeat uses `gateway.ping` (15s interval / 45s liveness deadline) when advertised by `gateway.ready`, in addition to an OkHttp transport keepalive.
- `client.capabilities {server_requests:true}` is sent before the UI's ready callback can resume a session.
- `open_requests` returned by `session.resume` / replay RPCs are re-delivered before the RPC completes.
- Server→client prompts are queued/deduplicated by request id instead of overwriting one another; `request.cancel` withdraws only the matching prompt.
- Per-session event sequence watermarks are retained across reconnects and `session.events.since` is used for best-effort lossless replay with live-frame holding/deduplication.
- JSON-RPC errors preserve `code` and `data` in `GatewayRpcException`.
- Telegram onboarding external navigation accepts only `https:` and `tg:` links.
- The ViewModel closes the gateway scope on destruction and a reconnect resume may not steal a newer foreground chat.
- AGP 9 external-Kotlin compatibility is made explicit with `android.newDsl=false` until the build-system migration is done atomically.

Phase 2 (`v0.2.0-alpha2`) adds the session/chat correctness and performance layer described above. Phase 3 (`v0.2.0-alpha3`) adds the individual Bot Mode core: canonical creation races, kickoff/readiness semantics, server-CAS metadata, SOUL/model/Skills/toolsets/MCP editing and bot lifecycle actions. The Bot Mode follow-up (`v0.2.0-alpha4`) adds gateway-hosted same-gateway Group Chats, room approvals/retries/threading, local roster @mention resolution and lazy profile avatar asset sync. `v0.2.0-alpha5` completes the Routines/Channels control plane: Cron edit/details/history, delivery targets, blueprints, generic messaging configuration/testing, Telegram onboarding/pairing, gateway restart and dynamic webhooks. `v0.2.0-alpha6` adds capability-driven Basic/native-PKCE/session-token authentication, single-use WS tickets, encrypted multi-gateway persistence, per-connection profile routing and stale-connection fencing. `v0.2.0-alpha7` repairs private-LAN onboarding and completes the next Chat parity block: encrypted drafts/queue, remote-safe attachments, richer live status/tool/reasoning events, model/reasoning/context controls and basic artifact discovery. `v0.2.0-alpha8` adds the global/profile-scoped Capabilities core: model/provider defaults, custom endpoints, Keys, Skills/Skill Hub, toolsets and MCP management. `v0.2.0-alpha8.1` hardens Basic-auth credential entry and verifies the minted cookie session through `/api/auth/me` before connection handoff. `v0.2.0-alpha8.2` adds strict-schema downgrade for older gateways. `v0.2.0-alpha9` fixes first-turn session-model persistence, adds source-aware Desktop-style chat grouping, profile management, fleet Bot discovery and capability-gated cross-gateway RoomLink. Alpha10 adds memory/learning, plugins, auxiliary/MoA models, files/artifacts, chained voice, declarative settings and diagnostics; see the current parity matrix and validation report.

### v0.2.0-alpha9 — Bot/Profile fleet parity + RoomLink + Desktop-style Chats

- Fixes the per-conversation model switch race: Mobile now uses Hermes' session-scoped `config.set key=model` contract (the same control path as Desktop), reasserts a confirmed choice immediately before a lazy session's first prompt build, and fences stale `session.info` frames so the selector cannot jump back to the profile default. Older gateways still retain a bounded `slash.exec` compatibility fallback when `config.set` is genuinely absent.
- Chat list presentation now follows Desktop's source-aware philosophy instead of showing every conversation as the same profile row. Sessions are separated into **Pinned**, **Hermes apps** (Desktop/Mobile), per-channel groups such as Telegram/Discord/Slack, and **Other clients** (CLI/TUI/API/one-shot/etc.). Every row keeps an explicit source icon/label, and the active chat header carries its origin too.
- Bot Mode now builds a fleet roster from every authenticated saved gateway without changing the foreground connection. Remote bots show their owning gateway and can be reached by switching to that connection. Credential rotations discovered while probing secondary gateways are written back to the encrypted connection registry without changing the active gateway.
- Group Chat creation supports **RoomLink multi-gateway members** when both sides advertise the current `groups.peer.*` contract. Android asks the target gateway for a scoped room grant, creates the authoritative room on the active gateway, registers/probes each peer route, and revokes grants/disbands the room if setup fails part-way through. Same-gateway creation deliberately keeps the alpha4 wire shape so older strict-schema gateways remain compatible.
- Adds a Profiles management surface for create/clone, rename, delete, gateway-side export and import, alongside the existing advanced Bot editor for SOUL/model/Skills/toolsets/MCP.
- Multi-gateway RoomLink is capability-gated: remote members are disabled in the room picker when the active gateway does not advertise RoomLink, rather than pretending relay support exists.

### v0.2.0-alpha8.2 — gateway contract compatibility hotfix

Hermes Mobile no longer assumes that every reachable gateway is running the exact same RPC schema as the current upstream Desktop client. `session.resume` now classifies newer fields such as `inline_images`, `omit_messages`, `defer_history`, `source`, and `close_on_disconnect` as optional compatibility hints. If an older gateway answers Hermes `4000` / legacy JSON-RPC `-32602` and explicitly identifies one of those fields as an unknown/extra input, Mobile removes only that named optional field, retries in a bounded loop, and remembers the downgrade for the lifetime of that connection. Required identity/routing fields such as `session_id` and `profile` are never stripped.

The same compatibility helper also records JSON-RPC `-32601` method absence for optional future capability calls. It deliberately does **not** retry ordinary value-validation errors, authentication failures, or semantic RPC errors, so compatibility fallback cannot hide a real bad request.
