package com.shibajide.policyintelligence.embedding.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class OpenAiEmbeddingGeneratorTest {

    @Test
    void embedsTextWithConfiguredOpenAiModel() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> requestMethod = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/embeddings", exchange -> {
            requestMethod.set(exchange.getRequestMethod());
            authorization.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            contentType.set(exchange.getRequestHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "data": [
                        {
                          "embedding": [0.25, -0.5, 1.0]
                        }
                      ]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        var generator = new OpenAiEmbeddingGenerator(
                RestClient.builder(),
                "http://localhost:" + server.getAddress().getPort() + "/v1",
                "test-key",
                "text-embedding-3-small",
                1536
        );

        try {
            var embedding = generator.embed("policy access");

            assertThat(requestMethod.get()).isEqualTo("POST");
            assertThat(authorization.get()).isEqualTo("Bearer test-key");
            assertThat(contentType.get()).contains(MediaType.APPLICATION_JSON_VALUE);
            assertThat(requestBody.get()).contains("\"model\":\"text-embedding-3-small\"");
            assertThat(requestBody.get()).contains("\"input\":\"policy access\"");
            assertThat(embedding.model()).isEqualTo("text-embedding-3-small");
            assertThat(embedding.dimension()).isEqualTo(3);
            assertThat(embedding.values()).containsExactly(0.25f, -0.5f, 1.0f);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void requiresApiKeyWhenOpenAiProviderIsSelected() {
        var generator = new OpenAiEmbeddingGenerator(
                RestClient.builder(),
                "https://api.openai.test/v1",
                "",
                "text-embedding-3-small",
                1536
        );

        assertThatThrownBy(() -> generator.embed("policy access"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }
}
