plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

description = "Rollback-resistant trusted time for XingZhi applications"

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        javaParameters.set(true)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/OpenXingZhi/trusted-time")
            credentials {
                username = providers.gradleProperty("GitHubPackagesUsername")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .getOrElse("")
                password = providers.gradleProperty("GitHubPackagesPassword")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .getOrElse("")
            }
        }
    }
    publications {
        create<MavenPublication>("library") {
            artifactId = "trusted-time"
            from(components["java"])
            pom {
                name.set("XingZhi Trusted Time")
                description.set(project.description)
                url.set("https://github.com/OpenXingZhi/trusted-time")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                scm {
                    url.set("https://github.com/OpenXingZhi/trusted-time")
                    connection.set("scm:git:https://github.com/OpenXingZhi/trusted-time.git")
                }
            }
        }
    }
}
