package com.wsclient

import android.net.VpnService
import android.content.pm.PackageManager

object SplitTunneling {

    fun apply(
        builder: VpnService.Builder,
        packageManager: PackageManager,
        packages: Set<String>,
        allowListMode: Boolean
    ) {
        packages.forEach { packageName ->
            try {
                if (allowListMode) {
                    builder.addAllowedApplication(packageName)
                } else {
                    builder.addDisallowedApplication(packageName)
                }
            } catch (_: PackageManager.NameNotFoundException) {
                // Приложение удалено — пропускаем.
            }
        }
    }
}
