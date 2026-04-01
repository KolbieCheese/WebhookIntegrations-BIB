package rudynakodach.github.io.webhookintegrations.Clans;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

public class LightweightClansWebhookSender {
    private static final String WEBHOOK_SOURCE = "lightweight-clans";

    private final JavaPlugin plugin;
    private final ClansWebhookConfig config;
    private final AsyncTaskScheduler scheduler;
    private final HttpTransport transport;
    private final LightweightClansWebhookSigner signer;

    public LightweightClansWebhookSender(JavaPlugin plugin, ClansWebhookConfig config) {
        this(
                plugin,
                config,
                new BukkitAsyncTaskScheduler(plugin),
                new ApacheHttpTransport(),
                new LightweightClansWebhookSigner()
        );
    }

    LightweightClansWebhookSender(
            JavaPlugin plugin,
            ClansWebhookConfig config,
            AsyncTaskScheduler scheduler,
            HttpTransport transport,
            LightweightClansWebhookSigner signer
    ) {
        this.plugin = plugin;
        this.config = config;
        this.scheduler = scheduler;
        this.transport = transport;
        this.signer = signer;
    }

    public void sendAsync(LightweightClansPayloadMapper.WebhookPayload payload) {
        scheduler.runAsync(() -> deliver(payload, 1));
    }

    private void deliver(LightweightClansPayloadMapper.WebhookPayload payload, int attemptNumber) {
        DeliveryResult result = transport.post(
                config.endpoint(),
                payload.body(),
                buildHeaders(payload),
                config
        );

        if (result.success()) {
            return;
        }

        if (attemptNumber <= config.retryAttempts()) {
            logRetry(payload, result, attemptNumber);
            scheduler.runLaterAsync(() -> deliver(payload, attemptNumber + 1), config.retryDelayTicks());
            return;
        }

        plugin.getLogger().log(
                Level.WARNING,
                "Failed to deliver Lightweight Clans webhook event {0} for clan {1} after {2} attempt(s): {3}",
                new Object[]{
                        payload.eventName(),
                        clanLabel(payload),
                        attemptNumber,
                        result.failureReason()
                }
        );
    }

    private Map<String, String> buildHeaders(LightweightClansPayloadMapper.WebhookPayload payload) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", ContentType.APPLICATION_JSON.getMimeType());
        headers.put("X-Webhook-Source", WEBHOOK_SOURCE);
        headers.put("X-Webhook-Event", payload.eventName());
        headers.put("X-Webhook-Timestamp", payload.occurredAt());
        headers.put("X-Webhook-Signature", signer.sign(config.secret(), payload.occurredAt(), payload.body()));
        return headers;
    }

    private void logRetry(LightweightClansPayloadMapper.WebhookPayload payload, DeliveryResult result, int attemptNumber) {
        Level level = attemptNumber == 1 ? Level.WARNING : Level.FINE;
        int retriesRemaining = config.retryAttempts() - attemptNumber + 1;

        plugin.getLogger().log(
                level,
                "Lightweight Clans webhook delivery failed for event {0} and clan {1}. Scheduling retry in {2} second(s); {3} retry attempt(s) remain. Reason: {4}",
                new Object[]{
                        payload.eventName(),
                        clanLabel(payload),
                        config.retryDelaySeconds(),
                        retriesRemaining,
                        result.failureReason()
                }
        );
    }

    private String clanLabel(LightweightClansPayloadMapper.WebhookPayload payload) {
        return payload.clanName() + " (id=" + payload.clanId() + ")";
    }

    interface AsyncTaskScheduler {
        void runAsync(Runnable task);

        void runLaterAsync(Runnable task, long delayTicks);
    }

    interface HttpTransport {
        DeliveryResult post(String endpoint, String body, Map<String, String> headers, ClansWebhookConfig config);
    }

    record DeliveryResult(boolean success, String failureReason) {
        static DeliveryResult ok() {
            return new DeliveryResult(true, "");
        }

        static DeliveryResult failure(String failureReason) {
            return new DeliveryResult(false, failureReason);
        }
    }

    private static final class BukkitAsyncTaskScheduler implements AsyncTaskScheduler {
        private final JavaPlugin plugin;

        private BukkitAsyncTaskScheduler(JavaPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void runAsync(Runnable task) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        }

        @Override
        public void runLaterAsync(Runnable task, long delayTicks) {
            plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
        }
    }

    private static final class ApacheHttpTransport implements HttpTransport {
        @Override
        public DeliveryResult post(String endpoint, String body, Map<String, String> headers, ClansWebhookConfig config) {
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(Timeout.ofMilliseconds(config.connectTimeoutMillis()))
                    .setResponseTimeout(Timeout.ofMilliseconds(config.readTimeoutMillis()))
                    .build();

            try (CloseableHttpClient httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .build()) {
                HttpPost httpPost = new HttpPost(endpoint);
                httpPost.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));

                for (Map.Entry<String, String> header : headers.entrySet()) {
                    httpPost.setHeader(header.getKey(), header.getValue());
                }

                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    int statusCode = response.getCode();

                    if (statusCode >= 200 && statusCode < 300) {
                        return DeliveryResult.ok();
                    }

                    return DeliveryResult.failure("HTTP " + statusCode);
                }
            } catch (Exception exception) {
                String message = exception.getMessage() == null ? "<no message>" : exception.getMessage();
                return DeliveryResult.failure(exception.getClass().getSimpleName() + ": " + message);
            }
        }
    }
}
