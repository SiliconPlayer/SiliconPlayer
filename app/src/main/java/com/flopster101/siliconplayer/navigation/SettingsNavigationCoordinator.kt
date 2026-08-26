package com.flopster101.siliconplayer

internal data class SettingsNavigationCoordinator(
    val openSettingsRoute: (SettingsRoute, Boolean) -> Unit,
    val popSettingsRoute: () -> Boolean,
    val exitSettingsToReturnView: () -> Unit,
    val openCurrentCoreSettings: () -> Unit,
    val openVisualizationSettings: () -> Unit,
    val openSelectedVisualizationSettings: (VisualizationMode) -> Unit
)

internal fun buildSettingsNavigationCoordinator(
    currentView: MainView,
    settingsRoute: SettingsRoute,
    settingsRouteHistory: List<SettingsRoute>,
    settingsReturnView: MainView,
    lastUsedCoreName: String?,
    setSettingsRoute: (SettingsRoute) -> Unit,
    setSettingsRouteHistory: (List<SettingsRoute>) -> Unit,
    setSettingsReturnView: (MainView) -> Unit,
    setCurrentView: (MainView) -> Unit,
    setSelectedPluginName: (String) -> Unit,
    setPlayerExpanded: (Boolean) -> Unit
): SettingsNavigationCoordinator {
    val resolvedSettingsReturnView =
        (if (currentView == MainView.Settings) settingsReturnView else currentView)
            .takeUnless { it == MainView.Settings }
            ?: MainView.Home

    val openSettingsRoute: (SettingsRoute, Boolean) -> Unit = { targetRoute, resetHistory ->
        if (resetHistory) {
            setSettingsRouteHistory(emptyList())
            setSettingsRoute(targetRoute)
        } else if (settingsRoute != targetRoute) {
            setSettingsRouteHistory((settingsRouteHistory + settingsRoute).takeLast(24))
            setSettingsRoute(targetRoute)
        }
    }

    val popSettingsRoute: () -> Boolean = {
        val previousRoute = settingsRouteHistory.lastOrNull()
        if (previousRoute != null) {
            setSettingsRouteHistory(settingsRouteHistory.dropLast(1))
            setSettingsRoute(previousRoute)
            true
        } else {
            false
        }
    }

    val exitSettingsToReturnView: () -> Unit = {
        val target = settingsReturnView.takeUnless { it == MainView.Settings } ?: MainView.Home
        setSettingsRouteHistory(emptyList())
        setSettingsRoute(SettingsRoute.Root)
        setCurrentView(target)
    }

    val openCurrentCoreSettings: () -> Unit = {
        pluginNameForCoreName(lastUsedCoreName)?.let { pluginName ->
            setSettingsReturnView(resolvedSettingsReturnView)
            setSelectedPluginName(pluginName)
            openSettingsRoute(SettingsRoute.PluginDetail, true)
            setCurrentView(MainView.Settings)
            setPlayerExpanded(false)
        }
    }

    val openVisualizationSettings: () -> Unit = {
        setSettingsReturnView(resolvedSettingsReturnView)
        openSettingsRoute(SettingsRoute.Visualization, true)
        setCurrentView(MainView.Settings)
        setPlayerExpanded(false)
    }

    val openVisualizationBarsSettings: () -> Unit = {
        setSettingsReturnView(resolvedSettingsReturnView)
        openSettingsRoute(SettingsRoute.VisualizationBasicBars, true)
        setCurrentView(MainView.Settings)
        setPlayerExpanded(false)
    }

    val openVisualizationOscilloscopeSettings: () -> Unit = {
        setSettingsReturnView(resolvedSettingsReturnView)
        openSettingsRoute(SettingsRoute.VisualizationBasicOscilloscope, true)
        setCurrentView(MainView.Settings)
        setPlayerExpanded(false)
    }

    val openVisualizationVuMetersSettings: () -> Unit = {
        setSettingsReturnView(resolvedSettingsReturnView)
        openSettingsRoute(SettingsRoute.VisualizationBasicVuMeters, true)
        setCurrentView(MainView.Settings)
        setPlayerExpanded(false)
    }

    val openVisualizationChannelScopeSettings: () -> Unit = {
        setSettingsReturnView(resolvedSettingsReturnView)
        openSettingsRoute(SettingsRoute.VisualizationAdvancedChannelScope, true)
        setCurrentView(MainView.Settings)
        setPlayerExpanded(false)
    }

    val openVisualizationProjectMSettings: () -> Unit = {
        setSettingsReturnView(resolvedSettingsReturnView)
        openSettingsRoute(SettingsRoute.VisualizationAdvancedProjectM, true)
        setCurrentView(MainView.Settings)
        setPlayerExpanded(false)
    }

    val openSelectedVisualizationSettings: (VisualizationMode) -> Unit = { mode ->
        when (mode) {
            VisualizationMode.Bars -> openVisualizationBarsSettings()
            VisualizationMode.Oscilloscope -> openVisualizationOscilloscopeSettings()
            VisualizationMode.VuMeters -> openVisualizationVuMetersSettings()
            VisualizationMode.ChannelScope -> openVisualizationChannelScopeSettings()
            VisualizationMode.ProjectM -> openVisualizationProjectMSettings()
            VisualizationMode.Off -> Unit
        }
    }

    return SettingsNavigationCoordinator(
        openSettingsRoute = openSettingsRoute,
        popSettingsRoute = popSettingsRoute,
        exitSettingsToReturnView = exitSettingsToReturnView,
        openCurrentCoreSettings = openCurrentCoreSettings,
        openVisualizationSettings = openVisualizationSettings,
        openSelectedVisualizationSettings = openSelectedVisualizationSettings
    )
}
