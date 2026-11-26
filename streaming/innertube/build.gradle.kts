plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":streaming:api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    // Terisolasi HANYA di modul :streaming:innertube (GPL-3.0, lihat LICENSE & NOTICE)
    implementation(libs.newpipeextractor)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
