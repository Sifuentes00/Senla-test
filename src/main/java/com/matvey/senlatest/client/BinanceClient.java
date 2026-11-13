package com.matvey.senlatest.client;

import com.matvey.senlatest.exception.BinanceApiException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class BinanceClient {

    private final RestClient restClient;

    public BinanceClient(RestClient binanceRestClient) {
        this.restClient = binanceRestClient;
    }

    public BinanceAvgPriceResponse fetchAvgPrice(String symbol) {
        try {
            BinanceAvgPriceResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v3/avgPrice")
                            .queryParam("symbol", symbol)
                            .build())
                    .retrieve()
                    .body(BinanceAvgPriceResponse.class);

            if (response == null) {
                throw new BinanceApiException("Empty response from Binance for symbol " + symbol);
            }
            return response;
        } catch (RestClientException e) {
            throw new BinanceApiException("Failed to fetch avgPrice for symbol " + symbol, e);
        }
    }
}
