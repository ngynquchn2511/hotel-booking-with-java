package com.hotel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.base-dir}")
    private String baseUploadDir;

    @Value("${app.page-images.dir}")
    private String pageImagesDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = Paths.get(baseUploadDir).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + "/");

        // Thu muc anh nen tung trang, nam ngoai project (VD: D:/CongNgheJava/img)
        String pageImagesPath = Paths.get(pageImagesDir).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/page-images/**")
                .addResourceLocations("file:" + pageImagesPath + "/");
    }
}
