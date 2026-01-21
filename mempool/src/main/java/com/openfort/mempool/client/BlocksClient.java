package com.openfort.mempool.client;

import com.openfort.mempool.model.dto.PriceResponse;
import com.openfort.mempool.model.dto.SimulateBlockRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
public class BlocksClient {

    // Reactive HTTP client used to communicate with the Blocks service
    private final WebClient client;

    /**
     * Creates a BlocksClient configured with the base URL of the Blocks service.
     *
     * The base URL can be configured via the `blocks.base-url` property.
     * Defaults to `http://blocks:8080` if not provided.
     *
     * @param baseUrl base URL of the Blocks service
     */
    public BlocksClient(
            @Value("${blocks.base-url:http://blocks:8080}") String baseUrl
    ) {
        this.client = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Retrieves the current network gas price and fee information
     * from the Blocks service.
     *
     * This method performs a synchronous (blocking) HTTP GET request.
     *
     * @return the current pricing information
     */
    public PriceResponse getCurrentPrice() {
        return client.get()
                .uri("/getCurrentPrice")
                .retrieve()
                .bodyToMono(PriceResponse.class)
                .block();
    }

    /**
     * Sends a batch of transactions to the Blocks service for simulation.
     *
     * This method performs a synchronous (blocking) HTTP POST request
     * and does not return a response body.
     *
     * Any HTTP or network error will result in a runtime exception.
     *
     * @param req the block simulation request containing the transaction batch
     */
    public void simulateBlock(SimulateBlockRequest req) {
        client.post()
                .uri("/simulateBlock")
                .bodyValue(req)
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
