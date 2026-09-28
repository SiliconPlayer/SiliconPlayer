package com.flopster101.siliconplayer

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.flopster101.siliconplayer.ui.icons.AboutAppIconColor
import com.flopster101.siliconplayer.ui.icons.AboutAppIconMonochrome
import com.flopster101.siliconplayer.ui.icons.AirwaveIcon
import com.flopster101.siliconplayer.ui.icons.FileGameIcon
import com.flopster101.siliconplayer.ui.icons.FileTrackedIcon
import com.flopster101.siliconplayer.ui.icons.FileUnsupportedIcon
import com.flopster101.siliconplayer.ui.icons.FolderZipIcon
import com.flopster101.siliconplayer.ui.icons.PlaceholderGamepadIcon
import com.flopster101.siliconplayer.ui.icons.PlaceholderMusicNoteIcon
import com.flopster101.siliconplayer.ui.icons.PlaceholderTrackerChipIcon
import com.flopster101.siliconplayer.ui.icons.SettingsApplicationsIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedCustomIconsTest {

    @Test
    fun fileIconsUse24dpViewport() {
        assertIconGeometry(FileTrackedIcon, "FileTrackedIcon", 24f)
        assertIconGeometry(FileGameIcon, "FileGameIcon", 24f)
        assertIconGeometry(FileUnsupportedIcon, "FileUnsupportedIcon", 24f)
        assertIconGeometry(FolderZipIcon, "FolderZipIcon", 24f)
    }

    @Test
    fun placeholderIconsUse24dpViewport() {
        assertIconGeometry(PlaceholderMusicNoteIcon, "PlaceholderMusicNoteIcon", 24f)
        assertIconGeometry(PlaceholderGamepadIcon, "PlaceholderGamepadIcon", 24f)
    }

    @Test
    fun translatedIconsKeep960Viewport() {
        assertIconGeometry(PlaceholderTrackerChipIcon, "PlaceholderTrackerChipIcon", 960f)
        assertIconGeometry(AirwaveIcon, "AirwaveIcon", 960f)
    }

    @Test
    fun actionIconsUse24dpViewport() {
        assertIconGeometry(SettingsApplicationsIcon, "SettingsApplicationsIcon", 24f)
    }

    @Test
    fun aboutIconsUse1024Viewport() {
        assertIconGeometry(AboutAppIconColor, "AboutAppIconColor", 1024f, 108.dp)
        assertIconGeometry(AboutAppIconMonochrome, "AboutAppIconMonochrome", 1024f, 108.dp)
    }

    private fun assertIconGeometry(
        icon: ImageVector,
        name: String,
        viewport: Float,
        defaultSize: Dp = 24.dp
    ) {
        assertEquals(name, icon.name)
        assertEquals(viewport, icon.viewportWidth)
        assertEquals(viewport, icon.viewportHeight)
        assertEquals(defaultSize, icon.defaultWidth)
        assertEquals(defaultSize, icon.defaultHeight)
        assertTrue(icon.root.iterator().hasNext())
    }
}
