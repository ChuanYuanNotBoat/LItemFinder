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
}

tasks.test {
    useJUnitPlatform()
}

tasks.check {
    dependsOn(tasks.jar)
}
