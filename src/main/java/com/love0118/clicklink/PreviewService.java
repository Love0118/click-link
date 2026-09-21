package com.love0118.clicklink;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import com.google.gson.JsonParser;

import java.net.InetAddress;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;

final class PreviewService implements AutoCloseable {
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), runnable -> {
                Thread thread = new Thread(runnable, "ClickLink-preview");
                thread.setDaemon(true);
                return thread;
            });
    private final Map<String, Entry> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final int timeout;
    private final int maxEntries;
    private final long ttl;

    PreviewService(int timeout, int maxEntries, int cacheMinutes) {
        this.timeout = timeout;
        this.maxEntries = maxEntries;
        ttl = TimeUnit.MINUTES.toNanos(cacheMinutes);
    }

    synchronized CompletableFuture<String> title(String url) {
        Entry existing = cache.get(url);
        if (existing != null && (System.nanoTime() < existing.expires() || !existing.value().isDone())) {
            return existing.value();
        }
        CompletableFuture<String> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try { future.complete(fetch(url)); }
                catch (Exception ignored) { future.complete(null); }
            });
        } catch (RejectedExecutionException ignored) {
            return CompletableFuture.completedFuture(null);
        }
        cache.put(url, new Entry(future, System.nanoTime() + ttl));
        while (cache.size() > maxEntries) cache.remove(cache.keySet().iterator().next());
        return future;
    }

    private String fetch(String url) throws Exception {
        URI uri = URI.create(url);
        String host = uri.getHost();
        if (host != null && (host.equalsIgnoreCase("youtu.be") || host.equalsIgnoreCase("youtube.com")
                || host.toLowerCase(java.util.Locale.ROOT).endsWith(".youtube.com"))) {
            String title = youtubeTitle(uri);
            if (title != null) return title;
        }
        for (int redirects = 0; redirects <= 3; redirects++) {
            validate(uri);
            var response = Jsoup.connect(uri.toASCIIString())
                    .userAgent("Mozilla/5.0 (compatible; ClickLink/1.0)")
                    .timeout(timeout).maxBodySize(262144).followRedirects(false)
                    .ignoreHttpErrors(true).execute();
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                String location = response.header("Location");
                if (location == null) return null;
                uri = uri.resolve(location);
                continue;
            }
            if (response.statusCode() != 200) return null;
            String type = response.contentType();
            if (type == null || !(type.startsWith("text/html") || type.startsWith("application/xhtml+xml"))) return null;
            return extractTitle(response.parse());
        }
        return null;
    }

    private String youtubeTitle(URI original) throws Exception {
        validate(original);
        URI endpoint = URI.create("https://www.youtube.com/oembed?format=json&url="
                + java.net.URLEncoder.encode(original.toString(), java.nio.charset.StandardCharsets.UTF_8));
        validate(endpoint);
        try {
            var response = Jsoup.connect(endpoint.toString()).timeout(timeout).maxBodySize(32768)
                    .followRedirects(false).ignoreContentType(true).ignoreHttpErrors(true).execute();
            if (response.statusCode() != 200) return null;
            var json = JsonParser.parseString(response.body()).getAsJsonObject();
            return json.has("title") ? clean(json.get("title").getAsString()) : null;
        } catch (java.io.IOException | RuntimeException ignored) {
            return null;
        }
    }

    static void validate(URI uri) throws Exception {
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443)) {
            throw new IllegalArgumentException("Not a public HTTP URL");
        }
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (!publicAddress(address)) throw new IllegalArgumentException("Non-public address");
        }
    }

    static boolean publicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int a = bytes[0] & 255, b = bytes[1] & 255;
            return a != 0 && a < 224 && !(a == 100 && b >= 64 && b <= 127)
                    && !(a == 198 && (b == 18 || b == 19));
        }
        // Only global unicast IPv6; reject unique-local and transition mechanisms.
        return (bytes[0] & 0xe0) == 0x20 && !(bytes[0] == 0x20 && bytes[1] == 0x02)
                && !(bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0 && bytes[3] == 0);
    }

    static String extractTitle(Document document) {
        for (String selector : new String[]{"meta[property=og:title]", "meta[name=twitter:title]", "meta[property=twitter:title]"}) {
            var meta = document.selectFirst(selector);
            String title = meta == null ? null : clean(meta.attr("content"));
            if (title != null) return title;
        }
        return clean(document.title());
    }

    private static String clean(String title) {
        String result = title.replaceAll("[\\p{Cc}\\p{Cf}§]", " ").replaceAll("\\s+", " ").strip();
        if (result.isBlank()) return null;
        int length = result.codePointCount(0, result.length());
        return length > 160 ? result.substring(0, result.offsetByCodePoints(0, 157)) + "..." : result;
    }

    @Override
    public synchronized void close() {
        executor.shutdownNow();
        cache.values().forEach(entry -> entry.value().complete(null));
        cache.clear();
    }

    private record Entry(CompletableFuture<String> value, long expires) {}
}
