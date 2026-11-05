plugins {
    `java-library`
    id("net.neoforged.moddev") version "2.0.147"
}

evaluationDependsOn(":core")
evaluationDependsOn(":storage-sqlite")

val minecraftVersion = providers.gradleProperty("minecraft_version").get()
val neoVersion = providers.gradleProperty("neo_version").get()
val parchmentMinecraftVersion = providers.gradleProperty("parchment_minecraft_version").get()
val parchmentMappingsVersion = providers.gradleProperty("parchment_mappings_version").get()
val sqliteVersion = providers.gradleProperty("sqlite_version").get()
val modId = providers.gradleProperty("mod_id").get()
val coreMain = project(":core").extensions.getByType<SourceSetContainer>().named("main")
val sqliteMain = project(":storage-sqlite").extensions.getByType<SourceSetContainer>().named("main")

base {
    archivesName = "litemfinder-neoforge-$minecraftVersion"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

val preparePackagedClientMod by tasks.registering(Sync::class) {
    group = "mod development"
    description = "Copies the installable mod JAR into the isolated packaged-client run directory."
    dependsOn(tasks.named("jar"))
    from(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    into(layout.projectDirectory.dir("run-packaged/mods"))
}

// A separate addon JAR exercises discovery across mod class loaders. It is never part of the install JAR.
val pluginProbe = sourceSets.create("pluginProbe") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
}
val pluginProbeJar by tasks.registering(Jar::class) {
    from(pluginProbe.output)
    archiveFileName = "litemfinder-compat-probe.jar"
    destinationDirectory = layout.buildDirectory.dir("compat-probe")
}
val preparePluginProbeMods by tasks.registering(Sync::class) {
    dependsOn(tasks.named("jar"), pluginProbeJar)
    from(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    from(pluginProbeJar.flatMap { it.archiveFile })
    into(layout.projectDirectory.dir("run-plugin-probe/mods"))
}
val preparePluginProbeOptions by tasks.registering {
    doLast {
        // Only the dedicated probe directory: first-run onboarding otherwise waits for manual input forever.
        val options = file("run-plugin-probe/options.txt")
        options.parentFile.mkdirs()
        val lines = if (options.isFile) options.readLines() else emptyList()
        options.writeText((lines.filterNot { it.startsWith("onboardAccessibility:") }
                + "onboardAccessibility:false").joinToString("\n", postfix = "\n"))
    }
}
val prepareNavigationProbeMods by tasks.registering(Sync::class) {
    dependsOn(tasks.named("jar"), pluginProbeJar, ":navigation-neoforge-1.21.1:jar")
    from(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    from(pluginProbeJar.flatMap { it.archiveFile })
    from(rootProject.layout.projectDirectory.file(
            "navigation-neoforge-1.21.1/build/libs/litemnavigation-neoforge-1.21.1-${project.version}.jar"))
    into(layout.projectDirectory.dir("run-navigation-probe/mods"))
}
val prepareNavigationProbeOptions by tasks.registering {
    doLast {
        val options = file("run-navigation-probe/options.txt")
        options.parentFile.mkdirs()
        val lines = if (options.isFile) options.readLines() else emptyList()
        options.writeText((lines.filterNot { it.startsWith("onboardAccessibility:") }
                + "onboardAccessibility:false").joinToString("\n", postfix = "\n"))
    }
}
val prepareRouteUiProbeMods by tasks.registering(Sync::class) {
    dependsOn(tasks.named("jar"), pluginProbeJar)
    from(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    from(pluginProbeJar.flatMap { it.archiveFile })
    into(layout.projectDirectory.dir("run-route-ui-probe/mods"))
}
val prepareRouteUiProbeOptions by tasks.registering {
    doLast {
        val options = file("run-route-ui-probe/options.txt")
        options.parentFile.mkdirs()
        val lines = if (options.isFile) options.readLines() else emptyList()
        options.writeText((lines.filterNot { it.startsWith("onboardAccessibility:") }
                + "onboardAccessibility:false").joinToString("\n", postfix = "\n"))
    }
}

neoForge {
    version = neoVersion
    addModdingDependenciesTo(sourceSets.test.get())

    parchment {
        minecraftVersion = parchmentMinecraftVersion
        mappingsVersion = parchmentMappingsVersion
    }

    runs {
        create("client") {
            client()
            gameDirectory = project.file("run")
            systemProperty("litemfinder.m0.sqliteProbe", "true")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
        create("packagedClient") {
            client()
            gameDirectory = project.file("run-packaged")
            loadedMods.set(emptySet())
            taskBefore(preparePackagedClientMod)
            systemProperty("litemfinder.m0.sqliteProbe", "true")
        }
        create("pluginProbeClient") {
            client()
            gameDirectory = project.file("run-plugin-probe")
            loadedMods.set(emptySet())
            taskBefore(preparePluginProbeMods)
            taskBefore(preparePluginProbeOptions)
            systemProperty("litemfinder.m0.sqliteProbe", "true")
            systemProperty("litemfinder.compatProbe.exitAfterSetup", "true")
        }
        create("navigationProbeClient") {
            client()
            gameDirectory = project.file("run-navigation-probe")
            loadedMods.set(emptySet())
            taskBefore(prepareNavigationProbeMods)
            taskBefore(prepareNavigationProbeOptions)
            systemProperty("litemfinder.m0.sqliteProbe", "true")
            systemProperty("litemfinder.compatProbe.exitAfterSetup", "true")
            systemProperty("litemfinder.compatProbe.navigationExpected", "true")
        }
        create("routeUiProbeClient") {
            client()
            gameDirectory = project.file("run-route-ui-probe")
            loadedMods.set(emptySet())
            taskBefore(prepareRouteUiProbeMods)
            taskBefore(prepareRouteUiProbeOptions)
            systemProperty("litemfinder.compatProbe.exitAfterSetup", "true")
            systemProperty("litemfinder.compatProbe.routeUi", "true")
        }
    }

    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
            sourceSet(coreMain.get())
            sourceSet(sqliteMain.get())
        }
    }

    unitTest {
        enable()
        testedMod.set(mods.named(modId))
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":storage-sqlite"))
    add("clientAdditionalRuntimeClasspath", "org.xerial:sqlite-jdbc:$sqliteVersion")

    add("jarJar", project(":core"))
    add("jarJar", project(":storage-sqlite"))
    add("jarJar", "org.xerial:sqlite-jdbc:[$sqliteVersion]") {
        isTransitive = false
    }

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.xerial:sqlite-jdbc:$sqliteVersion")
}

val modMetadata = mapOf(
    "minecraft_version" to minecraftVersion,
    "neo_version" to neoVersion,
    "mod_id" to modId,
    "mod_name" to providers.gradleProperty("mod_name").get(),
    "mod_license" to providers.gradleProperty("mod_license").get(),
    "mod_version" to project.version.toString()
)

tasks.processResources {
    inputs.properties(modMetadata)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(modMetadata)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.named<Jar>("jar") {
    from(rootProject.file("LICENSE")) {
        into("META-INF")
        rename { "LICENSE-LITEMFINDER" }
    }
    from(rootProject.file("THIRD_PARTY_NOTICES.md")) {
        into("META-INF")
    }
}

val verifyReleaseJar by tasks.registering {
    group = "verification"
    description = "Verifies the installable JAR contains licenses and all runtime Jar-in-Jar dependencies."
    dependsOn(tasks.named("jar"))
    val installableJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    inputs.file(installableJar)
    doLast {
        val entries = mutableSetOf<String>()
        zipTree(installableJar.get().asFile).visit {
            if (!isDirectory) {
                entries.add(relativePath.pathString)
            }
        }
        val requiredEntries = listOf(
                "META-INF/LICENSE-LITEMFINDER",
                "META-INF/THIRD_PARTY_NOTICES.md",
                "META-INF/jarjar/metadata.json"
        )
        requiredEntries.forEach { required ->
            check(entries.any { it == required }) { "Missing release JAR entry: $required" }
        }
        check(entries.count { it.startsWith("META-INF/jarjar/") && it.endsWith(".jar") } == 3) {
            "Expected exactly three nested runtime JARs (core, storage-sqlite, sqlite-jdbc)"
        }
        check(entries.none { it.startsWith("dev/litemfinder/compatprobe/") }) {
            "Compatibility probe fixtures must not be included in the installable JAR"
        }
        check(entries.none { it.startsWith("dev/litemfinder/navigation/") || it.contains("navigation-core") }) {
            "The inventory installable JAR must remain independent of the optional navigation Mod"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.check {
    dependsOn(verifyReleaseJar)
}

tasks.register("verifyPluginProbeClient") {
    group = "verification"
    description = "Runs the isolated addon probe and verifies its runtime and normal shutdown markers."
    dependsOn("runPluginProbeClient")
    doLast {
        val log = file("run-plugin-probe/logs/latest.log").readText()
        listOf("COMPAT PROBE PASSED:", "COMPAT PROBE MAIN MENU READY:", "COMPAT PROBE SHUTDOWN:",
                "Inventory plugins: 1 loaded, 2 skipped, 1 failed", "NAVIGATION ABSENT PROBE PASSED:").forEach { marker ->
            check(log.contains(marker)) { "Missing packaged plugin probe evidence: $marker" }
        }
        check(!log.contains("Shutdown failure!") && !log.contains("/FATAL]")) {
            "The plugin probe client did not shut down cleanly; inspect run-plugin-probe/logs/latest.log"
        }
    }
}

tasks.register("verifyNavigationProbeClient") {
    group = "verification"
    description = "Verifies the separately installed inventory and navigation Mods discover and call the optional bridge."
    dependsOn("runNavigationProbeClient")
    doLast {
        val log = file("run-navigation-probe/logs/latest.log").readText()
        listOf("COMPAT PROBE PASSED:", "COMPAT PROBE MAIN MENU READY:", "COMPAT PROBE SHUTDOWN:",
                "NAVIGATION BRIDGE PROBE PASSED:", "Inventory plugins: 1 loaded, 2 skipped, 1 failed").forEach { marker ->
            check(log.contains(marker)) { "Missing navigation bridge probe evidence: $marker" }
        }
        check(!log.contains("Shutdown failure!") && !log.contains("/FATAL]")) {
            "The combined client did not shut down cleanly; inspect run-navigation-probe/logs/latest.log"
        }
    }
}

tasks.register("verifyRouteUiProbeClient") {
    group = "verification"
    description = "Renders route summary fixtures with real fonts in both languages and at GUI scales 3 and 5."
    dependsOn("runRouteUiProbeClient")
    doLast {
        val log = file("run-route-ui-probe/logs/latest.log").readText()
        listOf("NAVIGATION UI PROBE PASSED:", "COMPAT PROBE MAIN MENU READY:", "COMPAT PROBE SHUTDOWN:").forEach {
            check(log.contains(it)) { "Missing route UI evidence: $it" }
        }
        check(!log.contains("Shutdown failure!") && !log.contains("/FATAL]") && !log.contains("/ERROR]"))
        check(fileTree(layout.buildDirectory.dir("route-ui-probe")).matching { include("*.png") }.files.size == 8) {
            "Expected eight route UI screenshots"
        }
        check(log.contains("PLAYER INVENTORY UI PROBE PASSED:"))
        check(fileTree(layout.buildDirectory.dir("player-ui-probe")).matching { include("*.png") }.files.size == 6)
    }
}
