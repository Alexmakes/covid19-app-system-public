
## API Patterns

There are several general patterns of APIs in use:
* [Submission](#submission)
* [Distribution](#distribution)
* [Upload](#upload)
* [Circuit Breaker](#circuit-breaker)
* [Connector](#connector)
* [Exporter](#exporter)

Details of specific APIs can be found using the links provided or, for the fuller list, in [API contracts](api-contracts/README.md).

### Submission

Submission APIs are used by the App to submit data to the backend.

* Endpoint schema: ```https://<FQDN>/submission/<payload type>```
* Payload content-type: application/json
* Authentication: ```Authorization: Bearer <API KEY>``` (see [API Security](api-security.md))

| API Name | API Group | API Contract | User/Client impact |
| --- | --- | --- | --- |
| Crash Report | Submission | [crash-report-submission.md](api-contracts/mobile-facing/submission/crash-report-submission.md) | Used to send crash reports. |
| Diagnosis Key | Submission | [diagnosis-key-submission.md](api-contracts/mobile-facing/submission/diagnosis-key-submission.md) | Used to send anonymous diagnosis keys in the event of a positive diagnosis, and user consent to share. |
| Empty | Submission | [empty-submission.md](api-contracts/mobile-facing/submission/empty-submission.md) | Used for time-based traffic obfuscation. |
| Isolation Payment Claim Token Generation | Submission | [isolation-payment-claim-token-submission.md](api-contracts/mobile-facing/submission/isolation-payment-claim-token-submission.md) | Used to generate and activate an IPC token, for isolation payment claims. |
| Mobile Analytics  | Submission | [analytics-submission.md](api-contracts/mobile-facing/submission/analytics-submission.md) | Used to send analytics data, daily. |
| Mobile Analytics Events | Submission | [analytics-event-submission.md](api-contracts/mobile-facing/submission/analytics-event-submission.md) | Used to send anonymous epidemiological data. |
| Virology Test Order | Submission | [virology-test-order-submission.md ](api-contracts/mobile-facing/submission/virology-test-order-submission.md) | Used to order a test and poll for a result, using a CTA token generated by the order.  |
| Virology Test Result Token  | Submission | [virology-test-result-token-submission.md ](api-contracts/mobile-facing/submission/virology-test-result-token-submission.md) | Used to get a result for a test booked outside of the App, using a CTA token from a test result notification.  |

### Distribution

Distribution APIs are used by the App to ensure datasets and configurations are kept up-to-date, by polling periodically.

* Endpoint schema: ```https://<FQDN>/distribution/<payload specific>```
* `FQDN`: One (CDN-) hostname for all distribute APIs
* HTTP verb: GET
* Payload content-type: payload specific
* Authentication: none (see [API Security](api-security.md))


| API Name | API Group | API Contract | User/Client impact |
| --- | --- | --- | --- |
| App Availability | Distribution | [app-availability-distribution.md](api-contracts/mobile-facing/distribution/app-availability-distribution.md) | Gets the latest minimum and recommended OS and App versions, for checking on App launch. |
| Diagnosis Key | Distribution | [diagnosis-key-distribution.md](api-contracts/mobile-facing/distribution/diagnosis-key-distribution.md) | Gets the latest diagnosis keys, valid for 14 days (as per EN API), for matching against the user's recent contacts. |
| Identified Risk Venues | Distribution | [risky-venue-distribution.md](api-contracts/mobile-facing/distribution/risky-venue-distribution.md) | Gets the latest list of venues identified as risky, for matching against the user's venue check-ins. |
| Local Messages | Distribution | [local-messages-distribution.md](api-contracts/mobile-facing/distribution/local-messages-distribution.md) | Gets the latest messages, specific to local authorities, for matching against the user's local authority. |
| Postal District Risk Levels | Distribution | [postal-district-risk-level-distribution.md](api-contracts/mobile-facing/distribution/postal-district-risk-level-distribution.md) | Gets the latest risk indicator for postal districts, for matching against the user's postal district. |
| Symptoms Questionnaire | Distribution | [symptoms-questionnaire-distribution.md](api-contracts/mobile-facing/distribution/symptoms-questionnaire-distribution.md) | Gets the latest symptomatic questionnaire and advice, set by the NHS Medical Policy team, for use by users reporting symptoms. |
| Exposure Risk Configuration | Distribution | [exposure-risk-configuration.md](api-contracts/mobile-facing/configuration/exposure-risk-configuration.md) | Gets the latest configuration for exposure risk analysis. |
| Identified Risk Venues Configuration | Distribution | [risky-venue-configuration.md](api-contracts/mobile-facing/configuration/risky-venue-configuration.md) | Gets the latest configuration for venue risk notification e.g. how long a user can book a test after visiting a risky venue. |
| Self Isolation Configuration | Distribution | [self-isolation-configuration.md](api-contracts/mobile-facing/configuration/self-isolation-configuration.md) | Gets the latest configuration for self isolation e.g. how long a user needs to isolate for. |

### Upload

Upload APIs are used by external systems to submit data (files, json) to the backend, usually for subsequent distribution.

* Endpoint schema: ```https://<FQDN>/upload/<payload type>```
* Payload content type (HTTP header): application/json or text/csv
* Payload size restriction: < 6MB
* Authentication: ```Authorization: Bearer <API KEY>``` (see [API Security](api-security.md))
* All-or-nothing: No partial processing (no row-by-row processing)
* Fast-fail: stop processing after first validation exception
* API GW Rate limit (can be periodically adjusted): 100-150 RPS, max concurrency of 10

| API Name | API Group | API Contract | User/Client impact |
| --- | --- | --- | --- |
| Identified Risk Venues | Upload | [risky-venue-upload.md](api-contracts/service-facing/upload/risky-venue-upload.md) | Source of data for the Risky Venue distribution API. |
| Isolation Payment Claim Token | Upload | [isolation-payment-claim-token-upload.md](api-contracts/service-facing/upload/isolation-payment-claim-token-upload.md) | Used by the Isolation Payment Gateway to verify and consume the IPC token for isolation payment claims. |
| Postal District Risk Levels | Upload | [postal-district-risk-level-upload.md](api-contracts/service-facing/upload/postal-district-risk-level-upload.md) | Source of data for the Postal District Risk Levels distribution API. |
| Virology Test Result | Upload | [virology-test-result-upload.md](api-contracts/service-facing/upload/virology-test-result-upload.md) | Source of data for a virology test result from a lab, when a test is booked within the App. |
| Virology Test Result Token Generation | Upload | [virology-test-result-token-upload.md](api-contracts/service-facing/upload/virology-test-result-token-upload.md) | Used by the notification service (BSA for England, NWIS for Wales) to generate a CTA token for sending with a test result notification, when a test is booked outside the App.|

### Circuit Breaker

Circuit breaker APIs delegate the decision for a risk-based action e.g. to advise self-isolation on exposure notification. 

* Endpoint schema: ```https://<FQDN>/circuit-breaker/<risk type specific>```
* HTTP verb: POST
* Payload content-type: application/json
* Payload: related context information (a simple JSON dictionary, i.e. key-value pairs)
* Authentication: ```Authorization: Bearer <API KEY>``` (see [API Security](api-security.md))

After receiving the token the mobile client polls the backend until it receives a resolution result from the backend.

| API Name | API Group | API Contract | User/Client impact |
| --- | --- | --- | --- |
| Exposure Notification Circuit Breaker | Circuit Breaker | [exposure-notification-circuit-breaker.md](api-contracts/mobile-facing/circuit-breaker/exposure-notification-circuit-breaker.md) | Manual circuit breaker to stop exposure notification alerts in mobile clients following a recent contact match with distributed diagnosis keys. |
| Risk Venues Circuit Breaker | Circuit Breaker | [risky-venue-circuit-breaker.md](api-contracts/mobile-facing/circuit-breaker/exposure-notification-circuit-breaker.md) | Manual circuit breaker to stop user notification alerts in mobile clients, following a check-in match on the mobile client with a venue identified as risky. |

### Connector

Connectors integrate with external systems, with data flowing both ways.

The external systems include:
* NearForm Federation API - for exchanging diagnosis keys with other GAEN-compatible systems

| API Name | API Group | API Contract | User/Client impact |
| --- | --- | --- | --- |
| Diagnosis Key Sharing | Connector | [diagnosis-key-federation-connector.md](api-contracts/service-facing/connector/diagnosis-key-federation-connector.md) | Import/Export of federated diagnosis keys shared by nations via Nearform API. |

### Exporter

Exporters integrate with external systems, with data flowing out only.

The external systems include:
* AAE - Advanced Analytics Environment

| API Name | API Group | API Contract | User/Client impact |
| --- | --- | --- | --- |
| Analytics to AAE | Exporter | [analytics-aae-exporter.md](api-contracts/service-facing/exporter/analytics-aae-exporter.md) | Export of mobile analytics data to AAE. |
| Epidemiological Event to AAE | Exporter | [analytics-event-aae-exporter.md](api-contracts/service-facing/exporter/analytics-event-aae-exporter.md) | Export of mobile epidemiological events to AAE. |

