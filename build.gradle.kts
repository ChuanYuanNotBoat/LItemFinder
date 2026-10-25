import java.security.MessageDigest

plugins {
    base
}

allprojects {
    group = "dev.litemfinder"
    version = providers.gradleProperty("mod_version").get()
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

val releaseVersion = version.toString()
val releaseDirectory = layout.buildDirectory.dir("releases/$releaseVersion")
val installableJar = layout.projectDirectory.file("neoforge-1.21.1/build/libs/litemfinder-neoforge-1.21.1-$releaseVersion.jar")

val releaseSources by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Archives the complete workspace sources used for this release."
    archiveFileName = "litemfinder-$releaseVersion-sources.zip"
    destinationDirectory = releaseDirectory
    from(rootDir) {
        include("*.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat", "gradle/**", ".github/**", ".gitignore",
                "LICENSE", "THIRD_PARTY_NOTICES.md", "README.md", "docs/**", "release/**",
                "core/src/**", "core/build.gradle.kts", "storage-sqlite/src/**", "storage-sqlite/build.gradle.kts",
                "navigation-core/src/**", "navigation-core/build.gradle.kts",
                "neoforge-1.21.1/src/**", "neoforge-1.21.1/build.gradle.kts")
    }
}
val releaseBundle by tasks.registering(Zip::class) {
    group = "distribution"
    dependsOn(":neoforge-1.21.1:jar")
    archiveFileName = "litemfinder-$releaseVersion-release.zip"
    destinationDirectory = releaseDirectory
    from(installableJar)
    from("LICENSE", "THIRD_PARTY_NOTICES.md", "release/INSTALL.md")
    from("docs/releases/$releaseVersion.md") { rename { "RELEASE_NOTES.md" } }
}
val releaseChecksums by tasks.registering {
    group = "distribution"
    dependsOn(releaseSources, releaseBundle, subprojects.map { "${it.path}:check" })
    val jarCopy = releaseDirectory.map { it.file(installableJar.asFile.name) }
    val checksumFile = releaseDirectory.map { it.file("SHA256SUMS") }
    inputs.files(installableJar, releaseSources.flatMap { it.archiveFile }, releaseBundle.flatMap { it.archiveFile })
    outputs.files(jarCopy, checksumFile)
    doLast {
        installableJar.asFile.copyTo(jarCopy.get().asFile, overwrite = true)
        val artifacts = listOf(jarCopy.get().asFile, releaseSources.get().archiveFile.get().asFile, releaseBundle.get().archiveFile.get().asFile)
        checksumFile.get().asFile.writeText(artifacts.joinToString("\n", postfix = "\n") { file ->
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    val length = input.read(buffer)
                    if (length < 0) break
                    digest.update(buffer, 0, length)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) } + "  " + file.name
        })
    }
}
tasks.register("release") {
    group = "distribution"
    description = "Checks all modules and packages the installable JAR, complete sources, notes and SHA-256 checksums."
    dependsOn(releaseChecksums)
}

subprojects {
    repositories {
        mavenCentral()
    }
}
