plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.test {
    useJUnitPlatform()
}

val forbiddenCoreImports = listOf(
    "net.minecraft.",
    "net.fabricmc.",
    "net.minecraftforge.",
    "net.neoforged.",
    "meteordevelopment."
)

val verifyCoreIsolation by tasks.registering {
    group = "verification"
    description = "Checks that Core source code does not import Minecraft loader APIs."

    val javaSources = fileTree("src") {
        include("**/*.java")
    }
    inputs.files(javaSources)

    doLast {
        val violations = javaSources.files.flatMap { source ->
            source.readLines().mapIndexedNotNull { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("import ") && forbiddenCoreImports.any(trimmed::contains)) {
                    "${source.relativeTo(projectDir)}:${index + 1}: $trimmed"
                } else {
                    null
                }
            }
        }

        check(violations.isEmpty()) {
            "Core must stay independent from Minecraft loaders:\n${violations.joinToString("\n")}"
        }
    }
}

tasks.check {
    dependsOn(verifyCoreIsolation)
}
