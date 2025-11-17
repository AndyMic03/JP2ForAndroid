@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.application")
    id("maven-publish")
}

android {
    namespace = "com.gemalto.jp2"
    compileSdk {
        version = release(36)
    }
    ndkVersion =  "29.0.14033849 rc4"

    defaultConfig {
        minSdk =  21
        targetSdk = 36
        versionName = "1.0.3"
        versionCode = 4
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags.addAll(kotlin.collections.listOf())
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    externalNativeBuild {
        cmake {
            file("CMakeLists.txt")
        }
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("androidx.annotation:annotation:1.1.0")
    //testImplementation("junit:junit:4.13.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")
}

/*************************************************************/
/******************* PUBLISHING STUFF ************************/
/*************************************************************/
//create a sources jar
val sourceJar by tasks.registering(Jar::class) {
    from(android.sourceSets["main"].java.srcDirs)
    archiveClassifier.set("sources")
}

//publish to a local Maven repository with the sources jar and all dependencies
afterEvaluate {publishing {
    publications {
        create<MavenPublication>("aarRelease") {
            groupId = "com.gemalto.jp2"
            artifactId = "jp2-android"
            version = "${android.defaultConfig.versionName}"
            afterEvaluate {
                from(components.getByName("release"))
            }
            artifact("build/outputs/aar/library-release.aar")
        }
    }
    repositories {
        maven {
            url = uri("build/repo")
        }
    }
}}