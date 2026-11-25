plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.devtools.ksp) apply false
    alias(libs.plugins.dagger.hilt.android) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    configurations.matching { it.name == "detekt" }.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion("2.0.21")
                because("Detekt 1.23.8 requires Kotlin 2.0.21 compiler runtime")
            }
        }
    }
}
