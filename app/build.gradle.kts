import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Secretos locales: secrets.properties (no versionado) o variables de entorno (CI).
// Resolución por variante: <VARIANTE>_<CLAVE> -> <CLAVE> -> valor por defecto.
val secrets = Properties().apply {
    val file = rootProject.file("secrets.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(variant: String, key: String, default: String = ""): String {
    val prefixed = "${variant.uppercase()}_$key"
    return secrets.getProperty(prefixed)
        ?: System.getenv(prefixed)
        ?: secrets.getProperty(key)
        ?: System.getenv(key)
        ?: default
}

fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val isReleaseBuildRequested = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
if (isReleaseBuildRequested && secret("release", "APP_CRYPTO_SALT").isBlank()) {
    throw GradleException(
        "Falta RELEASE_APP_CRYPTO_SALT (o APP_CRYPTO_SALT). Defínelo en secrets.properties o como variable de entorno. " +
            "Ver secrets.properties.example."
    )
}

android {
    namespace = "com.alfas.amortizacioncredito"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.alfas.amortizacioncredito"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "BASE_URL", "\"https://api-dev.credithome.com\"")
            buildConfigField("String", "VPS_SERVER_URL", "\"https://vps-dev.credithome.com\"")
            buildConfigField("String", "RABBITMQ_HOST", "\"127.0.0.1\"")
            buildConfigField("int", "RABBITMQ_PORT", "5672")
            buildConfigField("String", "RABBITMQ_USER", quoted(secret("debug", "RABBITMQ_USER", "guest")))
            buildConfigField("String", "RABBITMQ_PASSWORD", quoted(secret("debug", "RABBITMQ_PASSWORD", "guest")))
            buildConfigField("String", "RABBITMQ_VHOST", "\"/\"")
            buildConfigField("String", "RABBITMQ_EMAIL_QUEUE", "\"credithome_email_queue_dev\"")
            buildConfigField("String", "RABBITMQ_SYNC_QUEUE", "\"credithome_sync_queue_dev\"")
            buildConfigField("String", "SMTP_HOST", "\"smtp.gmail.com\"")
            buildConfigField("int", "SMTP_PORT", "465")
            buildConfigField("String", "SMTP_SENDER_NAME", "\"CreditHome Dev\"")
            buildConfigField("Boolean", "IS_PRODUCTION", "false")
            buildConfigField("String", "APP_CRYPTO_SALT", quoted(secret("debug", "APP_CRYPTO_SALT", "dev-only-insecure-salt")))
        }

        create("qa") {
            initWith(getByName("debug"))
            versionNameSuffix = "-qa"
            buildConfigField("String", "BASE_URL", "\"https://api-qa.credithome.com\"")
            buildConfigField("String", "VPS_SERVER_URL", "\"https://vps-qa.credithome.com\"")
            buildConfigField("String", "RABBITMQ_HOST", "\"qa-vps.credithome.com\"")
            buildConfigField("int", "RABBITMQ_PORT", "5672")
            buildConfigField("String", "RABBITMQ_USER", quoted(secret("qa", "RABBITMQ_USER")))
            buildConfigField("String", "RABBITMQ_PASSWORD", quoted(secret("qa", "RABBITMQ_PASSWORD")))
            buildConfigField("String", "APP_CRYPTO_SALT", quoted(secret("qa", "APP_CRYPTO_SALT", "dev-only-insecure-salt")))
            buildConfigField("String", "RABBITMQ_EMAIL_QUEUE", "\"credithome_email_queue_qa\"")
            buildConfigField("String", "RABBITMQ_SYNC_QUEUE", "\"credithome_sync_queue_qa\"")
            buildConfigField("String", "SMTP_SENDER_NAME", "\"CreditHome QA\"")
            buildConfigField("Boolean", "IS_PRODUCTION", "false")
        }

        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "BASE_URL", "\"https://api.credithome.com\"")
            buildConfigField("String", "VPS_SERVER_URL", "\"https://vps.credithome.com\"")
            buildConfigField("String", "RABBITMQ_HOST", "\"vps.credithome.com\"")
            buildConfigField("int", "RABBITMQ_PORT", "5672")
            buildConfigField("String", "RABBITMQ_USER", quoted(secret("release", "RABBITMQ_USER")))
            buildConfigField("String", "RABBITMQ_PASSWORD", quoted(secret("release", "RABBITMQ_PASSWORD")))
            buildConfigField("String", "RABBITMQ_VHOST", "\"/\"")
            buildConfigField("String", "RABBITMQ_EMAIL_QUEUE", "\"credithome_email_queue\"")
            buildConfigField("String", "RABBITMQ_SYNC_QUEUE", "\"credithome_sync_queue\"")
            buildConfigField("String", "SMTP_HOST", "\"smtp.gmail.com\"")
            buildConfigField("int", "SMTP_PORT", "465")
            buildConfigField("String", "SMTP_SENDER_NAME", "\"CreditHome Notificaciones\"")
            buildConfigField("Boolean", "IS_PRODUCTION", "true")
            buildConfigField("String", "APP_CRYPTO_SALT", quoted(secret("release", "APP_CRYPTO_SALT")))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
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
    implementation("com.google.mlkit:text-recognition:16.0.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}