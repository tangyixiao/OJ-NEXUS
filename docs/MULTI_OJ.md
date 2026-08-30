# OJ NEXUS — Multi-OJ Architecture

The multi-OJ boundary is implemented for Codeforces, AtCoder, and Luogu public synchronization.

`JudgeAdapter` declares identity, capabilities, reliability, and runtime status.
`JudgeRegistry` resolves the adapter, account connector, and sync coordinator for a
`JudgeId`. The shared worker validates both judge and account ID before dispatching. A
coordinator owns only its judge's stages and data source.

Codeforces and AtCoder have independent request gates, retry/error policies, network DTOs,
cursors, and WorkManager names. A slow or degraded community source cannot serialize or
overwrite another judge's sync. Unsupported features are omitted from capability sets rather
than implemented as placeholders.

Luogu uses its own experimental adapter, request gate, typed content-only JSON transport, and
coordinator. A bound account can sync public profile/rating, contests, and the remote problem
catalog through the existing manual and WorkManager paths. Anonymous records return a typed
AUTH_REQUIRED partial stage; no submission rows are invented or written.

Room v6 uses text contest identities and stores account verification/source reliability,
remote difficulty provenance, and the AtCoder timestamp cursor. Problem, attempt, contest,
profile, rating, remote-catalog, and sync rows are keyed or filtered by judge. Disconnecting
one judge cancels only that judge/account's work and optionally purges only that judge's
cached remote rows; local training history remains.

Feature screens read through local repositories and Room flows. Settings has one connection
panel per registered judge. Dashboard/Profile show connected judges and only source-supported
ratings. Problems and contests provide judge filters. Analytics labels activity by judge and
keeps estimated AtCoder difficulty source-native. Cached data remains visible when network
sync is offline, partial, or degraded.
