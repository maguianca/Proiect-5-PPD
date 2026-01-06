plugins {
    id("java")
    id("application")
}

group = "ro.ppd2025"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation(project(":Utils"))
}
application {
    mainClass.set("ro.ppd2025.Server")
}
tasks.test {
    useJUnitPlatform()
}