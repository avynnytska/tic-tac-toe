package org.example.gateway;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
@RequiredArgsConstructor
public class ProxyController {

    private final GatewayProperties props;
    private final RestClient client = RestClient.builder().build();
    private final HttpClient streamingClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    @RequestMapping(value = "/api/engine/**")
    public ResponseEntity<byte[]> proxyEngine(HttpServletRequest req, @RequestBody(required = false) byte[] body) {
        String suffix = req.getRequestURI().substring("/api/engine".length());
        return forward(props.routes().get("engine") + suffix, req, body);
    }

    @RequestMapping(value = "/api/sessions/**", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> proxySession(HttpServletRequest req, @RequestBody(required = false) byte[] body) {
        String suffix = req.getRequestURI().substring("/api".length());
        return forward(props.routes().get("session") + suffix, req, body);
    }

    /**
     * SSE proxy — uses AsyncContext + HttpServletResponse.flushBuffer() to defeat
     * Tomcat's output buffering. StreamingResponseBody's OutputStream.flush() only
     * flushes Spring's intermediate buffer, not the servlet container's, so events
     * pile up until the connection closes.
     */
    @RequestMapping(value = "/api/sessions/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void proxySessionStream(@PathVariable String sessionId,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        AsyncContext ctx = request.startAsync();
        ctx.setTimeout(0);

        String upstream = props.routes().get("session") + "/sessions/" + sessionId + "/stream";

        streamExecutor.submit(() -> {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(upstream))
                    .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                    .GET()
                    .build();
            try {
                HttpResponse<InputStream> upstreamResp = streamingClient.send(
                        req, HttpResponse.BodyHandlers.ofInputStream());
                ServletOutputStream out = response.getOutputStream();
                try (InputStream in = upstreamResp.body()) {
                    byte[] buf = new byte[128];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        response.flushBuffer();
                    }
                }
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            } finally {
                ctx.complete();
            }
        });
    }

    private ResponseEntity<byte[]> forward(String url, HttpServletRequest req, byte[] body) {
        var spec = switch (req.getMethod()) {
            case "GET" -> client.get().uri(url);
            case "POST" -> body == null
                    ? client.post().uri(url)
                    : client.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body);
            case "PUT" -> body == null
                    ? client.put().uri(url)
                    : client.put().uri(url).contentType(MediaType.APPLICATION_JSON).body(body);
            case "DELETE" -> client.delete().uri(url);
            default -> throw new IllegalArgumentException("Unsupported method: " + req.getMethod());
        };

        return spec.retrieve()
                .onStatus(s -> true, (r, resp) -> {})
                .toEntity(byte[].class);
    }
}
