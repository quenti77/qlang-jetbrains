import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.20.0"
}

group = "dev.qlang"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        val local = providers.gradleProperty("platformLocalPath").orNull
        if (local != null) local(local) else phpstorm(providers.gradleProperty("platformVersion").get())

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
        ideaVersion { sinceBuild = providers.gradleProperty("pluginSinceBuild") }
    }
    pluginVerification {
        ides {
            val local = providers.gradleProperty("platformLocalPath").orNull
            if (local != null) local(file(local)) else create("PS", providers.gradleProperty("platformVersion").get())
        }
    }
}

tasks.test {
    // Le serveur à essayer : QLANG_BIN=/chemin/vers/qlang ./gradlew test
    environment("QLANG_BIN", System.getenv("QLANG_BIN") ?: "")
}
