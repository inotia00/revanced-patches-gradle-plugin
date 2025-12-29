package app.revanced.patches.gradle

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper
import kotlin.io.path.Path
import kotlin.io.path.pathString

@Suppress("unused")
abstract class ExtensionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("extension", ExtensionExtension::class.java)

        project.configureAndroid()
        project.configureArtifactSharing(extension)
    }

    /**
     * Setup sharing the extension dex file with the consuming patches project.
     */
    private fun Project.configureArtifactSharing(extension: ExtensionExtension) {
        val syncExtensionTask = tasks.register("syncExtension", Sync::class.java) {
            it.apply {
                dependsOn("assembleRelease")

                val apk = layout.buildDirectory.dir("outputs/apk/release").map { dir ->
                    dir.asFile.listFiles { _, name -> name.endsWith(".apk") }!!.first()
                }

                from(zipTree(apk).matching { include("classes.dex") })
                into(
                    layout.buildDirectory.zip(extension.name) { buildDirectory, extensionName ->
                        buildDirectory.dir("revanced/${Path(extensionName).parent.pathString}")
                    },
                )

                rename { "${Path(extension.name.get()).fileName}" }
            }
        }

        configurations.consumable("extensionConfiguration").also { configuration ->
            artifacts.add(
                configuration.name,
                layout.buildDirectory.dir("revanced"),
            ) { artifact -> artifact.builtBy(syncExtensionTask) }
        }
    }

    /**
     * Set up the Android plugin for the extension project.
     */
    private fun Project.configureAndroid() {
        pluginManager.apply {
            apply(AppPlugin::class.java)
            apply(KotlinAndroidPluginWrapper::class.java)
        }

        extensions.configure(BaseAppModuleExtension::class.java) {
            it.apply {
                compileSdk = 35

                defaultConfig {
                    minSdk = 24
                    multiDexEnabled = false
                }

                buildTypes {
                    release {
                        // If this were true by default, and no proguard files would be present,
                        // no dex file would be generated.
                        isMinifyEnabled = false

                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                    }
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
        }

        extensions.configure(KotlinAndroidProjectExtension::class.java) {
            it.apply {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }
}
