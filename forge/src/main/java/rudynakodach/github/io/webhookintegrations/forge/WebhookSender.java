package rudynakodach.github.io.webhookintegrations.forge;

import java.net.URI;
import java.util.Map;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** A bounded FIFO keeps HTTP off the server thread and limits memory during outages. */
public final class WebhookSender implements AutoCloseable {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(256), runnable -> {
                Thread thread = new Thread(runnable, "webhookintegrations-sender");
                thread.setDaemon(true);
                return thread;
            });
    private final Consumer<String> warning;

    public WebhookSender(Consumer<String> warning) { this.warning = warning; }

    public boolean send(String url, String payload) {
        return send(url, payload, Map.of());
    }

    public boolean send(String url, String payload, Map<String, String> headers) {
        if (url == null || url.isBlank()) return false;
        var safeHeaders = Map.copyOf(headers);
        WebhookConfig.validateUrl(url);
        try {
            worker.execute(() -> deliver(url, payload, safeHeaders));
            return true;
        } catch (RejectedExecutionException e) {
            warning.accept("Webhook queue is full or shutting down; message dropped.");
            return false;
        }
    }

    private void deliver(String url, String payload, Map<String, String> headers) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                var builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(payload));
                headers.forEach(builder::header);
                var request = builder.build();
                var response = client.send(request, HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();
                if (status >= 200 && status < 300) return;
                if (status != 429 && status != 408 && status < 500) {
                    warning.accept("Webhook delivery rejected (HTTP " + status + "). Check the configured endpoint.");
                    return;
                }
                if (attempt < 2) {
                    long seconds = attempt + 1;
                    try { seconds = Math.max(seconds, Math.min(30, (long) Math.ceil(Double.parseDouble(response.headers().firstValue("Retry-After").orElse("0"))))); }
                    catch (NumberFormatException ignored) {}
                    Thread.sleep(seconds * 1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // Exceptions can include secret webhook URLs; only report the exception type.
                if (attempt == 2) warning.accept("Webhook delivery failed: " + e.getClass().getSimpleName());
            }
        }
        warning.accept("Webhook delivery exhausted its retries.");
    }

    @Override public void close() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(15, TimeUnit.SECONDS)) {
                worker.shutdownNow();
                warning.accept("Webhook shutdown deadline reached; pending deliveries cancelled.");
            }
        } catch (InterruptedException e) { worker.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}
