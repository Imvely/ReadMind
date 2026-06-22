plugins {
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    kotlin("plugin.jpa") version "2.0.21"
}

group = "com.readmind"
version = "0.1.0"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

val jjwtVersion = "0.12.6"
val awsSdkVersion = "2.28.16"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    // S3 호환 스토리지(MinIO/S3) presigned URL. s3 아티팩트에 presigner 포함.
    implementation(platform("software.amazon.awssdk:bom:$awsSdkVersion"))
    implementation("software.amazon.awssdk:s3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

kotlin {
    compilerOptions { freeCompilerArgs.addAll("-Xjsr305=strict") }
}

// 기본 test는 인프라 없이 도는 슬라이스/단위만. 인프라 의존 통합 테스트는 @Tag("integration")로
// 분리하고 -PwithIntegration 플래그(또는 integrationTest 태스크)로만 실행한다.
tasks.test {
    useJUnitPlatform {
        if (!project.hasProperty("withIntegration")) excludeTags("integration")
    }
}

tasks.register<Test>("integrationTest") {
    description = "인프라(Postgres 등)가 필요한 @Tag(\"integration\") 테스트만 실행"
    group = "verification"
    useJUnitPlatform { includeTags("integration") }
    shouldRunAfter(tasks.test)
}
