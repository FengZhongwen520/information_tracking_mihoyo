package com.aitracking.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    private static final Set<String> EXACT_HOSTS = Set.of("static.wikia.nocookie.net");
    private static final Set<String> HOST_SUFFIXES = Set.of(".hoyoverse.com", ".hoyolab.com");
    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif", "image/avif");
    private static final long MAX_BYTES = 12L * 1024 * 1024;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    private final Path cacheDir;
    private final long maxCacheBytes;
    private final int maxAgeDays;
    private final AtomicLong cacheBytes = new AtomicLong(0);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public ImageController(
            @Value("${app.images.cache-dir:./data/images}") String cacheDir,
            @Value("${app.images.max-cache-bytes:2147483648}") long maxCacheBytes,
            @Value("${app.images.max-age-days:30}") int maxAgeDays) {
        this.cacheDir = Path.of(cacheDir);
        this.maxCacheBytes = Math.max(64L * 1024 * 1024, maxCacheBytes);
        this.maxAgeDays = Math.max(1, maxAgeDays);
    }

    @PostConstruct
    void initializeCacheSize() {
        cacheBytes.set(scanCacheSize());
    }

    @GetMapping
    public ResponseEntity<byte[]> image(@RequestParam("url") String rawUrl) {
        URI uri = validate(rawUrl);
        String key = hash(uri.toString());
        CachedImage cached = readCache(key);
        if (cached == null) {
            cached = fetch(uri);
            writeCache(key, cached);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(cached.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(cached.body());
    }

    private URI validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank() || rawUrl.length() > 2000) {
            throw ApiException.badRequest("图片地址无效");
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String host = uri.getHost();
            boolean allowed = host != null
                    && (EXACT_HOSTS.contains(host.toLowerCase(Locale.ROOT))
                            || HOST_SUFFIXES.stream().anyMatch(host.toLowerCase(Locale.ROOT)::endsWith));
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !allowed) {
                throw ApiException.badRequest("图片地址不受支持");
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("图片地址无效");
        }
    }

    private CachedImage fetch(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", "https://www.fandom.com/")
                .header("Sec-CH-UA", "\"Chromium\";v=\"130\", \"Google Chrome\";v=\"130\", \"Not?A_Brand\";v=\"99\"")
                .header("Sec-CH-UA-Mobile", "?0")
                .header("Sec-CH-UA-Platform", "\"Windows\"")
                .header("Sec-Fetch-Dest", "image")
                .header("Sec-Fetch-Mode", "no-cors")
                .header("Sec-Fetch-Site", "cross-site")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String contentType = response.headers().firstValue("content-type").orElse("")
                    .split(";")[0].trim().toLowerCase(Locale.ROOT);
            byte[] body = response.body();
            if (response.statusCode() != 200
                    || !IMAGE_TYPES.contains(contentType)
                    || body == null
                    || body.length == 0
                    || body.length > MAX_BYTES) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "image_unavailable", "图片暂时无法加载");
            }
            return new CachedImage(body, contentType);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "image_unavailable", "图片暂时无法加载");
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "image_unavailable", "图片暂时无法加载");
        }
    }

    private CachedImage readCache(String key) {
        Path data = cacheDir.resolve(key + ".bin");
        Path type = cacheDir.resolve(key + ".type");
        try {
            if (!Files.isRegularFile(data) || !Files.isRegularFile(type)) {
                return null;
            }
            String contentType = Files.readString(type, StandardCharsets.UTF_8).trim();
            if (!IMAGE_TYPES.contains(contentType)) {
                return null;
            }
            return new CachedImage(Files.readAllBytes(data), contentType);
        } catch (IOException ex) {
            return null;
        }
    }

    private void writeCache(String key, CachedImage image) {
        Path data = cacheDir.resolve(key + ".bin");
        Path type = cacheDir.resolve(key + ".type");
        Path temp = null;
        try {
            Files.createDirectories(cacheDir);
            temp = Files.createTempFile(cacheDir, key, ".tmp");
            Files.write(temp, image.body(), StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temp, data, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, data, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(type, image.contentType(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            cacheBytes.addAndGet(image.body().length);
            pruneIfNeeded();
        } catch (IOException ignored) {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException cleanupFailure) {
                    // Best-effort cleanup only.
                }
            }
        }
    }

    @Scheduled(cron = "0 40 4 * * *", zone = "Asia/Shanghai")
    public void evictOldCache() {
        Instant cutoff = Instant.now().minusSeconds(maxAgeDays * 86400L);
        try (var entries = Files.list(cacheDir)) {
            entries.filter(path -> path.getFileName().toString().endsWith(".bin"))
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
                        } catch (IOException ex) {
                            return false;
                        }
                    })
                    .toList()
                    .forEach(path -> removeCacheEntry(path.getFileName().toString()
                            .replaceFirst("\\.bin$", "")));
        } catch (IOException ignored) {
            // A missing cache directory simply means there is nothing to clean.
        }
    }

    private synchronized void pruneIfNeeded() {
        if (cacheBytes.get() <= maxCacheBytes) {
            return;
        }
        long target = (long) (maxCacheBytes * 0.8);
        List<Path> entries = new ArrayList<>();
        try (var stream = Files.list(cacheDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".bin"))
                    .forEach(entries::add);
        } catch (IOException ex) {
            return;
        }
        entries.sort(Comparator.comparingLong(path -> {
            try {
                return Files.getLastModifiedTime(path).toMillis();
            } catch (IOException ex) {
                return Long.MAX_VALUE;
            }
        }));
        for (Path path : entries) {
            if (cacheBytes.get() <= target) {
                break;
            }
            removeCacheEntry(path.getFileName().toString().replaceFirst("\\.bin$", ""));
        }
    }

    private synchronized void removeCacheEntry(String key) {
        Path data = cacheDir.resolve(key + ".bin");
        try {
            long size = Files.exists(data) ? Files.size(data) : 0;
            Files.deleteIfExists(data);
            Files.deleteIfExists(cacheDir.resolve(key + ".type"));
            if (size > 0) {
                cacheBytes.updateAndGet(current -> Math.max(0, current - size));
            }
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }

    private long scanCacheSize() {
        try (var entries = Files.list(cacheDir)) {
            return entries.filter(path -> path.getFileName().toString().endsWith(".bin"))
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException ex) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException ex) {
            return 0;
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record CachedImage(byte[] body, String contentType) {
    }
}
