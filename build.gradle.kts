import io.papermc.hangarpublishplugin.model.Platforms
import java.time.Instant

plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.6.1"
    id("pl.allegro.tech.build.axion-release") version "1.21.3"
    id("io.papermc.hangar-publish-plugin") version "0.1.4"
}

scmVersion {
    tag {
        prefix.set("v")
    }
    // release.yml decides the bump type from the Conventional Commits made since the last tag
    // (feat -> minor, a breaking change -> major, anything else -> patch) and passes it through
    // this environment variable, rather than trying to re-implement that classification here.
    versionIncrementer({ context ->
        when (System.getenv("RELEASE_BUMP")) {
            "major" -> context.currentVersion.incrementMajorVersion()
            "minor" -> context.currentVersion.incrementMinorVersion()
            else -> context.currentVersion.incrementPatchVersion()
        }
    })
}
version = scmVersion.version

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")

    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("org.incendo:cloud-paper:2.0.0-beta.10")
    implementation("net.dv8tion:JDA:6.6.0") {
        exclude(module = "opus-java")
    }
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")

    // Soft-depend integrations: compile-time only, never shaded. Each hook checks the target
    // plugin's actual presence at runtime before touching any of these classes (see the
    // integration/ package), so the plugin runs fine with none of them installed.
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.luckperms:api:5.5")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        // VaultAPI's own POM pulls in a decade-old org.bukkit:bukkit snapshot as a compile
        // dependency; Paper API already provides everything Vault's classes reference from
        // Bukkit, and the two conflict on the same "org.bukkit:bukkit" capability otherwise.
        exclude(group = "org.bukkit", module = "bukkit")
    }

    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.2:4.116.1")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:deprecation")
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
        relocate("com.github.benmanes.caffeine", "$libs.caffeine")
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

hangarPublish {
    publications.register("plugin") {
        version.set(project.version as String)
        channel.set("Release")
        // Set by release.yml from the HANGAR_PROJECT_SLUG repository variable once this project
        // has actually been created on hangar.papermc.io; "SOS-Staff" is just a local fallback.
        id.set(System.getenv("HANGAR_PROJECT_SLUG") ?: "SOS-Staff")
        apiKey.set(System.getenv("HANGAR_API_TOKEN"))

        platforms {
            register(Platforms.PAPER) {
                jar.set(tasks.shadowJar.flatMap { it.archiveFile })
                // The floor is Paper 26.2 itself (it requires Java 25 to even boot), not the
                // wider 1.20-1.26 range originally targeted before that decision was made.
                platformVersions.set(listOf("26.2"))
            }
        }
    }
}
