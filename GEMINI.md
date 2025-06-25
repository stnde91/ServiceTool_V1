# Gemini Project Configuration

This file helps Gemini understand your project better.

## Project Overview

This is an Android Service Tool application, likely designed for industrial use on devices running Android 14 or newer. It seems to involve Telnet communication, possibly for interacting with Moxa devices.

## Build & Run Commands

*   **Build:** `./gradlew build`
*   **Run:** The app can be run directly from Android Studio or by installing the generated APK.
*   **Clean Build:** `./gradlew clean build`
*   **Unit Tests:** `./gradlew test`
*   **Instrumented Tests:** `./gradlew connectedAndroidTest`

## Tech Stack

*   **Language:** Kotlin
*   **Framework:** Android Native (minSdk 34 - Android 14)
*   **Build Tool:** Gradle

### Key Libraries

*   **UI & Foundation:**
    *   `androidx.core:core-ktx`: Core Kotlin extensions.
    *   `androidx.appcompat:appcompat`: Provides backward compatibility for older Android versions.
    *   `com.google.android.material:material`: Material Design components.
    *   `androidx.constraintlayout:constraintlayout`: For building complex layouts.
    *   `androidx.activity:activity-ktx` & `androidx.fragment:fragment-ktx`: Activity and Fragment management with Kotlin extensions.
    *   `androidx.recyclerview:recyclerview`: For displaying large data sets efficiently.
    *   `androidx.viewpager2:viewpager2`: For creating swipeable views with tabs.
    *   `androidx.core:core-splashscreen`: For creating a splash screen.
*   **Navigation:**
    *   `androidx.navigation:navigation-fragment-ktx` & `androidx.navigation:navigation-ui-ktx`: For in-app navigation.
*   **Networking:**
    *   `commons-net:commons-net`: For Telnet communication.
*   **Testing:**
    *   `junit:junit`: For unit testing.
    *   `androidx.test.ext:junit`: For AndroidX testing.
    *   `androidx.test.espresso:espresso-core`: For UI testing.

## Coding Style & Conventions

*   The project uses Kotlin with the standard Android conventions.
*   ViewBinding is enabled (`viewBinding = true`), so views should be accessed via the generated binding classes.
*   The project uses the `libs.versions.toml` file for dependency management. New dependencies should be added there.