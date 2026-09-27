plugins {
    id("com.android.application")
}

// Ces propriétés sont injectées par GitHub Actions à chaque release :
//   ./gradlew :app:assembleRelease -PappVersionName=1.2.3 -PappVersionCode=42
val ciVersionName: String? = project.findProperty("appVersionName") as String?
val ciVersionCode: Int? = (project.findProperty("appVersionCode") as String?)?.toIntOrNull()

android {
    namespace = "com.leov.quizrevise"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.leov.quizrevise"
        minSdk = 21
        targetSdk = 36
        versionCode = ciVersionCode ?: 1
        versionName = ciVersionName ?: "1.0.0"
    }

    signingConfigs {
        create("release") {
            // Keystore versionné dans le repo pour garantir une signature STABLE
            // entre les builds locaux et GitHub Actions (indispensable pour que
            // Android accepte d'installer les mises à jour par-dessus l'existant).
            // Pour un projet sérieux : passer les mots de passe en variables d'env/secrets.
            storeFile = file("quizrevise.keystore")
            storePassword = "quizrevise2026"
            keyAlias = "quizrevise"
            keyPassword = "quizrevise2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
