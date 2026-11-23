import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

compose.desktop {
    application {
        mainClass = "br.archplayer.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Exe, TargetFormat.Msi)
            packageName = "ArchPlayer"
            packageVersion = "1.4.0"
            modules("java.naming", "java.management")
            vendor = "ArchPlayer"
            windows {
                menu = true
                shortcut = true
                dirChooser = true
                upgradeUuid = "5d5e8f2a-7c9b-4a60-9ad1-1f3a4d5b6c70"
            }
        }
    }
}
