import java.time.Instant

plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.6.1"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")

    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("org.incendo:cloud-paper:2.0.0-beta.10")
    implementation("net.dv8tion:JDA:6.6.0") {
        exclude(module = "opus-java")
    }

    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.20.0")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }

    shadowJar {
        val libs = "fr.mathildeuh.sosstaff.libs"
        relocate("com.zaxxer.hikari", "$libs.hikari")
        relocate("org.incendo.cloud", "$libs.cloud")
        // org.sqlite is intentionally NOT relocated: its native-library loader resolves
        // bundled .so/.dll/.dylib resources through hardcoded org/sqlite/native paths,
        // and relocating the package is a known way to break that lookup at runtime.

        // JDA itself and its highest-conflict-risk transitive libraries (all pure JVM
        // bytecode, no native/JNI component):
        relocate("net.dv8tion.jda", "$libs.jda")
        relocate("com.fasterxml.jackson", "$libs.jackson")
        relocate("okhttp3", "$libs.okhttp3")
        relocate("okio", "$libs.okio")
        relocate("com.neovisionaries.ws.client", "$libs.nvwebsocket")
        relocate("gnu.trove", "$libs.trove")
        relocate("org.apache.commons.collections4", "$libs.commonscollections4")
        // Deliberately NOT relocated: org.slf4j (a shared logging facade, not a private
        // implementation detail - relocating it is the opposite of what shading guides for
        // it recommend); the Kotlin stdlib (compiler-generated metadata references class
        // names in ways relocation is not guaranteed to rewrite cleanly); and the whole
        // com.google.* cluster JDA pulls in via Tink (protobuf/gson/errorprone/jsr305) -
        // low real-world collision odds for a Bukkit plugin, and Tink's own crypto code is
        // exactly the kind of thing a subtly-broken relocation would fail silently in.
    }

    val gitCommitHash: String = try {
        providers.exec { commandLine("git", "rev-parse", "--short", "HEAD") }.standardOutput.asText.get().trim()
    } catch (e: Exception) {
        "unknown"
    }
    val buildDate: String = Instant.now().toString()
    val buildVersion: String = version.toString()

    processResources {
        filesMatching("plugin.yml") {
            expand(mapOf("version" to buildVersion))
        }
        filesMatching("version.properties") {
            expand(mapOf("version" to buildVersion, "commitHash" to gitCommitHash, "buildDate" to buildDate))
        }
    }
}
