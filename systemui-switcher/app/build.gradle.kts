import java.util.Properties
import java.io.File

plugins { id("com.android.application") }

val parentSigning = Properties().apply {
    rootProject.file("../signing.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
android {
    namespace = "dev.glass.systemuiswitcher"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.glass.systemuiswitcher"
        minSdk = 33
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.1"
    }
    signingConfigs {
        create("distribution") {
            val keyFile = File(parentSigning.getProperty("storeFile", ""))
            storeFile = if (keyFile.isAbsolute) keyFile else rootProject.file("../app/${keyFile.path}")
            storePassword = parentSigning.getProperty("storePassword")
            keyAlias = parentSigning.getProperty("keyAlias")
            keyPassword = parentSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.getByName("distribution") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    sourceSets["main"].assets.directories.add(layout.buildDirectory.dir("generated/systemUiAssets").get().asFile.absolutePath)
}
val prepareSystemUiAssets = tasks.register<Copy>("prepareSystemUiAssets") {
    from(rootProject.file("../系统界面-com.android.systemui-17.99.02.apk"))
    into(layout.buildDirectory.dir("generated/systemUiAssets/apks"))
    rename { "oneplus15.apk" }
    doFirst {
        check(rootProject.file("../系统界面-com.android.systemui-17.99.02.apk").isFile) {
            "The supplied OnePlus 15 SystemUI APK is missing."
        }
    }
}
tasks.named("preBuild") { dependsOn(prepareSystemUiAssets) }
dependencies { testImplementation("junit:junit:4.13.2") }
