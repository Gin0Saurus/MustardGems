// Pure Java game rules. No Minecraft dependency, so it builds and tests anywhere.
plugins { java }

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

// Dependency-free test runner (see CoreTests.java). Fails the build on any failing test.
val coreTests by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the core rule tests."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.gin0saurus.facet.core.CoreTests")
}
tasks.named("check") { dependsOn(coreTests) }
