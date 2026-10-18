# Plan: AtCoder contest results & rating sync (Phase 93)

Date: 2026-10-18 · Spec: docs/superpowers/specs/2026-10-18-atcoder-rating-sync.md

## Steps

1. `AtCoderUrls.SITE_BASE_URL`; `AtCoderRatingChangeDto` in AtCoderDtos.kt.
2. NEW `api/AtCoderHistoryApi.kt` Retrofit interface.
3. Generalize `AtCoderProblemsClient<C>` (retry semantics unchanged).
4. Adapter: `fetchRatingHistory` + RATING/RATING_HISTORY capabilities (`contestType=algo`).
5. `AtCoderMappers.toRatingChangeEntity` (extension on the DTO).
6. `AtCoderSyncPolicy.RATING_FRESH_MS = 6h`; repository `syncRating` with transactional
   delete+insert (`RatingChangeDao.insertAll` added); runStage/stamp RATING arms.
7. Coordinator: RATING stage after SUBMISSIONS.
8. OjNexusApplication: history Retrofit + shared gate; adapter wiring.
9. Tests: AtCoderHistoryClientTest (2), AtCoderMappersTest (+3 + capability flip),
   AtCoderSyncRepositoryTest (+4, coordinator 3→4 stages), account-binding fake ripple.
10. Docs: ATCODER.md boundary + rating paragraphs; ROADMAP bilingual section.
11. Gates: full test assembleDebug (693 tests), aggregate 0 failures.
12. Bookkeeping: versionCode 91 / versionName 0.3.91; plan ✅ mark; commit `feat:`.

## Risks / notes

- Room @Upsert drops unique-index conflicts → delete+insert (see spec).
- EndTime offsets: parse with OffsetDateTime, never Instant.parse.
- Epoch fixture: 2016-09-04T22:50:00+09:00 == 1_472_997_000 (verified).
