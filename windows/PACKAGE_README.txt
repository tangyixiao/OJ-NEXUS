OJ NEXUS WINDOWS PACKAGE v@VERSION@

This is an unsigned, self-contained win-x64 directory bundle. It is not an installer and does
not register applications, write registry entries, create shortcuts, or install updates.

START THE DESKTOP CLIENT

Run:
  desktop\OjNexus.Windows.Desktop.exe

USE THE CLI

Run:
  cli\ojnexus.exe status --json

The CLI also supports sync, history, data, and config show. Only public OJ handles are accepted.
The local database is stored at:
  %LOCALAPPDATA%\OJ-NEXUS\ojnexus.db

DATA AND SECURITY BOUNDARY

This package does not contain a user database, passwords, cookies, sessions, OpenApp secrets,
raw HTTP bodies, source code, or custom input. It does not ask for or store OJ passwords or
main-site cookies. Network synchronization remains explicit and typed; local history remains
available offline.

RELEASE BOUNDARY

The package is unsigned and has no MSI/MSIX or Store identity. It has no automatic updater.
Verify shipped files against SHA256SUMS.txt before copying or executing the bundle.
