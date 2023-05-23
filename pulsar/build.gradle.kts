plugins {
    java
}

repositories {
    jcenter()
}

dependencies {
    // https://mvnrepository.com/artifact/org.apache.zookeeper/zookeeper

    implementation("org.slf4j:slf4j-simple:1.7.21")
    implementation("ch.qos.logback:logback-classic:1.1.7")

    implementation("org.apache.zookeeper:zookeeper:3.6.3")

    implementation("org.apache.pulsar:pulsar-client:2.10.3")
    implementation("org.apache.pulsar:pulsar-client-admin:2.10.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.2")
}


val test by tasks.getting(Test::class) {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
    }
}