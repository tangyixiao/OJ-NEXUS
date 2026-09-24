# Workspace problem context / 工作区题目上下文设计

## Context / 背景

The native Luogu problem detail screen already has the authoritative problem title, but the
workspace route currently carries only the PID. As a result, a user who moves from a problem
statement to code loses the title context and sees only an identifier.

## Design / 设计

Keep `pid` as the stable required route argument and add an optional URL-encoded `title` query
argument. Centralize route construction in `NexusRoutes.workspace(pid, title)` so every title is
encoded before navigation and blank titles are omitted. The workspace screen receives the title,
keeps it in `WorkspaceState`, and renders it beside the PID. Existing callers that only know a
PID continue to navigate successfully and show the same PID-only view.

Only the native Luogu detail callback supplies the live title in this phase. The title is display
context, not an authority or identity key; submission requests continue to use the PID. No Room
schema, network endpoint, authentication boundary, cloud service, compiler, or runner changes.

## Error handling / 错误处理

Route parsing treats a missing or blank title as `null`. URI decoding is handled by Navigation;
malformed or absent optional data must not prevent the workspace from opening. The PID remains the
only required argument.

## Verification / 验证

Unit tests cover encoded PID/title route construction, blank-title omission, and ViewModel state
retention. The release emulator opens a real Luogu problem detail, enters its workspace, and shows
the same PID plus the live problem title without a crash or data reset.
