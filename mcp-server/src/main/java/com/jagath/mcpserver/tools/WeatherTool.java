package com.jagath.mcpserver.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Passthrough to Open-Meteo (free, no API key) for current weather by city name.
 * Two calls: geocode the city name to lat/lon, then fetch current conditions.
 */
@Component
public class WeatherTool {

    private final RestClient restClient = RestClient.create();

    @Tool(name = "get_weather",
          description = "Gets current weather (temperature, wind, conditions) for a city name.")
    public String getWeather(@ToolParam(description = "City name, e.g. 'Chennai'") String city) {
        try {
            Map<String, Object> geo = restClient.get()
                    .uri("https://geocoding-api.open-meteo.com/v1/search?name={city}&count=1", city)
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> results = (List<Map<String, Object>>) geo.get("results");
            if (results == null || results.isEmpty()) {
                return "Could not find a location matching \"" + city + "\".";
            }

            Map<String, Object> match = results.get(0);
            double lat = ((Number) match.get("latitude")).doubleValue();
            double lon = ((Number) match.get("longitude")).doubleValue();
            String resolvedName = (String) match.get("name");

            Map<String, Object> weather = restClient.get()
                    .uri("https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}"
                            + "&current=temperature_2m,wind_speed_10m,relative_humidity_2m,weather_code",
                            lat, lon)
                    .retrieve()
                    .body(Map.class);

            Map<String, Object> current = (Map<String, Object>) weather.get("current");

            return String.format(
                    "Weather in %s: %.1f°C, humidity %s%%, wind %.1f km/h, weather code %s",
                    resolvedName,
                    ((Number) current.get("temperature_2m")).doubleValue(),
                    current.get("relative_humidity_2m"),
                    ((Number) current.get("wind_speed_10m")).doubleValue(),
                    current.get("weather_code"));

        } catch (Exception e) {
            return "Weather lookup failed: " + e.getMessage();
        }
    }
}
