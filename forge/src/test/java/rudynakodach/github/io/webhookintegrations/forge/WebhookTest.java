package rudynakodach.github.io.webhookintegrations.forge;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class WebhookTest {
    @TempDir Path directory;

    @Test void rendersNestedJsonWithoutInjectionOrRecursiveExpansion() {
        JsonObject template = JsonParser.parseString("{\"embeds\":[{\"description\":\"$player$: $message$\",\"color\":123}],\"allowed_mentions\":{\"parse\":[\"everyone\"]}}").getAsJsonObject();
        String userText = "quotes: \" \\ \n @everyone $player$";
        var result = JsonParser.parseString(MessageRenderer.payload(template, Map.of("player", "Alex", "message", userText))).getAsJsonObject();
        var embed = result.getAsJsonArray("embeds").get(0).getAsJsonObject();
        assertEquals("Alex: " + userText, embed.get("description").getAsString());
        assertEquals(123, embed.get("color").getAsInt());
        assertTrue(result.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").isEmpty());
        assertEquals("$player$: $message$", template.getAsJsonArray("embeds").get(0).getAsJsonObject().get("description").getAsString());
    }

    @Test void configRoundTripAndInvalidFilePreservation() throws Exception {
        Path file = directory.resolve("config.json");
        var config = WebhookConfig.load(file);
        assertTrue(config.enabled);
        assertFalse(config.events.get("onPlayerCountChange").announce);
        config.webhooks.put("extra", "https://example.com/webhook");
        config.events.get("onPlayerChat").announce = false;
        config.save(file);
        var loaded = WebhookConfig.load(file);
        assertEquals(config.webhooks, loaded.webhooks);
        assertFalse(loaded.events.get("onPlayerChat").announce);
        Files.writeString(file, "{\"webhooks\":null}");
        assertThrows(java.io.IOException.class, () -> WebhookConfig.load(file));
        assertEquals("{\"webhooks\":null}", Files.readString(file));
    }

    @Test void validatesEndpointsAndCensorsPlayerText() {
        assertThrows(IllegalArgumentException.class, () -> WebhookConfig.validateUrl("file:///etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> WebhookConfig.validateUrl("https://user:secret@example.com"));
        var config = new WebhookConfig();
        config.removeColorCoding = true;
        assertEquals("everyone \\*hi\\*", config.clean("@everyone §a*hi*", true));
    }

    @Test void sendsUtf8JsonRetriesTransientFailureAndDrainsOnClose() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        List<String> bodies = new CopyOnWriteArrayList<>();
        List<String> types = new CopyOnWriteArrayList<>();
        server.createContext("/hook", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            types.add(exchange.getRequestHeaders().getFirst("Content-Type"));
            exchange.sendResponseHeaders(requests.incrementAndGet() == 1 ? 503 : 204, -1);
            exchange.close();
        });
        server.start();
        List<String> warnings = new CopyOnWriteArrayList<>();
        try {
            try (var sender = new WebhookSender(warnings::add)) {
                assertTrue(sender.send("http://127.0.0.1:" + server.getAddress().getPort() + "/hook", "{\"content\":\"héllo\"}"));
            }
            assertEquals(2, requests.get());
            assertEquals(List.of("{\"content\":\"héllo\"}", "{\"content\":\"héllo\"}"), bodies);
            assertTrue(types.stream().allMatch(type -> type.startsWith("application/json")));
            assertTrue(warnings.isEmpty());
        } finally { server.stop(0); }
    }

    @Test void permanentHttpErrorsAreNotRetriedAndClosedSenderRejectsWork() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/hook", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
        });
        server.start();
        List<String> warnings = new CopyOnWriteArrayList<>();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
        try {
            var sender = new WebhookSender(warnings::add);
            assertTrue(sender.send(url, "{}"));
            sender.close();
            assertEquals(1, requests.get());
            assertTrue(warnings.get(0).contains("HTTP 400"));
            assertFalse(sender.send(url, "{}"));
        } finally { server.stop(0); }
    }
}
