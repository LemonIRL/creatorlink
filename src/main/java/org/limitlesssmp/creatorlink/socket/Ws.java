package org.limitlesssmp.creatorlink.socket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Small wrapper over java.net.http's WebSocket that reassembles partial text
 * frames and funnels lifecycle events into simple callbacks.
 */
public final class Ws {
    public static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private Ws() {}

    public static CompletableFuture<WebSocket> connect(String url, Map<String, String> headers,
                                                       Consumer<String> onText,
                                                       Consumer<String> onClose,
                                                       Consumer<Throwable> onError) {
        return connect(url, headers, null, onText, onClose, onError);
    }

    /**
     * onOpen receives the WebSocket before any message is delivered — assign
     * your handle there, not from the returned future, or replies sent from
     * the first onText can race the assignment and be dropped.
     */
    public static CompletableFuture<WebSocket> connect(String url, Map<String, String> headers,
                                                       Consumer<WebSocket> onOpen,
                                                       Consumer<String> onText,
                                                       Consumer<String> onClose,
                                                       Consumer<Throwable> onError) {
        WebSocket.Builder b = HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(15));
        if (headers != null) headers.forEach(b::header);
        return b.buildAsync(URI.create(url), new WebSocket.Listener() {
            private final StringBuilder buffer = new StringBuilder();

            @Override
            public void onOpen(WebSocket ws) {
                if (onOpen != null) onOpen.accept(ws);
                ws.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                buffer.append(data);
                if (last) {
                    String message = buffer.toString();
                    buffer.setLength(0);
                    try {
                        onText.accept(message);
                    } catch (Exception e) {
                        onError.accept(e);
                    }
                }
                ws.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                onClose.accept(statusCode + " " + reason);
                return null;
            }

            @Override
            public void onError(WebSocket ws, Throwable error) {
                onError.accept(error);
            }
        });
    }
}
