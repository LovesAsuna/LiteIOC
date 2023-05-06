import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.20"
    application
    id("me.champeau.jmh") version "0.7.1"
}

group = "com.hyosakura"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.dom4j:dom4j:2.1.4")
    implementation("org.slf4j:slf4j-api:2.0.6")
    implementation("net.bytebuddy:byte-buddy:1.14.2")
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.20.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.7.20")
    jmh("org.openjdk.jmh:jmh-core:1.36")
    testImplementation("org.openjdk.jmh:jmh-core:1.36")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.36")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.36")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs += "-Xjvm-default=all"
}

tasks.withType<JavaCompile> {
    this.sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

application {
    mainClass.set("MainKt")
}