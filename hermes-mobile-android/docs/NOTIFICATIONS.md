# Notifications

Hermes Mobile can receive reminders, scheduled jobs, finished turns and approval requests from your own Hermes server,
just as Telegram does. Everything is self-hosted:

- the server side is the [`hermes-android`](https://github.com/adrianlaborda/hermes-android-plugin) plugin;
- delivery goes through **your own** Firebase project;
- the APK contains no Firebase project and there is no relay in the middle.

## The short way: ask Hermes

Open a chat in the app and send:

> Install the Hermes Android plugin from https://github.com/adrianlaborda/hermes-android-plugin and set up notifications
> for my Android phone.

Or open **Menu → Notifications** and tap **Ask Hermes** next to whatever is missing; the request is prefilled for you.
Hermes installs and configures the plugin and walks you through the Firebase console. The phone side happens in the
**Notifications** screen.

## What you do

### In the Firebase console

At <https://console.firebase.google.com>:

1. **Create a project**, or reuse one. Google Analytics is optional.
2. Go to **Project settings → Your apps → Add app → Android**. Enter the **package name shown in the app's Notifications
   screen**; there is a copy button. Register the app and **download `google-services.json`**, then skip the SDK steps.
3. Go to **Project settings → Service accounts → Generate new private key**. This downloads the *server key*, which is a
   secret.

### In the app (Menu → Notifications)

| Step | What it does |
|---|---|
| 1. Server plugin | Asks the server whether `hermes-android` is installed, configured and reachable. Offers **Ask Hermes** when it is not. |
| 2. Your Firebase project | **Import google-services.json**. It is stored encrypted on the phone, and Firebase starts with it at runtime. |
| 3. Server key | **Upload server key** sends the service-account JSON to your server over the app's authenticated connection. The plugin stores it owner-only. It never passes through the chat or the model. |
| 4. Permission | Android's notification permission. |
| **Pair with this server** | The app fetches the API server's port and the pairing secret over the same authenticated connection, then registers the phone. You never copy a secret. |

Then tap **Send test**.

### More profiles

Hermes isolates profiles: each one enables its own plugins and has its own pairing secret, inbox and scheduled jobs.
So each profile has to enable the plugin before it can notify the phone, and pairs separately. You install it once.

Pick the profile at the top of **Notifications**. If another profile already runs the plugin, tap **Enable for this
profile**. The app asks that profile to:

- link the same install into this profile;
- create this profile's own secret;
- point it at the same Firebase key, so there is nothing to upload;
- bring it up live.

Then the app pairs the phone. With `gateway.multiplex_profiles` (the default for several profiles), only that profile
restarts inside the gateway, and the others and their channels keep running. Otherwise the app asks you to restart
the gateway.

You can also ask Hermes in chat: *"enable Android notifications for the work profile"*.

> **Never attach the server key in a chat.** Chat attachments are expanded into the model's prompt, so the key would
> reach your model provider. Use the **Upload server key** button.

## Troubleshooting

| Symptom | Fix |
|---|---|
| **"Firebase rejected this phone's registration"** (the log shows `API_KEY_HTTP_REFERRER_BLOCKED` or another API-key 403) | Open Google Cloud Console → **APIs & Services → Credentials → Android key (auto created by Firebase)**. Set **Application restrictions** to *None*, or to *Android apps* with this package and the SHA-1 of the APK's signing certificate. A *Websites* restriction always blocks Android apps. Then pair again. |
| Test answers `auth` | The server key and `google-services.json` come from different Firebase projects, or *Cloud Messaging API (V1)* is disabled. |
| Step 1 never turns ready | Ask Hermes. It runs `hermes_android_status` and explains what is missing. |
| Nothing arrives although the test says delivered | Check Android notification settings and battery restrictions for the app. |

## How it works

- **The plugin's endpoint.** The phone reaches the plugin through the official Hermes API server (default port 8642),
  on the same host as the chat. Plain HTTP is accepted only for private and local addresses (LAN, VPN); anything public
  needs HTTPS.
- **What travels through FCM.** Only an opaque event ID, the device ID and the protocol version. The phone shows a
  private, generic notice, then fetches the content from the server's inbox. If the server is only reachable over a VPN,
  the content arrives once the VPN is on.
- **Retries.** WorkManager recovers events when a push arrives and when the app comes to the foreground. There is no
  background polling.
- **Revocation.** Revoking the device, signing out or forgetting the connection also revokes the device on the server
  when it can.

## Self-built APKs and forks

The default package is `io.github.adrianlaborda.hermesmobile`, and that is what users register in Firebase.

- **Fork with its own package:** build with `-Phermes.applicationId=your.package`. Its users then register that
  package instead.
- **Bundle a Firebase project:** pass `-Pfirebase.google_app_id`, `-Pfirebase.gcm_defaultSenderId`,
  `-Pfirebase.google_api_key` and `-Pfirebase.project_id`, which are the public values from `google-services.json`.
  That build skips step 2 and always uses that project. Never put the service-account key into an APK.
