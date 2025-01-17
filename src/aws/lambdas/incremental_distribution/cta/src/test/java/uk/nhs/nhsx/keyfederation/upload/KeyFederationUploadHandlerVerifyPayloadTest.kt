@file:Suppress("TestFunctionName")

package uk.nhs.nhsx.keyfederation.upload

import com.amazonaws.services.lambda.runtime.events.ScheduledEvent
import com.amazonaws.services.s3.model.S3ObjectSummary
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.mockk.every
import io.mockk.slot
import io.mockk.spyk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.map
import uk.nhs.nhsx.core.TestEnvironments
import uk.nhs.nhsx.core.aws.s3.BucketName
import uk.nhs.nhsx.core.aws.secretsmanager.SecretName
import uk.nhs.nhsx.core.aws.ssm.ParameterName
import uk.nhs.nhsx.core.events.RecordingEvents
import uk.nhs.nhsx.domain.ReportType.CONFIRMED_TEST
import uk.nhs.nhsx.domain.ReportType.UNKNOWN
import uk.nhs.nhsx.domain.TestType.LAB_RESULT
import uk.nhs.nhsx.domain.TestType.RAPID_RESULT
import uk.nhs.nhsx.domain.TestType.RAPID_SELF_REPORTED
import uk.nhs.nhsx.keyfederation.InMemoryBatchTagService
import uk.nhs.nhsx.keyfederation.InteropClient
import uk.nhs.nhsx.keyfederation.TestKeyPairs
import uk.nhs.nhsx.testhelper.ContextBuilder.Companion.aContext
import uk.nhs.nhsx.testhelper.assertions.captured
import uk.nhs.nhsx.testhelper.mocks.FakeInteropDiagnosisKeysS3
import uk.nhs.nhsx.testhelper.s3.S3ObjectSummary
import uk.nhs.nhsx.testhelper.wiremock.WireMockExtension
import java.time.Instant

@ExtendWith(WireMockExtension::class)
class KeyFederationUploadHandlerVerifyPayloadTest(private val wireMock: WireMockServer) {

    private val events = RecordingEvents()
    private val now = Instant.parse("2020-02-05T10:00:00.000Z")
    private val submissionDate = Instant.parse("2020-02-04T10:00:00.000Z")

    @Test
    fun `upload exposure keys uses correct test type and report type`() {
        wireMock.stubFor(
            post("/diagnosiskeys/upload")
                .withHeader("Authorization", equalTo("Bearer DUMMY_TOKEN"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(
                            """
                            {
                                "batchTag": "75b326f7-ae6f-42f6-9354-00c0a6b797b3",
                                "insertedExposures":0
                            }
                            """.trimIndent()
                        )
                )
        )

        val fakeS3 = FakeInteropDiagnosisKeysS3(
            S3ObjectSummary("mobile/LAB_RESULT/abc", "SUBMISSION_BUCKET", submissionDate),
            S3ObjectSummary("mobile/RAPID_RESULT/def", "SUBMISSION_BUCKET", submissionDate),
            S3ObjectSummary("mobile/RAPID_SELF_REPORTED/ghi", "SUBMISSION_BUCKET", submissionDate)
        )

        val payload = slot<List<ExposureUpload>>()
        val interopClient = spyk(InteropClient(wireMock)) {
            every { uploadKeys(capture(payload)) } answers { callOriginal() }
        }

        KeyFederationUploadHandler(wireMock, fakeS3, interopClient)
            .handleRequest(ScheduledEvent(), aContext())

        wireMock.verify(1, postRequestedFor(urlEqualTo("/diagnosiskeys/upload")))

        expectThat(payload).captured
            .map { it.copy(rollingStartNumber = 1) } // ignore
            .containsExactlyInAnyOrder(
            ExposureUpload(
                keyData = fakeS3.getEncodedKeyData(),
                rollingStartNumber = 1,
                transmissionRiskLevel = 7,
                rollingPeriod = 144,
                regions = listOf("GB-EAW"),
                testType = LAB_RESULT,
                reportType = CONFIRMED_TEST,
                daysSinceOnset = 0
            ), ExposureUpload(
                keyData = fakeS3.getEncodedKeyData(),
                rollingStartNumber = 1,
                transmissionRiskLevel = 7,
                rollingPeriod = 144,
                regions = listOf("GB-EAW"),
                testType = RAPID_RESULT,
                reportType = UNKNOWN,
                daysSinceOnset = 0
            ), ExposureUpload(
                keyData = fakeS3.getEncodedKeyData(),
                rollingStartNumber = 1,
                transmissionRiskLevel = 7,
                rollingPeriod = 144,
                regions = listOf("GB-EAW"),
                testType = RAPID_SELF_REPORTED,
                reportType = UNKNOWN,
                daysSinceOnset = 0
            )
        )
    }

    private fun KeyFederationUploadHandler(
        wireMockServer: WireMockServer,
        fakeS3: FakeInteropDiagnosisKeysS3,
        interopClient: InteropClient
    ) = KeyFederationUploadHandler(
        environment = TestEnvironments.environmentWith(),
        clock = { now },
        events = RecordingEvents(),
        submissionBucket = BucketName.of("SUBMISSION_BUCKET"),
        config = KeyFederationUploadConfig(wireMockServer),
        batchTagService = InMemoryBatchTagService(),
        interopClient = interopClient,
        awsS3Client = fakeS3
    )

    private fun InteropClient(wireMockServer: WireMockServer) = InteropClient(
        interopBaseUrl = wireMockServer.baseUrl(),
        authToken = "DUMMY_TOKEN",
        jws = JWS(KmsCompatibleSigner(TestKeyPairs.ecPrime256r1.private)),
        events = events
    )

    private fun KeyFederationUploadConfig(wireMockServer: WireMockServer) =
        KeyFederationUploadConfig(
            maxSubsequentBatchUploadCount = 100,
            initialUploadHistoryDays = 14,
            maxUploadBatchSize = 0,
            uploadFeatureFlag = { true },
            uploadRiskLevelDefaultEnabled = false,
            uploadRiskLevelDefault = -1,
            interopBaseUrl = wireMockServer.baseUrl(),
            interopAuthTokenSecretName = SecretName.of("authToken"),
            signingKeyParameterName = ParameterName.of("parameter"),
            stateTableName = "DUMMY_TABLE",
            region = "GB-EAW",
            federatedKeyUploadPrefixes = emptyList()
        )

    private fun FakeInteropDiagnosisKeysS3(vararg summaries: S3ObjectSummary) =
        FakeInteropDiagnosisKeysS3(summaries.toList())
}
