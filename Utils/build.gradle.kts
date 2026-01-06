plugins {
    id("java")
}

group = "ro.ppd2025"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
sourceSets {
    main {
        resources {
            srcDir("src/main/resources")
        }
    }
}
tasks.test {
    useJUnitPlatform()
}