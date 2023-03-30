package io.av360.maverick.graph.feature.applications.client;

import io.av360.maverick.graph.feature.applications.api.dto.Requests;
import io.av360.maverick.graph.feature.applications.domain.model.ApplicationFlags;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

public class ApplicationsTestClient {

    private final WebTestClient webClient;

    public ApplicationsTestClient(WebTestClient webClient) {
        this.webClient = webClient;
    }


    public WebTestClient.ResponseSpec createApplication(String label, ApplicationFlags flags) {

        Requests.RegisterApplicationRequest req = new Requests.RegisterApplicationRequest(label, flags);

        return webClient.post()
                .uri("/api/applications")
                .body(BodyInserters.fromValue(req))
                .exchange();

    }

    public WebTestClient.ResponseSpec getApplication(String applicationKey) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/applications/{id}")
                        .build(applicationKey)
                )
                .exchange();
    }
    public WebTestClient.ResponseSpec listApplications() {

        return webClient.get()
                .uri("/api/applications")
                .exchange();
    }

    public WebTestClient.ResponseSpec listSubscriptions(String applicationKey) {

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/applications/{id}/subscriptions")
                        .build(applicationKey)
                )
                .exchange();
    }


    public WebTestClient.ResponseSpec createSubscription(String subscriptionLabel, String applicationKey) {
        Requests.CreateApiKeyRequest request = new Requests.CreateApiKeyRequest(subscriptionLabel);

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/applications/{id}/subscriptions")
                        .build(applicationKey)
                )
                .bodyValue(request)
                .exchange();


        //        .expectStatus().isCreated()
        //        .expectBody(Responses.ApiKeyResponse.class);
    }



}
