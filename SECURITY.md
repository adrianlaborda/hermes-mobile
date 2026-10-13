# Security policy

## Supported versions

Only the latest release gets security fixes.

## Reporting a vulnerability

Please **do not open a public issue**. Report it privately through
[GitHub's private vulnerability reporting](https://github.com/adrianlaborda/hermes-mobile/security/advisories/new).

Include what an attacker can do, the steps to reproduce and the app version. You will get an answer within a week.
Once a fix is released, the advisory is published and you are credited unless you prefer otherwise.

Problems in the Hermes Agent server belong to [Hermes Agent](https://github.com/NousResearch/hermes-agent/security).
Problems in the notifications server plugin belong to
[hermes-android-plugin](https://github.com/adrianlaborda/hermes-android-plugin/security).

## Scope

The things this app must protect:

- **Gateway credentials.** Tokens, session cookies and proxy headers are encrypted with the Android Keystore.
  Passwords are never stored.
- **Transport.** Plain HTTP is allowed only for private and local addresses.
- **Notification secrets.** The pairing secret and the Firebase server key never travel through the chat or the model.
  FCM carries only an opaque event ID.
- **Drafts and queued messages,** which are encrypted at rest.
