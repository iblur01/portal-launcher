package com.iblu01.portallauncher

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocationPermissionContractTest {
    @Test
    fun `launcher does not declare location permissions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val requested = packageInfo.requestedPermissions.orEmpty().toSet()

        assertFalse(requested.contains(android.Manifest.permission.ACCESS_COARSE_LOCATION))
        assertFalse(requested.contains(android.Manifest.permission.ACCESS_FINE_LOCATION))
    }
}
