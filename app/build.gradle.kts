import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "dev.glass.soundbar"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.glass.soundbar"
        minSdk = 33
        targetSdk = 36
        versionCode = 7
        versionName = "0.3.1"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21); freeCompilerArgs.add("-Xcontext-parameters") }
}
dependencies {
    implementation(project(":liquidglass"))
    implementation("androidx.activity:activity-compose:1.13.0")
    compileOnly("io.github.libxposed:api:102.0.0")
    testImplementation("junit:junit:4.13.2")
}
