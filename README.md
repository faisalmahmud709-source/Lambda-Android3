# Lambda — Android release verification

Official release page: https://github.com/faisalmahmud709-source/Lambda-Android3/releases/tag/v1.0.0

## Current APK

- File: `Lambda-release.apk`
- App version: `1.2.0`
- Git tag: `v1.0.0`
- SHA-256: `2dcdadd975a317b855ce2e8fb12699c0baa72e1f4e18ea395a63ea0d76735cb7`

Verify the downloaded APK on Linux with:

```bash
sha256sum Lambda-release.apk
```

The output must match the SHA-256 value above.

## Signing certificate

- Certificate subject: `CN=Faisal Mahmud, O=Lambda Personal OS, L=Chattogram, C=BD`
- Certificate SHA-256 fingerprint: `296bff2b53fc8647b70d4503dfe9bb65cf3b65869cfb6862a4474fa6756f687e`
- Public-key algorithm: RSA 4096-bit
- Verified APK signature schemes: v2 and v3

The fingerprint identifies the public signing certificate; it is not the private signing key. Never publish the keystore or its passwords.

## Distribution

Download Lambda only from the official release page above. The current GitHub release contains one attached asset: `Lambda-release.apk`. No debug APK, R8 mapping file, or source JAR is attached to that release.

Copyright (c) 2026 Faisal Mahmud. All rights reserved. See [LICENSE](LICENSE).
