# Windows Code Signing

Status: supported, strictly opt-in. Every build is produced unsigned by default; signing applies
only when a certificate is explicitly configured. No certificate, key, or password is ever stored
in this repository.

## Certificate storage rules

1. The signing certificate (PFX or hardware token) lives OUTSIDE the repository — in the Windows
   certificate store (`certlm.msc` / `certmgr.msc`), on a hardware token, or in CI secret storage.
2. Only the SHA-1 thumbprint is referenced. It travels through environment variables, never
   through committed files:
   - `OJ_NEXUS_SIGNING_CERT_THUMBPRINT` — SHA-1 thumbprint of the code-signing cert.
   - `OJ_NEXUS_SIGNTOOL_PATH` — optional explicit path to `signtool.exe` (auto-discovered under
     `Windows Kits\10\bin\<version>\x64` when absent).
3. Never commit: `.pfx`/`.p12` files, private keys, export passwords, or the thumbprint itself in
   any tracked file. CI injects the thumbprint from a repository secret.
4. Import a PFX out of band, e.g.:
   `Import-PfxCertificate -FilePath cert.pfx -CertStoreLocation Cert:\CurrentUser\My -Password (Read-Host -AsSecureString)`

## Exact signtool invocations used

All signing runs through `windows/scripts/sign-binaries.ps1` (shared by package and installer):

```
signtool sign /fd SHA256 /sha1 <THUMBPRINT> /tr http://timestamp.digicert.com /td SHA256 <file>
signtool verify /pa /v <file>
```

- `/fd SHA256` — SHA-256 file digest (required by modern SmartScreen baselines).
- `/sha1 <THUMBPRINT>` — selects the cert in the store by SHA-1 thumbprint.
- `/tr <timestamp url>` — RFC 3161 timestamp so signatures outlive the certificate.
- `/td SHA256` — timestamp digest algorithm.
- `verify /pa /v` — Authenticode verification with verbose output; a failing verify fails the
  build.

## What gets signed, and when

1. **Executables (CLI + WPF)** — `package.ps1` signs `cli\ojnexus.exe` and
   `desktop\OjNexus.Windows.Desktop.exe` immediately after publish and BEFORE `SHA256SUMS.txt` is
   written, so the deterministic manifest describes the signed bytes.
2. **Installer** — `build-installer.ps1` signs the produced `OJ-NEXUS-Setup-*.exe` after the
   Inno Setup compile (same env vars, same sign/verify pair).

## Unsigned builds always ship

With no `OJ_NEXUS_SIGNTOOL_PATH` / `OJ_NEXUS_SIGNING_CERT_THUMBPRINT` configured, both scripts
print `SIGNING: SKIPPED` and complete normally — an unsigned artifact is always produced, and CI
on public runners runs unsigned. Verification of an unsigned build is the standard
`verify-package.ps1` / `verify-installer.ps1` flow (SHA256SUMS + payload checks).

## CI

The GitHub Actions workflow runs unsigned by default. To sign in CI: store the PFX as a secret
(`WINDOWS_PFX_BASE64`, `WINDOWS_PFX_PASSWORD`, `WINDOWS_SIGNING_THUMBPRINT`), import it into
`Cert:\CurrentUser\My` in a workflow step, export `OJ_NEXUS_SIGNING_CERT_THUMBPRINT`, and point
`OJ_NEXUS_SIGNTOOL_PATH` at the runner's Windows Kits signtool. The scripts fail loudly when
signing is requested but the cert or signtool is missing.
