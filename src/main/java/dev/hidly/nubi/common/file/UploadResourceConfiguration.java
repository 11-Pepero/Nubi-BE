package dev.hidly.nubi.common.file;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class UploadResourceConfiguration implements WebMvcConfigurer {
    private final FileStorageService fileStorageService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(FileStorageService.URL_PREFIX + "**")
                .addResourceLocations(fileStorageService.getRoot().toUri().toString());
    }
}
