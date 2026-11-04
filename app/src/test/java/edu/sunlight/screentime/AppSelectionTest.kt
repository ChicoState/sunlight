package edu.sunlight.screentime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSelectionTest {
  @Test
  fun `accepts only well-formed package names`() {
    assertTrue(AppSelection.isValidPackageName("com.example.video"))
    assertTrue(AppSelection.isValidPackageName("org.app_2.Main"))
    assertFalse(AppSelection.isValidPackageName("video"))
    assertFalse(AppSelection.isValidPackageName(""))
    assertFalse(AppSelection.isValidPackageName("com..example"))
    assertFalse(AppSelection.isValidPackageName("com.example/.Main"))
    assertFalse(AppSelection.isValidPackageName("1com.example"))
    assertFalse(AppSelection.isValidPackageName("com.${"a".repeat(260)}"))
  }

  @Test
  fun `keeps only launchable unprotected valid packages`() {
    val result =
        AppSelection.sanitize(
            requested =
                setOf("com.example.video", "com.example.gone", "com.android.settings", "bad"),
            launchable = setOf("com.example.video", "com.android.settings", "bad"),
            protectedPackages = emptySet(),
        )
    assertEquals(setOf("com.example.video"), result)
  }

  @Test
  fun `protects Sunlight the launcher dialer emergency and settings`() {
    val protected =
        AppSelection.protectedPackages(
            ownPackage = "edu.sunlight.screentime",
            resolved = setOf("com.example.launcher"),
        )
    listOf(
            "edu.sunlight.screentime",
            "com.example.launcher",
            "com.android.settings",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.emergency",
            "com.android.phone",
            "com.android.systemui",
        )
        .forEach { assertTrue(it, it in protected) }
    val requested = setOf("com.example.launcher", "com.example.video")
    assertEquals(
        setOf("com.example.video"),
        AppSelection.sanitize(requested, requested, protected),
    )
  }
}
