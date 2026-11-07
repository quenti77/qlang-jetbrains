import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.20.0"
}

group = "dev.qlang"

// Deux variantes, car l'API LSP de la plateforme a changé de noms en 2026.2 (LspServer -> LspClient…) :
//   legacy (défaut) : PhpStorm 2025.3 et 2026.1 (builds 253 à 261) -> src/lsp-legacy
//   new             : PhpStorm 2026.2+ (build 262 et plus)         -> src/lsp-new   (./gradlew -PlspVariant=new …)
val lspVariant = providers.gradleProperty("lspVariant").getOrElse("legacy")
require(lspVariant in setOf("legacy", "new")) { "lspVariant doit valoir legacy ou new" }
fun prop(name: String) = providers.gradleProperty(if (lspVariant == "new") "new" + name.replaceFirstChar { it.uppercase() } else name)
version = prop("pluginVersion").get()

sourceSets {
    main {
        kotlin.srcDir("src/lsp-$lspVariant/main/kotlin")
        resources.srcDir("src/lsp-$lspVariant/main/resources")
    }
    test { kotlin.srcDir("src/lsp-$lspVariant/test/kotlin") }
}

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        val local = providers.gradleProperty("platformLocalPath").orNull
        if (local != null) local(local) else phpstorm(prop("platformVersion").get())

        bundledPlugin("org.jetbrains.plugins.textmate")
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
        zipSigner()
    }
    testImplementation("junit:junit:4.13.2")
}

kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21 } }
java { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = prop("pluginSinceBuild")
            if (lspVariant == "legacy") untilBuild = "261.*"
        }
    }
    pluginVerification {
        ides {
            val local = providers.gradleProperty("platformLocalPath").orNull
            if (local != null) local(file(local)) else create("PS", prop("platformVersion").get())
            // Vérifier aussi d'autres versions : -PverifyAlso=2026.1,2026.2
            providers.gradleProperty("verifyAlso").orNull?.split(",")?.forEach { create("PS", it.trim()) }
        }
    }
}

tasks.test {
    // Le serveur à essayer : QLANG_BIN=/chemin/vers/qlang ./gradlew test
    environment("QLANG_BIN", System.getenv("QLANG_BIN") ?: "")
}
