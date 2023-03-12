plugins {
    java
}

repositories {
    jcenter()
}

dependencies {
    implementation("org.apache.pulsar:pulsar-client:2.10.3")
    implementation("org.apache.pulsar:pulsar-client-admin:2.10.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.2")
}


val test by tasks.getting(Test::class) {
    useJUnitPlatform()
}