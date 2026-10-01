import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    application
    checkstyle
    pmd
    id("net.ltgt.errorprone") version "5.1.1"
    id("net.ltgt.nullaway") version "3.2.0"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    checkstyle("com.puppycrawl.tools:checkstyle:14.0.0")

    errorprone("com.google.errorprone:error_prone_core:2.49.0")
    errorprone("com.uber.nullaway:nullaway:0.13.4")

    implementation("ch.qos.logback:logback-classic:1.6.3")
    implementation("com.github.spotbugs:spotbugs-annotations:4.10.4")
    implementation("org.jspecify:jspecify:1.0.0")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.junit-pioneer:junit-pioneer:2.3.0")
}

nullaway {
    jspecifyMode = true
    onlyNullMarked = true
}

tasks.withType<JavaCompile> {
    options.errorprone {
        disableAllChecks = true
        disableWarningsInGeneratedCode = true
        excludedPaths = ".*/build/generated/.*"
        error("RedundantNullCheck")
        nullaway {
            error()
        }
    }
    options.release = 25
}

sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}
val integrationTestImplementation = configurations.getByName("integrationTestImplementation") {
    extendsFrom(configurations.implementation.get(), configurations.testImplementation.get())
}
val integrationTestRuntimeOnly = configurations.getByName("integrationTestRuntimeOnly")

configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get(), configurations.testRuntimeOnly.get())

dependencies {
    add("integrationTestImplementation", "org.junit.platform:junit-platform-launcher")
}


tasks.test {
    maxHeapSize = "128m"
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"

    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter("test")

    useJUnitPlatform()
    maxHeapSize = "128m"

    testLogging {
        events("passed")
    }
    environment(
        "CURRENT_DATE", DateTimeFormatter.ISO_LOCAL_DATE.format(ZonedDateTime.now(ZoneId.of("Europe/Moscow")))
    )
    environment("CI", "true")
    outputs.upToDateWhen { false }
}

checkstyle {
    configFile = project.layout.projectDirectory.file("checkstyle.xml").asFile
    maxWarnings = 0
}


pmd {
    isConsoleOutput = true
    toolVersion = "7.16.0"
    rulesMinimumPriority = 5
    ruleSetFiles(project.layout.projectDirectory.file("pmd.xml"))
}

tasks.register("codeStyleChecks") {
    group = "verification"
    dependsOn(
        "checkstyleMain",
        "pmdMain",
    )
}

tasks.check {
    dependsOn(tasks.test, integrationTest, "codeStyleChecks")
}

tasks.named("pmdIntegrationTest") {
    enabled = false
}

tasks.named("pmdTest") {
    enabled = false
}

tasks.named("checkstyleIntegrationTest") {
    enabled = false
}

application {
    mainClass = "company.vk.edu.distrib.compute.ServiceLauncher"
    applicationDefaultJvmArgs = listOf("-Xmx128M")
}