# Security

Report vulnerabilities privately through GitHub's "Report a vulnerability" (Security tab) rather than a public issue.

Notes for users:
- Keys and tokens (`neurocanvas/ai.json`, `account.json`, `admin.key`) are stored in plain text, readable by your user only.
- The now-playing listener binds to 127.0.0.1 only and ignores requests that come from web pages.
- WorldBridge opens your world to the internet: keep "Check Mojang accounts" on unless you trust everyone who has the address.
