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
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations.add("en")
        base.archivesName = "mood-cairns-$versionName"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            // No applicationIdSuffix — personal-use builds install under the real id.
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

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
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
// permission. The app is designed to be fully offline; Syncthing handles sync.
val forbiddenPermissions = listOf(
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.CHANGE_NETWORK_STATE",
    "android.permission.CHANGE_WIFI_STATE",
)

androidComponents {
    onVariants { variant ->
        val verifyTask = tasks.register("verifyNoNetwork${variant.name.replaceFirstChar { it.uppercase() }}") {
            val manifestFile = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
            inputs.file(manifestFile)
            doLast {
                val file = manifestFile.get().asFile
                val doc = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                }.newDocumentBuilder().parse(file)
                val nodes = doc.getElementsByTagName("uses-permission")
                val offenders = mutableListOf<String>()
                for (i in 0 until nodes.length) {
                    val el = nodes.item(i) as Element
                    val name = el.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                    if (name in forbiddenPermissions) offenders += name
                }
                if (offenders.isNotEmpty()) {
                    throw GradleException(
                        "Privacy constraint violated: merged manifest declares forbidden permission(s): $offenders. " +
                            "This app must remain fully offline.",
                    )
                }
            }
        }
        afterEvaluate {
            tasks.named("assemble${variant.name.replaceFirstChar { it.uppercase() }}")
                .configure { dependsOn(verifyTask) }
        }
    }
}
