/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import de.undercouch.gradle.tasks.download.Download
import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("org.jetbrains.kotlin.jvm").version("2.4.0")
    id("de.undercouch.download").version("5.3.0")
}

data class BuildData(
    val ideaSDKShortVersion: String,
    // https://www.jetbrains.com/intellij-repository/releases
    val ideaSDKVersion: String,
    val sinceBuild: String,
    val untilBuild: String,
    val archiveName: String = "IntelliJ-EmmyLua",
    val jvmTarget: String = "1.8",
    val targetCompatibilityLevel: JavaVersion = JavaVersion.VERSION_25,
    val explicitJavaDependency: Boolean = true,
    val bunch: String = ideaSDKShortVersion,
    // https://github.com/JetBrains/gradle-intellij-plugin/issues/403#issuecomment-542890849
    val instrumentCodeCompilerVersion: String = ideaSDKVersion
)

val buildDataList = listOf(
    BuildData(
        ideaSDKShortVersion = "2026.2",
        ideaSDKVersion = "2026.2",
        sinceBuild = "253",
        untilBuild = "262.*",
        bunch = "212",
        targetCompatibilityLevel = JavaVersion.VERSION_25,
        jvmTarget = "25"
    )
)

val buildVersion = System.getProperty("IDEA_VER") ?: buildDataList.first().ideaSDKShortVersion

val buildVersionData = buildDataList.find { it.ideaSDKShortVersion == buildVersion }!!

val emmyDebuggerVersion = "1.3.0"

val resDir = "src/main/resources"

val isWin = Os.isFamily(Os.FAMILY_WINDOWS)

val isCI = System.getenv("CI") != null

// CI
if (isCI) {
    version = System.getenv("CI_BUILD_VERSION")
    providers.exec {
        workingDir(rootDir)
        commandLine("git", "config", "--global", "user.email", "love.tangzx@qq.com")
    }
    providers.exec {
        workingDir(rootDir)
        commandLine("git", "config", "--global", "user.name", "tangzx")
    }
}

version = "${version}-IDEA${buildVersion}"

fun getRev(): String {
    return providers.exec {
        workingDir(rootDir)
        commandLine("git", "rev-parse", "--short=7", "HEAD")
    }.standardOutput.asText.get().trim()
}

tasks.register<Download>("downloadEmmyDebugger") {
    src(
        arrayOf(
            "https://github.com/EmmyLua/EmmyLuaDebugger/releases/download/${emmyDebuggerVersion}/darwin-arm64.zip",
            "https://github.com/EmmyLua/EmmyLuaDebugger/releases/download/${emmyDebuggerVersion}/darwin-x64.zip",
            "https://github.com/EmmyLua/EmmyLuaDebugger/releases/download/${emmyDebuggerVersion}/linux-x64.zip",
            "https://github.com/EmmyLua/EmmyLuaDebugger/releases/download/${emmyDebuggerVersion}/win32-x64.zip",
            "https://github.com/EmmyLua/EmmyLuaDebugger/releases/download/${emmyDebuggerVersion}/win32-x86.zip"
        )
    )

    dest("temp")
}

tasks.register<Copy>("unzipEmmyDebugger") {
    dependsOn("downloadEmmyDebugger")
    from(zipTree("temp/win32-x86.zip")) {
        into("windows/x86")
    }
    from(zipTree("temp/win32-x64.zip")) {
        into("windows/x64")
    }
    from(zipTree("temp/darwin-x64.zip")) {
        into("mac/x64")
    }
    from(zipTree("temp/darwin-arm64.zip")) {
        into("mac/arm64")
    }
    from(zipTree("temp/linux-x64.zip")) {
        into("linux")
    }
    destinationDir = file("temp")
}

