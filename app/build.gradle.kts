plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
    application
}

repositories {
    mavenCentral()
}

dependencies {

    implementation(libs.logback)
    implementation(libs.jackson.dataformat.toml)
    implementation(libs.jansi)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

tasks.withType<JavaCompile>() {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<JavaExec>() {
    jvmArgs("--enable-preview")
    standardInput = System.`in`
}

application {
    // Define the main class for the application.
    mainClass = "rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.App"
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
    jvmArgs("--enable-preview")
}
