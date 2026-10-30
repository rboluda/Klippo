import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// AGP 9 trae Kotlin integrado: no hace falta aplicar org.jetbrains.kotlin.android
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Marca de compilación (fecha y hora). Al ser un ValueSource, Gradle la vuelve a
// calcular en cada build aunque esté activa la configuration cache.
abstract class MarcaCompilacion : ValueSource<String, ValueSourceParameters.None> {
    override fun obtain(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd.HHmm"))
}

// Versión de Klippo (SemVer: MAYOR.MENOR.PARCHE). La sube scripts/publicar.ps1 con -Subir.
val versionKlippo = "1.3.0"
val marca = providers.of(MarcaCompilacion::class) {}.get()
val (versionMayor, versionMenor, versionParche) = versionKlippo.split(".").map { it.toInt() }
// versionCode siempre creciente: 1.2.3 → 1020300. Queda por encima de los de las versiones
// 0.1.0 anteriores (minutos desde 2026, unos 430 000), así Android lo trata como actualización.
val codigoVersion = versionMayor * 1_000_000 + versionMenor * 10_000 + versionParche * 100

android {
    namespace = "es.rboluda.klippo"
    compileSdk = 37

    defaultConfig {
        applicationId = "es.rboluda.klippo"
        minSdk = 33
        targetSdk = 37
        versionCode = codigoVersion
        versionName = versionKlippo

        // Solo móviles de 64 bits: el motor (Python + FFmpeg) pesa mucho por ABI
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // Firma de las releases publicadas en GitHub. Las claves viven fuera del repo, en
    // ~/.gradle/gradle.properties (KLIPPO_KEYSTORE, KLIPPO_KEYSTORE_PASSWORD, KLIPPO_KEY_ALIAS,
    // KLIPPO_KEY_PASSWORD). Sin ellas, la release sale sin firmar y no se puede instalar.
    val keystore = providers.gradleProperty("KLIPPO_KEYSTORE").orNull
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = providers.gradleProperty("KLIPPO_KEYSTORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("KLIPPO_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("KLIPPO_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        // Las de depuración llevan la fecha y hora para distinguir cada compilación:
        // "1.0.0-dev.20261024.1305". Las publicadas, solo el número: "1.0.0".
        debug {
            versionNameSuffix = "-dev.$marca"
        }
        release {
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
            // R8 quita el código y los recursos que no se usan de las librerías (el código de
            // Klippo y el del motor se conservan enteros: ver proguard-rules.pro)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Para mostrar BuildConfig.VERSION_NAME en la pantalla de inicio
        buildConfig = true
    }

    // El motor ejecuta binarios nativos desde disco: las .so deben extraerse al instalar
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// Copia el APK a <proyecto>/apk/ con la versión en el nombre, para que el móvil no confunda
// archivos: klippo-1.0.0.apk (publicada) y klippo-1.0.0-dev.20261024.1305-debug.apk.
// Sync deja en esa carpeta solo el último de cada tipo.
listOf("debug", "release").forEach { tipo ->
    val copiar = tasks.register<Sync>("copiarApk${tipo.replaceFirstChar { it.uppercase() }}") {
        // Copias locales: la configuration cache no admite referencias al script en la tarea
        val nombreApk = if (tipo == "release") {
            "klippo-$versionKlippo.apk"
        } else {
            "klippo-$versionKlippo-dev.$marca-debug.apk"
        }
        val esRelease = tipo == "release"
        from(layout.buildDirectory.dir("outputs/apk/$tipo")) {
            include("*.apk")
            rename { nombreApk }
        }
        into(rootProject.layout.projectDirectory.dir("apk"))
        // No borrar el APK del otro tipo
        preserve {
            if (esRelease) include("*-debug.apk") else { include("*.apk"); exclude("*-debug.apk") }
        }
    }
    tasks.matching { it.name == "assemble${tipo.replaceFirstChar { it.uppercase() }}" }
        .configureEach { finalizedBy(copiar) }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)

    // Reproductor del editor (previsualización del vídeo y del recorte)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // Motor de descarga: yt-dlp (con Python) y FFmpeg empaquetados
    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
