package dev.hidly.nubi.common.file;

import dev.hidly.nubi.common.file.exception.ImageSizeExceededException;
import dev.hidly.nubi.common.file.exception.ImageTypeNotAllowedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void 이미지를_저장하고_지운다() {
        FileStorageService service = new FileStorageService(tempDir.resolve("uploads").toString());

        String url = service.save(new MockMultipartFile("image", "a.png", "image/png", new byte[]{1, 2, 3}));

        Path saved = service.getRoot().resolve(url.substring(FileStorageService.URL_PREFIX.length()));
        assertThat(url).startsWith("/uploads/").endsWith(".png");
        assertThat(saved).exists();

        service.delete(url);
        assertThat(saved).doesNotExist();
    }

    @Test
    void 이미지가_아니거나_비었거나_너무_크면_거절한다() {
        FileStorageService service = new FileStorageService(tempDir.toString());

        assertThatThrownBy(() -> service.save(new MockMultipartFile("image", "a.pdf", "application/pdf", new byte[]{1})))
                .isInstanceOf(ImageTypeNotAllowedException.class);
        assertThatThrownBy(() -> service.save(new MockMultipartFile("image", "a.png", "image/png", new byte[0])))
                .isInstanceOf(ImageTypeNotAllowedException.class);
        assertThatThrownBy(() -> service.save(new MockMultipartFile("image", "a.png", "image/png", new byte[5 * 1024 * 1024 + 1])))
                .isInstanceOf(ImageSizeExceededException.class);
    }

    @Test
    void 업로드_폴더_밖의_파일은_지우지_않는다() throws Exception {
        Path outside = Files.createFile(tempDir.resolve("secret.txt"));
        FileStorageService service = new FileStorageService(tempDir.resolve("uploads").toString());

        service.delete("/uploads/../secret.txt");

        assertThat(outside).exists();
    }
}
