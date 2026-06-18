package com.tingmusic.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeTest {
    @Test fun knownNames() {
        assertEquals(AppTheme.DEFAULT, AppTheme.fromStored("DEFAULT"))
        assertEquals(AppTheme.WHITE_RED, AppTheme.fromStored("WHITE_RED"))
    }
    @Test fun nullOrUnknownDefaults() {
        assertEquals(AppTheme.DEFAULT, AppTheme.fromStored(null))
        assertEquals(AppTheme.DEFAULT, AppTheme.fromStored("nope"))
    }
}
