import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    // Reads app/google-services.json (Firebase project cinema-devcrumbs; not a secret).
    id("com.google.gms.google-services")
}

android {
    namespace = "com.devcrumbs.cinema"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.devcrumbs.cinema"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        // Robolectric screenshot tests (ScreenshotTest) need the app's resources.
        unitTests.isIncludeAndroidResources = true
    }
}

tasks.withType<Test>().configureEach {
    // Roborazzi writes screenshots only when recording.
    systemProperty("roborazzi.test.record", "true")
    systemProperty("roborazzi.record.filePathStrategy", "relativePathFromRoborazziContextOutputDirectory")
    environment("LIVE_API", System.getenv("LIVE_API") ?: "")
    environment("SCREENSHOTS", System.getenv("SCREENSHOTS") ?: "")
}

/**
 * The app knows no city by name at the code level: the city list is the
 * committed `app/src/main/assets/cities.json`, derived from the cinema
 * platform's own `backend/cities/<slug>/city.toml` files (the single source of
 * truth the backend and the web frontend read). After adding a city there, or
 * changing a city's `[site].url`, regenerate it with:
 *
 *   ./gradlew syncCities -PcinemaPlatform=/path/to/server3management/services/cinema-platform
 *
 * `_template` is skipped, like everywhere else on the platform.
 */
abstract class SyncCitiesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val cityFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val cities = cityFiles.files
            .filter { it.parentFile.name != "_template" }
            .map { parseCity(it) }
            .sortedBy { it["name"] }
        require(cities.isNotEmpty()) { "No */city.toml found — pass -PcinemaPlatform=<path to services/cinema-platform>" }

        val json = cities.joinToString(",\n", "[\n", "\n]\n") { city ->
            city.entries.joinToString(", ", "  {", "}") { (k, v) -> "\"$k\": \"${escape(v)}\"" }
        }
        outputFile.get().asFile.writeText(json)
        logger.lifecycle("Wrote ${cities.size} cities: ${cities.joinToString { it.getValue("slug") }}")
    }

    // Minimal TOML reader: only flat `key = "string"` pairs of [site] and [geo]
    // are needed, and city.toml keeps those on single lines.
    private fun parseCity(file: java.io.File): Map<String, String> {
        val wanted = mapOf(
            "site.slug" to "slug",
            "site.name" to "name",
            "site.url" to "url",
            "geo.city_name" to "city_name",
        )
        val result = linkedMapOf<String, String>()
        var section = ""
        val sectionRe = Regex("""^\[([A-Za-z0-9_.]+)]\s*(#.*)?$""")
        val pairRe = Regex("""^([A-Za-z0-9_]+)\s*=\s*"([^"]*)"\s*(#.*)?$""")
        file.readLines().forEach { raw ->
            val line = raw.trim()
            sectionRe.find(line)?.let { section = it.groupValues[1]; return@forEach }
            pairRe.find(line)?.let { m ->
                wanted["$section.${m.groupValues[1]}"]?.let { result[it] = m.groupValues[2] }
            }
        }
        val missing = wanted.values.filterNot { it in result }
        require(missing.isEmpty()) { "${file.path}: missing $missing" }
        return wanted.values.associateWith { result.getValue(it) }
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
}

tasks.register<SyncCitiesTask>("syncCities") {
    val platform = providers.gradleProperty("cinemaPlatform").orNull
    if (platform != null) {
        cityFiles.from(fileTree(file(platform).resolve("backend/cities")) { include("*/city.toml") })
    }
    outputFile.set(layout.projectDirectory.file("src/main/assets/cities.json"))
}

// Lets `./gradlew syncCities assembleDebug` run in one go: sync writes into
// src/main/assets, which the asset merge reads.
tasks.matching { it.name.matches(Regex("merge\\w*Assets")) }.configureEach { mustRunAfter("syncCities") }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Push (Firebase Cloud Messaging) only — no Analytics.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")

    // Sign in with Google (Credential Manager; the ID token is verified by the backend).
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.43.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.43.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
