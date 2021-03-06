import org.gradle.internal.impldep.org.eclipse.jgit.lib.ObjectChecker.tag
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.kt3k.gradle.plugin.CoverallsPluginExtension
import pl.allegro.tech.build.axion.release.domain.TagNameSerializationConfig
import pl.allegro.tech.build.axion.release.domain.VersionConfig
import pl.allegro.tech.build.axion.release.domain.hooks.HooksConfig


buildscript {
    repositories {
        mavenCentral()
        jcenter()
    }
    dependencies {
        classpath("org.kt3k.gradle.plugin:coveralls-gradle-plugin:2.8.2")
    }
}

plugins {
    java
    kotlin("jvm") version "1.2.51" apply false
    id("pl.allegro.tech.build.axion-release") version "1.9.2"
    jacoco
    `maven-publish`
}

repositories {
    mavenCentral()
}

scmVersion {
    tag(closureOf<TagNameSerializationConfig> {
        prefix = ""
    })

    hooks(closureOf<HooksConfig> {
        pre("fileUpdate", mapOf(
                "file" to "README.md",
                "pattern" to "{v,p -> /('$'v)/}",
                "replacement" to """{v, p -> "'$'v"}]))"""))
        pre("commit")
    })
}

val scmVer = scmVersion.version

fun Project.isSampleProject() = this.name.contains("sample")

val nonSampleProjects =  subprojects.filterNot { it.isSampleProject() }

allprojects {

    group = "com.epages"
    version = scmVer

    if (!isSampleProject()) {
        apply(plugin = "java")
        apply(plugin = "kotlin")
        apply(plugin = "maven-publish")
        apply(plugin = "jacoco")
        apply(plugin = "com.github.kt3k.coveralls")
    }
}

subprojects {

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    if (!isSampleProject()) {
        tasks.withType<JacocoReport> {
            dependsOn("test")
            reports {
                html.isEnabled = true
                xml.isEnabled = true
            }
        }
    }
}

configure<CoverallsPluginExtension> {
    sourceDirs = nonSampleProjects.flatMap { it.java.sourceSets["main"].allSource.srcDirs }.map { it.path }
    jacocoReportPath = "$buildDir/reports/jacoco/jacocoRootReport/jacocoRootReport.xml"
}


tasks {
    val jacocoTestReport = tasks["jacocoTestReport"]
    jacocoTestReport.dependsOn(nonSampleProjects.map { it.tasks["jacocoTestReport"] })
    val jacocoRootReport by creating(JacocoReport::class) {
        description = "Generates an aggregate report from all subprojects"
        group = "Coverage reports"
        dependsOn(jacocoTestReport)
        sourceDirectories = files(nonSampleProjects.flatMap { it.java.sourceSets["main"].allSource.srcDirs } )
        classDirectories = files(nonSampleProjects.flatMap { it.java.sourceSets["main"].output } )
        executionData = files(nonSampleProjects.map { File(it.buildDir, "/jacoco/test.exec")})
        reports {
            html.isEnabled = true
            xml.isEnabled = true
        }
    }
    tasks["coveralls"].dependsOn(jacocoRootReport)
}
