// Top-level build file where you can add configuration options common to all sub-projects/modules.
//plugins {
//    id("com.android.application") version "8.3.1" apply false
//    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
//}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath ("com.android.tools.build:gradle:8.3.1")
        classpath ("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.0")
        // other dependencies
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}