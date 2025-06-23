plugins {
    id("com.android.application") // Standard-Plugin für Android-Anwendungen, Kotlin-Syntax
    id("org.jetbrains.kotlin.android") // Kotlin-Plugin für Android, Kotlin-Syntax
}

android {
    namespace = "com.example.servicetool" // Dein Paketname
    compileSdk = 35 // Aktuelle empfohlene SDK-Version (kann variieren)

    defaultConfig {
        applicationId = "com.example.servicetool"
        minSdk = 26 // ANGEPASST: Erhöht auf 26 wegen adaptiver Icons
        targetSdk = 35 // Sollte mit compileSdk übereinstimmen
        versionCode = 104
        versionName = "0.104" // Update-Funktion implementiert

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Für Release-Builds oft auf true setzen, um Code zu verkleinern/obfuskieren
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Spezifische Einstellungen für Debug-Builds, falls nötig
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // Kernbibliotheken
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    
    // Für App-Updates
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // UI & Layout
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.7.1")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // NEU: Telnet-Bibliothek (Apache Commons Net)
    // Diese Bibliothek wird für die direkte Telnet-Kommunikation mit der Moxa benötigt.
    implementation("commons-net:commons-net:3.11.0")

    // Testbibliotheken
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Splashscreen
    implementation("androidx.core:core-splashscreen:1.0.1")
}
