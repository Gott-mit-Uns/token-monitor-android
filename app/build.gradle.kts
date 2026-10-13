import java.util.Properties

// Secrets are read from an owner-controlled file outside this repository.
val releaseSigningPath = providers.environmentVariable("TOKEN_MONITOR_RELEASE_SIGNING_PROPERTIES")
    .orElse(providers.systemProperty("user.home").map { "$it/.local/share/token-monitor-android/private/release-signing/release-signing.properties" })
val releaseSigningFile = file(releaseSigningPath.get())
val releaseSigning = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
}
val releaseSigningAvailable = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !releaseSigning.getProperty(it).isNullOrBlank() }

plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "dev.bing.tokenmonitor"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.bing.tokenmonitor.nativeclient"
        minSdk = 26
        targetSdk = 37
        versionCode = 68
        versionName = "0.2.59"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (releaseSigningAvailable) create("release") {
            storeFile = file(releaseSigning.getProperty("storeFile"))
            storePassword = releaseSigning.getProperty("storePassword")
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        getByName("release") {
            if (releaseSigningAvailable) signingConfig = signingConfigs.getByName("release")
            // Keep the tested runtime behavior; shrinker tuning is a separate change.
            isMinifyEnabled = false
        }
    }
    testBuildType = if (providers.gradleProperty("tokenMonitorReleaseTest").orNull == "true") "release" else "debug"
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    bundle { language { enableSplit = false } }
    if (providers.gradleProperty("tokenMonitorLiveTest").orNull == "true") {
        sourceSets.getByName("androidTest").kotlin.directories.add("src/liveTest/java")
    }
    if (providers.gradleProperty("tokenMonitorPermissionTest").orNull == "true") {
        sourceSets.getByName("androidTest").kotlin.directories.add("src/permissionTest/java")
    }
    if (providers.gradleProperty("tokenMonitorMotionPerf").orNull == "true") {
        sourceSets.getByName("androidTest").kotlin.directories.add("src/perfTest/java")
    }
    if (providers.gradleProperty("tokenMonitorCapacityTest").orNull == "true") {
        sourceSets.getByName("androidTest").kotlin.directories.add("src/capacityTest/java")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    testImplementation("junit:junit:4.13.2")
    // Exercise the real ViewModel recovery chain on Windows without replacing the configured phone APK.
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("com.squareup.okhttp3:okhttp-tls:5.3.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("com.squareup.okhttp3:okhttp-tls:5.3.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

// Exact runtime artifacts for the offline notice generator; test/build dependencies are excluded.
tasks.register("writeRuntimeInventory") {
    val inventory = layout.buildDirectory.file("third-party/runtime-artifacts.tsv")
    outputs.file(inventory)
    outputs.upToDateWhen { false }
    doLast {
        val artifacts = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath").flatMap { name ->
            configurations.getByName(name).resolvedConfiguration.resolvedArtifacts.map { artifact ->
                val id = artifact.moduleVersion.id
                "${id.group}\t${id.name}\t${id.version}\t${artifact.file.absolutePath}"
            }
        }.distinct().sorted()
        inventory.get().asFile.apply { parentFile.mkdirs(); writeText(artifacts.joinToString("\n", postfix = "\n")) }
        logger.lifecycle("Runtime inventory: ${artifacts.size} artifacts")
    }
}

// Never silently produce an unsigned APK or substitute the Debug certificate.
tasks.configureEach {
    if (name == "validateSigningRelease" || name == "packageRelease") doFirst {
        check(releaseSigningAvailable) { "Release signing is missing. Configure the private external signing file; Debug signing is not a fallback." }
    }
}