tasks.register<Copy>("installEmmyDebugger") {
    dependsOn("unzipEmmyDebugger")
    from("temp/windows/x64/") {
        include("emmy_core.dll")
        into("debugger/emmy/windows/x64")
    }
    from("temp/windows/x86/") {
        include("emmy_core.dll")
        into("debugger/emmy/windows/x86")
    }
    from("temp/linux/") {
        include("emmy_core.so")
        into("debugger/emmy/linux")
    }
    from("temp/mac/x64") {
        include("emmy_core.dylib")
        into("debugger/emmy/mac/x64")
    }
    from("temp/mac/arm64") {
        include("emmy_core.dylib")
        into("debugger/emmy/mac/arm64")
    }
    destinationDir = file("src/main/resources")
}

project(":") {
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
            marketplace()
        }
    }

    dependencies {
        implementation(fileTree(baseDir = "libs") { include("*.jar") })
        implementation("com.google.code.gson:gson:2.8.6")
        implementation("org.scala-sbt.ipcsocket:ipcsocket:1.3.0")
        implementation("org.luaj:luaj-jse:3.0.1")
        implementation("org.eclipse.mylyn.github:org.eclipse.egit.github.core:2.1.5")
        implementation("com.jgoodies:forms:1.2.1")
        intellijPlatform {
            intellijIdeaUltimate(buildVersionData.ideaSDKVersion)
            bundledModule("intellij.spellchecker")
            testFramework(TestFrameworkType.Platform)
        }
    }

    sourceSets {
        main {
            java.srcDirs("gen", "src/main/compat")
            resources.exclude("debugger/**")
            resources.exclude("std/**")
        }
    }

    /*configure<JavaPluginConvention> {
        sourceCompatibility = buildVersionData.targetCompatibilityLevel
        targetCompatibility = buildVersionData.targetCompatibilityLevel
    }*/

    intellijPlatform {
        version = version
        sandboxContainer.set(layout.buildDirectory.dir("${buildVersionData.ideaSDKShortVersion}/idea-sandbox"))
    }

    tasks.register("bunch") {
        doLast {
            val rev = getRev()
            // reset
            providers.exec {
                executable = "git"
                args("reset", "HEAD", "--hard")
            }
            // clean untracked files
            providers.exec {
                executable = "git"
                args("clean", "-d", "-f")
            }
            // switch
            providers.exec {
                executable = if (isWin) "bunch/bin/bunch.bat" else "bunch/bin/bunch"
                args("switch", ".", buildVersionData.bunch)
            }
            // reset to HEAD
            providers.exec {
                executable = "git"
                args("reset", rev)
            }
        }
    }

    tasks {
        buildPlugin {
            dependsOn("bunch", "installEmmyDebugger")
            archiveBaseName.set(buildVersionData.archiveName)
            from(fileTree(resDir) { include("!!DONT_UNZIP_ME!!.txt") }) {
                into("/${project.name}")
            }
        }

        processResources {
            dependsOn("installEmmyDebugger")
        }
//        java {
//            toolchain {
//                languageVersion.set(JavaLanguageVersion.of(buildVersionData.jvmTarget))
//            }
//
//            sourceCompatibility = buildVersionData.targetCompatibilityLevel
//            targetCompatibility = buildVersionData.targetCompatibilityLevel
//        }

        kotlin {
            jvmToolchain {
                languageVersion.set(JavaLanguageVersion.of(buildVersionData.jvmTarget))
            }

            compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(buildVersionData.jvmTarget))
            }
        }
        patchPluginXml {
            dependsOn("installEmmyDebugger")
            sinceBuild.set(buildVersionData.sinceBuild)
            untilBuild.set(buildVersionData.untilBuild)
        }

        publishPlugin {
            token.set(System.getenv("IDEA_PUBLISH_TOKEN"))
        }

        withType<PrepareSandboxTask> {
            doLast {
                copy {
                    from("src/main/resources/std")
                    into("$destinationDir/${pluginName.get()}/std")
                }
                copy {
                    from("src/main/resources/debugger")
                    into("$destinationDir/${pluginName.get()}/debugger")
                }
            }
        }
    }
}
