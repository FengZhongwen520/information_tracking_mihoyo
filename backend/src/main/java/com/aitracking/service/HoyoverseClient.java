package com.aitracking.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Component;

import com.aitracking.config.HoyoverseProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

@Component
public class HoyoverseClient {

    private final HoyoverseProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HoyoverseClient(HoyoverseProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper.copy()
                .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
        Duration timeout = Duration.ofSeconds(Math.max(5, properties.getTimeoutSeconds()));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public CalendarPayload calendar(String gameSlug) {
        String locale = properties.getLocale() == null || properties.getLocale().isBlank()
                ? "zh-cn"
                : properties.getLocale().trim();
        String query = URLEncoder.encode(locale, StandardCharsets.UTF_8);
        return get("/" + gameSlug + "/calendar?lang=" + query, CalendarPayload.class);
    }

    public CodesPayload codes(String gameSlug) {
        return get("/" + gameSlug + "/codes", CodesPayload.class);
    }

    private <T> T get(String path, Class<T> type) {
        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        URI uri = URI.create(baseUrl + path);
        Duration timeout = Duration.ofSeconds(Math.max(5, properties.getTimeoutSeconds()));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", "ai-tracking-hoyoverse-sync/1.0")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "HoYoverse API 返回 HTTP " + response.statusCode() + "（" + uri + "）");
            }
            return objectMapper.readValue(response.body(), type);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("拉取 HoYoverse 数据被中断", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("拉取 HoYoverse 数据失败：" + ex.getMessage(), ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CalendarPayload(
            List<Event> events,
            List<Banner> banners,
            List<Challenge> challenges) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(
            Long id,
            String name,
            String description,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("type_name") String typeName,
            @JsonProperty("start_time") Long startTime,
            @JsonProperty("end_time") Long endTime,
            List<Reward> rewards,
            @JsonProperty("special_reward") Reward specialReward,
            Integer polychrome,
            String state) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Challenge(
            Long id,
            String name,
            @JsonProperty("type_name") String typeName,
            @JsonProperty("start_time") Long startTime,
            @JsonProperty("end_time") Long endTime,
            List<Reward> rewards,
            @JsonProperty("special_reward") Reward specialReward) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Banner(
            Long id,
            String name,
            String version,
            @JsonProperty("banner_type") String bannerType,
            List<BannerItem> characters,
            @JsonProperty("light_cones") List<BannerItem> lightCones,
            List<BannerItem> weapons,
            List<BannerItem> agents,
            @JsonProperty("w_engines") List<BannerItem> wEngines,
            @JsonProperty("start_time") Long startTime,
            @JsonProperty("end_time") Long endTime) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BannerItem(
            Long id,
            String name,
            String icon,
            String rarity,
            String element,
            String path,
            String profession,
            @JsonProperty("full_name") String fullName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reward(
            Long id,
            String name,
            String icon,
            String rarity,
            Integer amount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CodesPayload(
            List<CodePayload> active,
            List<CodePayload> inactive) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CodePayload(
            String code,
            List<String> rewards) {
    }
}
