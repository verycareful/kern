import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.w3c.dom.Element

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.kern"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "dev.kern"
        minSdk = 26
        targetSdk = 35
        versionCode = 31
        versionName = "0.1.12.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    sourceSets["main"].apply {
        manifest.srcFile("AndroidManifest.xml")
        java.srcDirs("src")
        res.srcDirs("res")
        assets.srcDirs("assets")
        jniLibs.srcDirs("jniLibs")
    }
    // List unit dirs explicitly so tests/instrumented (androidTest) is not also
    // compiled as a unit test, which lacks the instrumented + compose-test deps.
    sourceSets["test"].java.srcDirs("tests/editors", "tests/settings", "tests/shared")
    sourceSets["androidTest"].java.srcDirs("tests/instrumented")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        lintConfig = file("config/lint.xml")
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/DEPENDENCIES",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.poi)
    implementation(libs.poi.ooxml)
    implementation(libs.opencsv)
    implementation(libs.jsoup)
    implementation(libs.bundles.commonmark)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test)
    androidTestImplementation(libs.androidx.ui.test.junit4)
}

// ---- Open source notices ----------------------------------------------------
// Settings > Notices lists the licence of every library in the app. Library jars
// lose their META-INF licence files at packaging (see `packaging` above), so this
// task collects them at build time, together with each library's POM licence and
// the notices of the bundled assets listed in licenses/index.tsv, and writes them
// as assets under notices/: index.tsv (one entry per line) and texts/ (one file
// per distinct licence text, shared between entries). A library whose jar holds
// no licence file takes licenses/libraries/<module>.txt, or else the standard
// text from licenses/common/ for the licence its POM names.

abstract class GenerateNoticesTask : DefaultTask() {
    @get:Internal
    lateinit var libraries: ArtifactCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    val libraryFiles: FileCollection get() = libraries.artifactFiles

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val licensesDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private class Library(val group: String, val module: String, val version: String) {
        var url = ""
        val licenses = linkedSetOf<String>()
        val texts = linkedSetOf<String>()
    }

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile.resolve("notices")
        out.deleteRecursively()
        val textsDir = out.resolve("texts").apply { mkdirs() }
        val common = licensesDir.get().asFile.resolve("common")

        fun store(text: String): String {
            val normalized = text.replace("\r\n", "\n").trim() + "\n"
            val id = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
                .take(8).joinToString("") { "%02x".format(it) }
            textsDir.resolve("$id.txt").writeText(normalized)
            return id
        }

        val libraries = sortedMapOf<String, Library>()
        for (artifact in this.libraries.artifacts) {
            val id = artifact.id.componentIdentifier as? ModuleComponentIdentifier ?: continue
            val library = libraries.getOrPut("${id.group}:${id.module}") { Library(id.group, id.module, id.version) }
            library.texts += licenceFilesIn(artifact.file).map(::store)
        }
        for (library in libraries.values) {
            val pom = pomInfo(library.group, library.module, library.version, depth = 0)
            library.url = pom.first
            library.licenses += pom.second
            if (library.texts.isEmpty()) {
                val own = licensesDir.get().asFile.resolve("libraries/${library.module}.txt")
                (own.takeIf { it.isFile }?.readText() ?: canonicalText(library.licenses, common))
                    ?.let { library.texts += store(it) }
            }
        }

        val lines = mutableListOf<String>()
        for ((group, members) in libraries.values.groupBy { it.group }.toSortedMap()) {
            val versions = members.map { it.version }.distinct().joinToString(", ")
            val modules = members.joinToString(", ") { it.module }
            val licence = members.flatMap { it.licenses }.distinct().joinToString(" / ").ifEmpty { "See project" }
            val url = members.firstNotNullOfOrNull { it.url.ifEmpty { null } }.orEmpty()
            val texts = members.flatMap { it.texts }.distinct().joinToString(",")
            lines += listOf(group, "$modules ($versions)", licence, url, texts).joinToString("\t") { it.clean() }
        }
        licensesDir.get().asFile.resolve("index.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .forEach { line ->
                val (file, title, subtitle, licence, url) = line.split("\t")
                val text = store(licensesDir.get().asFile.resolve(file).readText())
                lines += listOf(title, subtitle, licence, url, text).joinToString("\t") { it.clean() }
            }
        out.resolve("index.tsv").writeText(lines.sortedBy { it.lowercase() }.joinToString("\n") + "\n")
    }

    private fun String.clean() = replace('\t', ' ').replace('\n', ' ').trim()

    /** LICENSE and NOTICE files inside a library's jar, wherever the library put them. */
    private fun licenceFilesIn(file: File): List<String> {
        if (!file.isFile || !(file.name.endsWith(".jar") || file.name.endsWith(".aar"))) {
            return file.takeIf { it.isDirectory }?.walkTopDown()?.filter { it.isFile && isLicenceName(it.name, it.parentFile.name) }
                ?.map { it.readText() }?.toList().orEmpty()
        }
        return ZipFile(file).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && isLicenceName(it.name.substringAfterLast('/'), it.name.substringBeforeLast('/', "")) }
                .map { zip.getInputStream(it).bufferedReader().readText() }
                .toList()
        }
    }

    private fun isLicenceName(name: String, folder: String): Boolean =
        (folder.isEmpty() || folder.endsWith("META-INF")) &&
            Regex("^(LICEN[CS]E|NOTICE|COPYING)(\\.(txt|md))?$", RegexOption.IGNORE_CASE).matches(name)

    /** The project URL and licence names from a POM, following parents until one declares a licence. */
    private fun pomInfo(group: String, module: String, version: String, depth: Int): Pair<String, List<String>> {
        val pom = runCatching {
            project.configurations.detachedConfiguration(project.dependencies.create("$group:$module:$version@pom"))
                .resolve().single()
        }.getOrNull() ?: return "" to emptyList()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom).documentElement
        fun child(parent: Element, name: String): Element? =
            (0 until parent.childNodes.length).map { parent.childNodes.item(it) }.filterIsInstance<Element>().firstOrNull { it.tagName == name }
        val url = child(doc, "url")?.textContent?.trim().orEmpty()
        val licenses = child(doc, "licenses")?.getElementsByTagName("name")?.let { names ->
            (0 until names.length).map { names.item(it).textContent.trim() }
        }.orEmpty()
        if (licenses.isNotEmpty() || depth >= 5) return url to licenses
        val parent = child(doc, "parent") ?: return url to licenses
        val (pg, pa, pv) = listOf("groupId", "artifactId", "version").map { child(parent, it)?.textContent?.trim().orEmpty() }
        val inherited = pomInfo(pg, pa, pv, depth + 1)
        return url.ifEmpty { inherited.first } to inherited.second
    }

    /** The standard text for a licence named only in a POM, when licenses/common has it. */
    private fun canonicalText(names: Collection<String>, common: File): String? {
        val joined = names.joinToString(" ").lowercase()
        val spdx = when {
            "apache" in joined && "2" in joined -> "Apache-2.0"
            else -> return null
        }
        return common.resolve("$spdx.txt").takeIf { it.isFile }?.readText()
    }
}

androidComponents {
    onVariants { variant ->
        val runtime = configurations.getByName("${variant.name}RuntimeClasspath")
        val notices = tasks.register<GenerateNoticesTask>("generate${variant.name.replaceFirstChar(Char::uppercase)}Notices") {
            libraries = runtime.incoming.artifactView {
                attributes { attribute(Attribute.of("artifactType", String::class.java), "android-classes-jar") }
            }.artifacts
            licensesDir.set(layout.projectDirectory.dir("licenses"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(notices, GenerateNoticesTask::outputDir)
    }
}
