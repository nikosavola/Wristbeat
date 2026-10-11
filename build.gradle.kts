// AGP 9 built-in Kotlin would otherwise pull its own bundled Kotlin (2.2.10, per the AGP 9.4.0
// POM). Force ours onto the buildscript classpath so built-in Kotlin compiles with it and the
// compose-compiler plugin (which must match the Kotlin version exactly) stays aligned.
buildscript {
  // Literal, not a version-catalog accessor: those aren't available this early in evaluation.
  // Keep in sync with `kotlin` in gradle/libs.versions.toml.
  dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0") }
}

plugins {
  alias(libs.plugins.android.application) apply false
  // kotlin.android is deliberately absent: AGP 9 provides built-in Kotlin, and applying it on top
  // errors on a duplicate `kotlin` extension.
  alias(libs.plugins.compose.compiler) apply false
}