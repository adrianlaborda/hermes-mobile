# Third-party notices

## commonmark-java 0.30.0

Core, GFM tables and GFM strikethrough. https://github.com/commonmark/commonmark-java

Copyright (c) 2015, Atlassian Pty Ltd
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.


## alpha12 local renderers and optional messaging

- KaTeX 0.19.0, MIT: packaged license at `app/src/main/assets/render/katex-LICENSE.txt`.
- Mermaid 12.0.0, MIT: packaged license at `app/src/main/assets/render/mermaid-LICENSE.txt`.
- Exact npm archive origins and SHA-512 integrity: `app/src/main/assets/render/DEPENDENCIES.json`.
- Firebase Android BoM 34.19.0 / Firebase Messaging 25.1.3 and AndroidX WorkManager 2.12.0:
  dependencies resolved by Gradle. Google Play services are needed for FCM delivery.
  Firebase project configuration and service-account credentials are not included.

## Hermes Desktop icon

The unchanged `hermes_desktop.png` is copied from `NousResearch/hermes-agent`, `apps/desktop/assets/icon.png`, revision f15c19c25f7409e749d6b38d5232f8969b78325e. Original branding remains attributed to Hermes / Nous Research. The Hermes Go public screenshots were used as UX references only; no Hermes Go code, icons or imagery are bundled.
