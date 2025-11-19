@file:Suppress("UnstableApiUsage")

import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64


plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
    id("signing")
}

version = "1.0.0"

android {
    namespace = "com.andymic.jpeg2k"
    compileSdk {
        version = release(36)
    }
    ndkVersion = "29.0.14206865"
    lint.targetSdk = 36

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags.addAll(listOf())
            }
        }
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    buildFeatures {
        buildConfig = true
    }
    externalNativeBuild {
        cmake {
            version = "4.1.2"
            path = file("CMakeLists.txt")
        }
    }
    kotlin {
        jvmToolchain(11)
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

/*************************************************************/
/******************* PUBLISHING STUFF ************************/
/*************************************************************/

//Publish to Maven Central
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = "com.andymic.jpeg2k"
                artifactId = "jpeg2k"
                version = "$version"

                from(components["release"])

                pom {
                    name.set("jpeg2k")
                    description.set("A fork of JP2ForAndroid which is a JPEG2000 wrapper for Android using OpenJPEG")
                    url.set("https://github.com/AndyMic03/JPEG2K")

                    scm {
                        url.set("https://github.com/AndyMic03/JPEG2K.git")
                        connection.set("scm:git:https://github.com/AndyMic03/JPEG2K.git")
                        developerConnection.set("scm:git:ssh://git@github.com:AndyMic03/JPEG2K.git")
                    }

                    developers {
                        developer {
                            id.set("andymic")
                            name.set("Andreas Michael")
                            email.set("ateasm03@gmail.com")
                        }
                    }

                    licenses {
                        license {
                            name = "The Apache License, Version 2.0"
                            url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                        }
                        license {
                            name = "BSD 2-Clause License"
                            url = "https://opensource.org/licenses/BSD-2-Clause"
                            distribution = "repo"
                        }
                    }
                }
            }
        }
        repositories {
            maven {
                name = "ossrh-staging-api"
                url =
                    uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")

                credentials {
                    username = providers.gradleProperty("centralPortalUsername").orNull
                    password = providers.gradleProperty("centralPortalPassword").orNull
                }
            }
        }
    }
}

signing {
    val signingKeyId: String? by project
    val signingPassword: String? by project
    val signingKey: String? by project

    if (!signingKey.isNullOrEmpty()) {
        useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    } else {
        useGpgCmd()
    }
    afterEvaluate {
        sign(publishing.publications.getByName("maven"))
    }
}

tasks.register("finalizeDeployment") {
    doLast {
        val myNamespace = "com.andymic"

        val user = project.findProperty("centralPortalUsername")?.toString()
        val password = project.findProperty("centralPortalPassword")?.toString()

        if (user.isNullOrEmpty() || password.isNullOrEmpty()) {
            error("Missing centralPortalUsername or centralPortalPassword properties")
        }

        val auth = "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray())
        val endpoint =
            "https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/$myNamespace"

        println("\n=== FINALIZING DEPLOYMENT FOR $myNamespace ===")
        println("Sending POST request to: $endpoint")

        with(URL(endpoint).openConnection() as HttpURLConnection) {
            requestMethod = "POST"
            setRequestProperty("Authorization", auth)

            val code = responseCode
            val msg = responseMessage

            println("Response: $code $msg")

            if (code in 200..299) {
                println("SUCCESS! The files should appear in the portal in ~30 seconds.")
            } else {
                println("FAILED. Check the namespace and credentials.")
                try {
                    println(errorStream?.bufferedReader()?.readText())
                } catch (_: Exception) {
                }
            }
        }
    }
}