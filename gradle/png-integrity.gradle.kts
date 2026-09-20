import java.io.File
import org.gradle.api.tasks.Exec

val blinkRepositoryRoot = generateSequence(rootProject.projectDir) { it.parentFile }
    .firstOrNull {
        File(it, "app/src/main/res").isDirectory &&
            File(it, "desktopApp/src/main/resources").isDirectory
    }
    ?: rootProject.projectDir

val blinkPngValidator = File(blinkRepositoryRoot, "scripts/ValidatePngAssets.java")
val blinkPngAssets = fileTree(blinkRepositoryRoot) {
    include("app/src/main/res/**/*.png")
    include("desktopApp/src/main/resources/**/*.png")
    include("web/**/*.png")
}

tasks.register<Exec>("validatePngIntegrity") {
    group = "verification"
    description = "Validates PNG signatures, structure, and every chunk CRC used by BLINK clients."
    inputs.file(blinkPngValidator)
    inputs.files(blinkPngAssets)
    workingDir(blinkRepositoryRoot)
    commandLine("java", blinkPngValidator.absolutePath, blinkRepositoryRoot.absolutePath)
}
