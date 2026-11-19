// The gem concept plugin. Ships two jars from one codebase: full and free.
plugins { java }

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val paperApi: String = (findProperty("paperApi") as String?) ?: "26.2.build.+"

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApi")
    implementation(project(":core"))
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-deprecation,-removal,-serial"))
}

tasks.processResources {
    val v = project.version.toString()
    inputs.property("version", v)
    filesMatching("plugin.yml") { expand("version" to v) }
}

fun editionJar(name: String, edition: String) = tasks.register<Jar>(name) {
    group = "build"
    archiveBaseName.set("FacetGems")
    archiveClassifier.set(if (edition == "free") "Free" else "")
    from(sourceSets.main.get().output)
    from(project(":core").sourceSets.main.get().output)
    from(project.resources.text.fromString("edition=$edition\n")) { rename { "edition.properties" } }
    dependsOn(tasks.classes, ":core:classes")
}

val fullJar = editionJar("fullJar", "full")
val freeJar = editionJar("freeJar", "free")
tasks.jar { enabled = false }
tasks.assemble { dependsOn(fullJar, freeJar) }
