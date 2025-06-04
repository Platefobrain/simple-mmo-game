plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.20"
    application                             // <<< dodajemy plugin application
}

group = "pl.decodesoft"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val gdxVersion = "1.12.1"
val ktorVersion = "2.3.4"

dependencies {
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:1.4.12")
    implementation("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")

    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")

    // Logowanie, Rejestracja
    implementation("at.favre.lib:bcrypt:0.9.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // Zależności LibGDX
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
}

// Konfiguracja zadań do uruchamiania klienta i serwera
tasks.register<JavaExec>("runClient") {
    group = "application"
    mainClass.set("pl.decodesoft.DesktopLauncher")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    workingDir = file("assets")

    // Jeśli masz zasoby aplikacji, stworzy katalog assets jeśli nie istnieje
    doFirst {
        file("assets").mkdirs()
    }
}

tasks.register<JavaExec>("runServer") {
    group = "application"
    mainClass.set("pl.decodesoft.ServerKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "pl.decodesoft.MMOGame.Launcher"
    }
}

kotlin {
    jvmToolchain(21)
}