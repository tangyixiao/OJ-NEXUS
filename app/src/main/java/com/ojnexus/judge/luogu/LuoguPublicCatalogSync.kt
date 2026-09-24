package com.ojnexus.judge.luogu

import com.ojnexus.core.data.sync.StageOutcome

/** Foreground entry point for importing Luogu's public problem catalog without an account. */
fun interface LuoguPublicCatalogSync {
    suspend fun syncPublicProblemCatalog(force: Boolean): StageOutcome
}
