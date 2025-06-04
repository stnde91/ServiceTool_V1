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
        versionCode = 1
        versionName = "1.0"

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
            // applicationIdSuffix = ".debug" // z.B. um Debug- und Release-Versionen parallel zu installieren
            // isMinifyEnabled = false // Ist standardmäßig false für debug
        }
    }

    // WICHTIG: Hier wird View Binding aktiviert
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
    implementation("androidx.core:core-ktx:1.13.1") // KTX für Kotlin-freundlichere APIs
    implementation("androidx.appcompat:appcompat:1.6.1") // Für Abwärtskompatibilität von UI-Komponenten
    implementation("com.google.android.material:material:1.12.0") // Material Design Komponenten

    // UI & Layout
    implementation("androidx.constraintlayout:constraintlayout:2.1.4") // Für ConstraintLayout
    implementation("androidx.activity:activity-ktx:1.9.0") // KTX für Activity
    implementation("androidx.fragment:fragment-ktx:1.7.1") // KTX für Fragment (wichtig für dich)

    // Navigation (falls du die Jetpack Navigation Komponente verwendest)
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // RecyclerView (explizit hinzufügen, auch wenn es manchmal transitiv dabei ist)
    implementation("androidx.recyclerview:recyclerview:1.3.2")


    // Testbibliotheken
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Hier kannst du weitere Abhängigkeiten hinzufügen, die dein Projekt benötigt
    // z.B. für Netzwerkaufrufe (Retrofit, Ktor), Bildladen (Glide, Coil), etc.
}
