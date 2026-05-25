package org.example.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxyControllerIT {

    private static HttpServer engineStub;
    private static HttpServer sessionStub;

    @LocalServerPort
    int gatewayPort;

    @BeforeAll
    static void startStubs() throws IOException {
        engineStub = HttpServer.create(new InetSocketAddress(0), 0);
        engineStub.createContext("/games", exchange -> {
            byte[] response = "{\"engine\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        engineStub.start();

        sessionStub = HttpServer.create(new InetSocketAddress(0), 0);
        sessionStub.createContext("/sessions", exchange -> {
            byte[] response = "{\"session\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        sessionStub.start();
    }

    @AfterAll
    static void stopStubs() {
        if (engineStub != null) engineStub.stop(0);
        if (sessionStub != null) sessionStub.stop(0);
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry r) {
        r.add("gateway.routes.engine", () -> "http://localhost:" + engineStub.getAddress().getPort());
        r.add("gateway.routes.session", () -> "http://localhost:" + sessionStub.getAddress().getPort());
    }

    @Test
    void routesEngineCalls() {
        RestClient client = RestClient.create("http://localhost:" + gatewayPort);
        String body = client.get().uri("/api/engine/games").retrieve().body(String.class);
        assertNotNull(body);
        assertTrue(body.contains("\"engine\":\"ok\""), "Expected engine stub response, got: " + body);
    }

    @Test
    void routesSessionCalls() {
        RestClient client = RestClient.create("http://localhost:" + gatewayPort);
        String body = client.get().uri("/api/sessions").retrieve().body(String.class);
        assertNotNull(body);
        assertTrue(body.contains("\"session\":\"ok\""), "Expected session stub response, got: " + body);
    }
}
