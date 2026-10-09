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

    // Thu muc anh ngoai project (tuy chon) - neu co, anh trong do duoc uu tien hon anh di kem project
    @Value("${app.page-images.dir:}")
    private String pageImagesDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = Paths.get(baseUploadDir).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + "/");

        // Anh nen tung trang: tim trong thu muc ngoai (neu cau hinh) truoc, khong co thi lay anh
        // di kem project (src/main/resources/static/page-images) - clone ve la chay duoc, khong can chep anh
        var pageImages = registry.addResourceHandler("/page-images/**");
        if (pageImagesDir != null && !pageImagesDir.isBlank()) {
            String pageImagesPath = Paths.get(pageImagesDir).toAbsolutePath().normalize().toString();
            pageImages.addResourceLocations("file:" + pageImagesPath + "/");
        }
        pageImages.addResourceLocations("classpath:/static/page-images/");
    }
}
