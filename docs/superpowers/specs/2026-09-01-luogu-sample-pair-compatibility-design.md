# Luogu sample-pair compatibility / 洛谷样例对兼容设计

## Context / 背景

The live Luogu content-only problem response represents samples as nested pairs:
`[[input, output], ...]`. The current DTO declares `List<String>`, so real problem details can
fail during JSON decoding before the native detail screen is rendered. Existing fixtures also use
the older flat `List<String>` shape.

## Design / 设计

Keep the domain and Room cache contract as `List<String>`, where values are ordered as input,
output, input, output. Add a DTO-level kotlinx.serialization adapter that accepts both a string
element and an array element, flattening every string in each sample pair in source order. Reject
non-string sample values as a typed parse failure rather than silently inventing text.

The existing detail UI continues to render the first input/output pair and the existing cache
schema remains unchanged. No endpoint, authentication boundary, main-site password, Cookie,
Session, CSRF state, cloud service, compiler, or runner behavior changes.

## Verification / 验证

Unit tests decode a live-shaped nested sample payload and preserve the existing flat fixture
behavior. The release emulator reopens a public Luogu problem detail and must show its title,
description, and sample content instead of a JSON parse error.
