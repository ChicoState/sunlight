plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktfmt) apply false
}

tasks.register("verify") {
    group = "verification"
    description = "Runs the infrastructure quality gates available before application code exists."
    dependsOn(":app:check", ":app:assembleDebug")
}
