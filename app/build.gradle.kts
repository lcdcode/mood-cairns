import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val androidNamespace = "http://schemas.android.com/apk/res/android"
val PSEUDO_LOCALE_ACCENTED = "en-rXA"

val simpleLocaleTag = Regex("^[a-z]{2,3}(-[A-Z]{2})?$")

// Converts a BCP-47 tag to a resource qualifier: "pt-BR" -> "pt-rBR",
// "zh-Hans" -> "b+zh+Hans".
fun toResourceQualifier(tag: String): String =
    if (simpleLocaleTag.matches(tag)) tag.replace("-", "-r") else "b+" + tag.replace("-", "+")

// res/xml/locale_config.xml is the single source of truth for shipped languages.
fun supportedResourceLocales(): List<String> {
    val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(file("src/main/res/xml/locale_config.xml"))
    val nodes = doc.getElementsByTagName("locale")
    val tags = (0 until nodes.length).map {
        (nodes.item(it) as Element).getAttributeNS(androidNamespace, "name")
    }
    if (tags.isEmpty() || tags.any(String::isBlank)) {
        throw GradleException("locale_config.xml must list at least one non-blank locale: $tags")
    }
    return tags.map(::toResourceQualifier)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

android {
    namespace = "com.lcdcode.moodcairns"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lcdcode.moodcairns"
        minSdk = 29
        targetSdk = 34
        versionCode = 10
        versionName = "1.2.0"
        // Package only the languages the app ships. Library resources for other locales
        // are stripped.
        resourceConfigurations.addAll(supportedResourceLocales())
        // en-XA pseudolocale (debug only, see isPseudoLocalesEnabled). Not ar-XB: listing
        // it also keeps every library's plain "ar" resources in release builds. Use the
        // "Force RTL layout direction" developer option for RTL checks instead.
        resourceConfigurations.add(PSEUDO_LOCALE_ACCENTED)
        base.archivesName = "mood-cairns-$versionName"
    }

    val releaseStoreFile = findProperty("MOOD_CAIRNS_STORE_FILE") as String?
    val releaseStorePassword = findProperty("MOOD_CAIRNS_STORE_PASSWORD") as String?
    val releaseKeyAlias = findProperty("MOOD_CAIRNS_KEY_ALIAS") as String?
    val releaseKeyPassword = findProperty("MOOD_CAIRNS_KEY_PASSWORD") as String?
    val hasReleaseSigning = listOf(
        releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword,
    ).all { !it.isNullOrBlank() }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // AGP 8.3+ embeds META-INF/version-control-info.textproto with the live
            // git HEAD at build time, which breaks F-Droid reproducible builds (the
            // hash differs between the developer build and F-Droid's buildserver).
            vcsInfo { include = false }

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // No applicationIdSuffix — personal-use builds install under the real id.

            // en-XA renders every resource string accented and lengthened, so hardcoded
            // (unextracted) text stands out. Select "English (XA)" in device languages.
            isPseudoLocalesEnabled = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Partial translations are expected: untranslated strings fall back to
        // English one at a time (see TRANSLATING.md). ExtraTranslation stays an error.
        warning += "MissingTranslation"
    }

    // In-app language switching needs every language installed. Without this, an App
    // Bundle install would only get the device's languages. No effect on APK builds.
    bundle {
        language { enableSplit = false }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }
}

// Several unit tests read these files from disk (string resources, locale configs,
// exported Room schemas). Declare them as inputs, or Gradle reuses a cached result
// after, say, a new translation is added.
tasks.withType<Test>().configureEach {
    listOf("src/main/res", "src/debug/res", "schemas").forEach { dir ->
        inputs.dir(dir).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName(dir)
    }
}

configurations.all {
    resolutionStrategy {
        // Termux's aapt2 (build-tools 34) can't parse android-35 resources,
        // so pin AndroidX libs to their last compileSdk-34-compatible releases.
        force("androidx.core:core:1.13.1")
        force("androidx.core:core-ktx:1.13.1")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)
    implementation(libs.sqlcipher.android)

    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.reorderable)

    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)
}

// Privacy guard: fail the build if the merged manifest ever declares a network
// permission. The app is designed to be fully offline; User responsible for backup.
val forbiddenPermissions = listOf(
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.CHANGE_NETWORK_STATE",
    "android.permission.CHANGE_WIFI_STATE",
)

// Typed task class so Gradle's configuration cache can serialize the task graph.
// Inline `doLast {}` lambdas capture script-scope references that CC can't handle.
abstract class VerifyNoNetworkTask : DefaultTask() {
    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val forbiddenPermissions: ListProperty<String>

    @TaskAction
    fun verify() {
        val doc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(mergedManifest.get().asFile)
        val forbidden = forbiddenPermissions.get().toSet()
        val nodes = doc.getElementsByTagName("uses-permission")
        val offenders = mutableListOf<String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            val name = el.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "name",
            )
            if (name in forbidden) offenders += name
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Privacy constraint violated: merged manifest declares forbidden " +
                    "permission(s): $offenders. This app must remain fully offline.",
            )
        }
    }
}

androidComponents {
    onVariants { variant ->
        val capitalized = variant.name.replaceFirstChar { it.uppercase() }
        val verifyTask = tasks.register<VerifyNoNetworkTask>("verifyNoNetwork$capitalized") {
            mergedManifest.set(
                variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST),
            )
            forbiddenPermissions.set(this@Build_gradle.forbiddenPermissions)
        }
        afterEvaluate {
            tasks.named("assemble$capitalized").configure { dependsOn(verifyTask) }
        }
    }
}
