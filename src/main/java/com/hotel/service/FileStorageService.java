package com.hotel.service;

import com.hotel.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "webp");

    // Thu muc goc chua toan bo file upload, VD: uploads/rooms, uploads/combos
    @Value("${app.upload.base-dir}")
    private String baseUploadDir;

    // Luu file anh vao thu muc con (VD: "rooms" hoac "combos"), tra ve duong dan web /uploads/<subDir>/<file>
    public String store(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String originalName = file.getOriginalFilename();
        String extension = getExtension(originalName);

        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException("Chỉ chấp nhận file ảnh định dạng jpg, jpeg, png hoặc webp");
        }

        try {
            Path uploadPath = Paths.get(baseUploadDir, subDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            String storedFileName = UUID.randomUUID() + "." + extension;
            Path targetPath = uploadPath.resolve(storedFileName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            return "/uploads/" + subDir + "/" + storedFileName;
        } catch (IOException ex) {
            throw new BusinessException("Không thể lưu file ảnh, vui lòng thử lại");
        }
    }

    // Xoa file anh vat ly khi phong/combo bi xoa hoac doi anh moi
    public void delete(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        try {
            // imageUrl dang co dang /uploads/<subDir>/<file>
            String relativePath = imageUrl.substring("/uploads/".length());
            Path filePath = Paths.get(baseUploadDir).toAbsolutePath().normalize().resolve(relativePath);
            Files.deleteIfExists(filePath);
        } catch (Exception ignored) {
            // Khong chan luong nghiep vu chinh neu xoa file that bai
        }
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            throw new BusinessException("File không hợp lệ");
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }
}
