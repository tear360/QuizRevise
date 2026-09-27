plugins {
    kotlin("jvm")
    application
}

version = (project.findProperty("appVersionName") as String?) ?: "1.1.0"

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("org.json:json:20240303")

    // Interface en HTML/CSS rendue par JavaFX WebView (WebKit embarqué)
    // Natives de la plateforme de build (le jar est packagé par OS côté CI)
    val fx = "21.0.4"
    val osName = System.getProperty("os.name").lowercase()
    val fxClassifier = when {
        osName.contains("win") -> "win"
        osName.contains("mac") || osName.contains("darwin") -> "mac"
        else -> "linux"
    }
    implementation("org.openjfx:javafx-base:$fx:$fxClassifier")
    implementation("org.openjfx:javafx-graphics:$fx:$fxClassifier")
    implementation("org.openjfx:javafx-controls:$fx:$fxClassifier")
    implementation("org.openjfx:javafx-media:$fx:$fxClassifier")
    implementation("org.openjfx:javafx-web:$fx:$fxClassifier")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "com.leov.quizrevise.desktop.MainKt"
}

// JAR gras : tout embarqué (SQLite, JSON) — lancable par simple double-clic / java -jar
tasks.jar {
    manifest.attributes["Main-Class"] = "com.leov.quizrevise.desktop.MainKt"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

// Génère BuildConfig.kt avec la version injectée par CI (-PappVersionName=...)
val genDir = layout.buildDirectory.dir("generated/src/kotlin/main")
sourceSets.main {
    kotlin.srcDir(genDir)
}
tasks.register("generateBuildConfig") {
    val v = (project.findProperty("appVersionName") as String?) ?: "1.1.0"
    outputs.dir(genDir)
    doLast {
        val dir = genDir.get().dir("com/leov/quizrevise/desktop").asFile
        dir.mkdirs()
        dir.resolve("BuildConfig.kt").writeText(
            "package com.leov.quizrevise.desktop\n\nobject BuildConfig {\n    const val VERSION = \"$v\"\n}\n"
        )
    }
}
tasks.named("compileKotlin") {
    dependsOn("generateBuildConfig")
}
