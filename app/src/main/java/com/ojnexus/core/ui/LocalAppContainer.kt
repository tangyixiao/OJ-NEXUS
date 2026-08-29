package com.ojnexus.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.ojnexus.AppContainer

/**
 * App-wide access to the manual dependency container. Provided once at the Compose root;
 * ViewModels receive repositories through explicit factories — composables never touch
 * repositories or DAOs directly.
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided — wrap content in CompositionLocalProvider")
}

/** Factory that pulls a ViewModel built from the container's repositories. */
class ContainerViewModelFactory(
    private val container: AppContainer,
    private val create: (AppContainer) -> ViewModel,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        create(container) as T
}
