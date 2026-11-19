package com.wsclient

import android.content.pm.PackageManager
import android.net.VpnService

object SplitTunneling {

    fun apply(
        builder: VpnService.Builder,
        packageManager: PackageManager,
        packages: Set<String>,
        allowListMode: Boolean
    ) {
        for (packageName in packages) {
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
