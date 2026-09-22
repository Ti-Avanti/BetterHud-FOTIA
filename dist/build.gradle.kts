import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.jar.JarFile

plugins {
    alias(libs.plugins.conventions.standard)
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":api"))
    compileOnly(libs.bundles.adventure)
    compileOnly(libs.bundles.library)

    testImplementation(libs.bundles.library)

    compileOnly("me.lucko:jar-relocator:1.7")
}

// 仅重编译字宽优化涉及的类，保留同版本官方 JAR 中的所有平台适配与资源。
// 用于验证指定版本，避免为不相关的 Minecraft 版本重新下载开发包。
tasks.register<ShadowJar>("optimizedBukkitJar") {
    dependsOn(tasks.classes)
    val baseJar = providers.gradleProperty("base-bukkit-jar").map { rootProject.file(it) }
    inputs.file(baseJar)
    configurations = emptyList()
    archiveFileName = "BetterHud-bukkit-${project.version}-fotia-memory.jar"
    destinationDirectory = rootProject.layout.buildDirectory.dir("libs")
    val patchedClasses = listOf(
        "kr/toxicity/hud/text/GlyphWidthCache*.class",
        "kr/toxicity/hud/element/TextElement*.class",
        "kr/toxicity/hud/hud/HudTextParser*.class",
        "kr/toxicity/hud/popup/PopupLayout*.class"
    )
    from(sourceSets.main.get().output) { include(patchedClasses) }
    from(baseJar.map { zipTree(it) }) {
        exclude(patchedClasses)
        exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
        filesMatching("plugin.yml") {
            filter { line -> if (line.startsWith("version:")) "version: '${project.version}-fotia-memory'" else line }
        }
    }
    relocate("kotlin", "${project.group}.shaded.kotlin")
    doFirst {
        JarFile(baseJar.get()).use { jar ->
            val baseVersion = jar.manifest.mainAttributes.getValue("Version")
            require(baseVersion == project.version.toString() || baseVersion.startsWith("${project.version}-SNAPSHOT-")) {
                "Base plugin version $baseVersion does not match source version ${project.version}"
            }
            manifest.attributes(jar.manifest.mainAttributes.entries.associate { it.key.toString() to it.value })
            manifest.attributes("Version" to "${project.version}-fotia-memory")
        }
    }
}
