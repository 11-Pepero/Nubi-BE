package dev.hidly.nubi.collect;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(CollectProperties.class)
public class CollectConfiguration {
    public static final String USER_AGENT = "NubiCollector/1.0 (+https://github.com/11-Pepero/Nubi-BE)";

    @Bean
    public RestClient collectRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
    }
}
