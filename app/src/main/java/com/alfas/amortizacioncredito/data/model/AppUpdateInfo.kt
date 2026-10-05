package com.alfas.amortizacioncredito.data.model

data class AppUpdateInfo(
    val hasUpdate: Boolean = false,
    val latestVersionCode: Int = 1,
    val latestVersionName: String = "1.0.0",
    val currentVersionCode: Int = 1,
    val currentVersionName: String = "1.0.0",
    val apkUrl: String = "",
    val releaseNotes: String = "",
    val isMandatory: Boolean = false
)
