// Top-level build file: só declara as versões dos plugins (aplicados em :app).
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 já compila Kotlin (built-in Kotlin, sem kotlin-android). Este plugin também põe o
    // Kotlin Gradle Plugin da versão `kotlin` do catálogo no classpath, que é a versão usada
    // pelo Kotlin embutido do AGP (o AGP sozinho traria o KGP 2.2.10).
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
