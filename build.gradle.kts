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
        // org.sqlite is intentionally NOT relocated: its native-library loader resolves
        // bundled .so/.dll/.dylib resources through hardcoded org/sqlite/native paths,
        // and relocating the package is a known way to break that lookup at runtime.
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
