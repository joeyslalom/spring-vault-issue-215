package github.joeyslalom.app

import com.amazonaws.auth.AWSSessionCredentials
import com.amazonaws.auth.AWSSessionCredentialsProvider
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.vault.annotation.VaultPropertySource
import java.time.LocalDateTime

@SpringBootApplication
class Issue215Application {
    private val log: Logger = LoggerFactory.getLogger(Issue215Application::class.java)

    @Bean
    fun runner(sqsClient: SqsClient) = ApplicationRunner {
        log.info("my rotating secret lasts 60 minutes, each request is 20 seconds")
        for (i in 0..181) {
            log.info("loop $i at ${LocalDateTime.now()}")
            sqsClient.fetch()
        }
    }
}

fun main(args: Array<String>) {
    runApplication<Issue215Application>(*args)
}

private const val STS_PREFIX_SQS = "sts.sqs."

@Configuration
@VaultPropertySource(
        value = ["aws/sts/use1-ucp-dev-sqs-app"],  // TODO can this be a ${spring.bootstrap.property}?
        renewal = VaultPropertySource.Renewal.ROTATE,
        propertyNamePrefix = STS_PREFIX_SQS
)
class VaultProps(val env: Environment) {
    fun credentials() = Credentials(
            env.getProperty(STS_PREFIX_SQS + "access_key", ""),
            env.getProperty(STS_PREFIX_SQS + "secret_key", ""),
            env.getProperty(STS_PREFIX_SQS + "security_token", "")
    )
}

data class Credentials(
        private val accessKey: String,
        private val secretKey: String,
        private val securityToken: String) : AWSSessionCredentials {

    override fun getSessionToken(): String = securityToken

    override fun getAWSAccessKeyId(): String = accessKey

    override fun getAWSSecretKey(): String = secretKey
}

@Configuration
class Config {
    @Bean
    fun credentialsProvider(vaultProps: VaultProps) = object : AWSSessionCredentialsProvider {

        override fun getCredentials(): AWSSessionCredentials = vaultProps.credentials()

        override fun refresh() {
        }
    }

    @Bean
    fun amazonSqs(@Value("us-east-1") region: String,
                  provider: AWSSessionCredentialsProvider): AmazonSQSAsync {
        return AmazonSQSAsyncClientBuilder.standard()
                .withRegion(region)
                .withCredentials(provider)
                .build()
    }
}

@Component
class SqsClient(val amazonSqsAsync: AmazonSQSAsync) {
    fun fetch(): List<Message> {
        val request = ReceiveMessageRequest()
                .withQueueUrl("use1-ucp-dev-joey-test-queue")
                .withWaitTimeSeconds(20)
        return amazonSqsAsync.receiveMessage(request).messages
    }
}