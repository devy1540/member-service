import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    base
    kotlin("jvm") version "2.4.21" apply false
    kotlin("plugin.spring") version "2.4.21" apply false
    id("org.springframework.boot") version "4.1.2" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

group = "com.api"
version = "0.0.1-SNAPSHOT"
description = "Member and group API services"
extra["springdocVersion"] = "3.2.0"
extra["exposedVersion"] = "1.6.1"

subprojects {
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(25)
            compilerOptions {
                freeCompilerArgs.add("-Xjsr305=strict")
            }
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.withType<BootRun>().configureEach {
        workingDir(rootProject.projectDir)
    }
}

tasks.named("build") {
    dependsOn(subprojects.map { "${it.path}:build" })
}

tasks.named("check") {
    dependsOn(subprojects.map { "${it.path}:check" })
}

tasks.named("clean") {
    dependsOn(subprojects.map { "${it.path}:clean" })
}
