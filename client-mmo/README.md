   # Kotlin MMO Game (LibGDX + Ktor)

A simple 2D multiplayer online RPG built with [LibGDX](https://libgdx.com/) and [Ktor](https://ktor.io/) using Kotlin.
Players can log in, select or create a character, move around, chat, and fight in real-time using WebSocket communication.

## Features
- Character login, creation, and selection
- WebSocket multiplayer with Ktor backend
- Real-time movement, health updates, and chat
- Class-based system with basic skill management
- Death and respawn mechanics
- UI built with LibGDX scenes

## Tech Stack
- **Client:** Kotlin + LibGDX
- **Server:** Kotlin + Ktor WebSockets
- **Networking:** WebSocket

## build.gradle.kts(client)

``````````
plugins {
    kotlin("jvm") version "1.9.10"
    application
}

application {
    mainClass.set("pl.decodesoft.DesktopLauncher") // or your main class
}

repositories {
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    maven { url = uri("https://raw.githubusercontent.com/libktx/ktx/master/repo/") }
}

dependencies {
    implementation("com.badlogicgames.gdx:gdx:1.12.0")
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:1.12.0")
    implementation("com.badlogicgames.gdx:gdx-platform:1.12.0:natives-desktop")

    // LibGDX FreeType font
    implementation("com.badlogicgames.gdx:gdx-freetype:1.12.0")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:1.12.0:natives-desktop")

    // Ktor WebSocket client
    implementation("io.ktor:ktor-client-core:2.3.5")
    implementation("io.ktor:ktor-client-cio:2.3.5")
    implementation("io.ktor:ktor-client-websockets:2.3.5")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}


``````````
## build.gradle.kts(server)

``````

plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.20"
}

group = "pl.decodesoft"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val gdxVersion = "1.12.1"
val ktorVersion = "2.3.4"

dependencies {
    // Twoje istniejące zależności
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:1.4.12")

    // Zależności do serwera (jeśli są potrzebne w tym samym projekcie)
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")

    // Logowanie, Rejestracja
    implementation("at.favre.lib:bcrypt:0.9.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")

    // Zależności LibGDX
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "pl.decodesoft.Main"
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}

kotlin {
    jvmToolchain(21)
}
``````

![character_select](https://github.com/user-attachments/assets/9899cc8e-88b3-4f78-a8cd-098edb64d6fd)
![game](https://github.com/user-attachments/assets/d02c3e9a-8687-4437-a160-cf128019eae3)

