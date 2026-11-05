import java.util.zip.ZipFile
import groovy.json.JsonSlurper

plugins {
    `java-library`
    id("net.neoforged.moddev") version "2.0.147"
}
evaluationDependsOn(":core")
evaluationDependsOn(":navigation-core")
val coreMain = project(":core").extensions.getByType<SourceSetContainer>().named("main")
val navigationMain = project(":navigation-core").extensions.getByType<SourceSetContainer>().named("main")
base { archivesName = "litemnavigation-neoforge-1.21.1" }
java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    withSourcesJar()
}
val prepareStandalone by tasks.registering(Sync::class) {
    dependsOn(tasks.named("jar"))
    from(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    into(layout.projectDirectory.dir("run-packaged/mods"))
}
val prepareStandaloneProbe by tasks.registering(Sync::class) {
    dependsOn(tasks.named("jar"))
    from(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    into(layout.projectDirectory.dir("run-standalone-probe/mods"))
}
val prepareStandaloneProbeOptions by tasks.registering {
    doLast {
        val options=file("run-standalone-probe/options.txt")
        options.parentFile.mkdirs()
        val lines=if(options.isFile)options.readLines() else emptyList()
        options.writeText((lines.filterNot { it.startsWith("onboardAccessibility:") }+"onboardAccessibility:false")
                .joinToString("\n",postfix="\n"))
    }
}
neoForge {
    version = providers.gradleProperty("neo_version").get()
    addModdingDependenciesTo(sourceSets.test.get())
    parchment {
        minecraftVersion = providers.gradleProperty("parchment_minecraft_version").get()
        mappingsVersion = providers.gradleProperty("parchment_mappings_version").get()
    }
    runs {
        create("client") { client(); gameDirectory = project.file("run") }
        create("standaloneClient") {
            client()
            gameDirectory = project.file("run-packaged")
            loadedMods.set(emptySet())
            taskBefore(prepareStandalone)
        }
        create("standaloneProbeClient") {
            client()
            gameDirectory=project.file("run-standalone-probe")
            loadedMods.set(emptySet())
            taskBefore(prepareStandaloneProbe)
            taskBefore(prepareStandaloneProbeOptions)
            systemProperty("litemnavigation.standaloneProbe","true")
        }
    }
    mods {
        create("litemnavigation") {
            sourceSet(sourceSets.main.get())
            sourceSet(navigationMain.get())
            sourceSet(coreMain.get())
        }
    }
    unitTest { enable(); testedMod.set(mods.named("litemnavigation")) }
}
dependencies {
    implementation(project(":navigation-core"))
    implementation(project(":core"))
    // Only the optional bridge mentions the inventory Mod. Standalone startup never loads that class.
    compileOnly(project(":neoforge-1.21.1"))
    add("jarJar", project(":navigation-core"))
    add("jarJar", project(":core"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.processResources {
    val values = mapOf("mod_version" to project.version.toString(),
            "neo_version" to providers.gradleProperty("neo_version").get())
    inputs.properties(values)
    filesMatching("META-INF/neoforge.mods.toml") { expand(values) }
}
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}
tasks.named<Jar>("jar") {
    from(rootProject.file("LICENSE")) { into("META-INF"); rename { "LICENSE-LITEMFINDER" } }
    from(rootProject.file("THIRD_PARTY_NOTICES.md")) { into("META-INF") }
    // ModDevGradle 2.0.147's copy renames can match core.jar as a suffix of navigation-core.jar.
    // jarJar has already copied and renamed its inputs before this task sees them.
    filesMatching("META-INF/jarjar/navigation-${project.group}.core-${project.version}.jar") {
        name = "${project.group}.navigation-core-${project.version}.jar"
    }
}
val verifyStandaloneJar by tasks.registering {
    dependsOn(tasks.named("jar"))
    val archive = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    inputs.file(archive)
    doLast {
        val entries = mutableSetOf<String>()
        zipTree(archive.get().asFile).visit { if (!isDirectory) entries.add(relativePath.pathString) }
        check(entries.count { it.startsWith("META-INF/jarjar/") && it.endsWith(".jar") } == 2) {
            "Navigation must embed only navigation-core and Core, never the inventory loader or SQLite"
        }
        check(entries.none { it.startsWith("dev/litemfinder/neoforge/") || it.contains("sqlite") })
        check(entries.contains("META-INF/services/dev.litemfinder.neoforge.client.navigation.NavigationGateway"))
        check(entries.containsAll(listOf("META-INF/LICENSE-LITEMFINDER", "META-INF/THIRD_PARTY_NOTICES.md")))
        ZipFile(archive.get().asFile).use { zip ->
            val metadata=zip.getInputStream(zip.getEntry("META-INF/jarjar/metadata.json")).reader().use { it.readText() }
            val root=JsonSlurper().parseText(metadata) as Map<*, *>
            val dependencies=root["jars"] as List<*>
            check(dependencies.map { ((it as Map<*, *>)["identifier"] as Map<*, *>)["artifact"] }.toSet()
                    == setOf("core", "navigation-core")) { "Unexpected embedded navigation dependencies" }
            dependencies.forEach { dependency ->
                val path=(dependency as Map<*, *>)["path"] as String
                check(entries.contains(path)) { "Jar-in-Jar metadata references missing entry: $path" }
            }
        }
    }
}
tasks.test { useJUnitPlatform() }
tasks.check { dependsOn(verifyStandaloneJar) }
tasks.register("verifyStandaloneClient") {
    group="verification"
    dependsOn("runStandaloneProbeClient")
    doLast {
        val log=file("run-standalone-probe/logs/latest.log").readText()
        listOf("NAVIGATION STANDALONE PROBE PASSED:","NAVIGATION STANDALONE PROBE MAIN MENU READY",
                "NAVIGATION STANDALONE PROBE SHUTDOWN").forEach { check(log.contains(it)) { "Missing standalone evidence: $it" } }
        check(!log.contains("Shutdown failure!")&&!log.contains("/FATAL]"))
    }
}
