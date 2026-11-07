package dev.hidly.nubi.common.file;

import dev.hidly.nubi.common.file.exception.FileUploadFailedException;
import dev.hidly.nubi.common.file.exception.ImageSizeExceededException;
import dev.hidly.nubi.common.file.exception.ImageTypeNotAllowedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {
    public static final String URL_PREFIX = "/uploads/";
    private static final long MAX_SIZE = 5 * 1024 * 1024;
    // ponytail: Content-Type 헤더만 확인, 위조된 파일까지 막으려면 매직 바이트 검사 추가
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final Path root;

    public FileStorageService(@Value("${file.upload-dir}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public Path getRoot() {
        return root;
    }

    public String save(MultipartFile file) {
        String extension = file == null || file.isEmpty() ? null : EXTENSIONS.get(file.getContentType());
        if (extension == null) {
            throw new ImageTypeNotAllowedException();
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ImageSizeExceededException();
        }

        String fileName = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(root);
            file.transferTo(root.resolve(fileName));
        } catch (IOException e) {
            log.error("[save] 파일 저장 실패: {}", fileName, e);
            throw new FileUploadFailedException();
        }
        return URL_PREFIX + fileName;
    }

    public void delete(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return;
        }
        Path target = root.resolve(url.substring(URL_PREFIX.length())).normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("[delete] 파일 삭제 실패: {}", target, e);
        }
    }
}
