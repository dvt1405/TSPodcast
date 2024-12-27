package tss.t.build

import org.gradle.api.JavaVersion

object TSBuilds {
    const val combineSdk = 35
    const val minSdk = 23
    val javaVersion = JavaVersion.VERSION_11
    const val jvmTarget = "11"
}