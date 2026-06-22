package com.readmind.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

/** S3/MinIO 클라이언트 빈. presigner는 presigned PUT/GET URL 발급, client는 서버측 작업용. */
@Configuration
class S3Config(
    private val props: S3Properties,
) {
    private val credentials = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(props.accessKey, props.secretKey),
    )

    // MinIO는 path-style 강제(가상 호스트 스타일 미지원).
    private val serviceConfig = S3Configuration.builder()
        .pathStyleAccessEnabled(props.pathStyle)
        .build()

    @Bean
    fun s3Presigner(): S3Presigner = S3Presigner.builder()
        .endpointOverride(URI.create(props.endpoint))
        .region(Region.of(props.region))
        .credentialsProvider(credentials)
        .serviceConfiguration(serviceConfig)
        .build()

    @Bean
    fun s3Client(): S3Client = S3Client.builder()
        .endpointOverride(URI.create(props.endpoint))
        .region(Region.of(props.region))
        .credentialsProvider(credentials)
        .serviceConfiguration(serviceConfig)
        .build()
}
