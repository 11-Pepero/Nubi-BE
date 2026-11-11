package dev.hidly.nubi.common.pdf;

import org.openpdf.text.pdf.BaseFont;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

// PDF에 넣는 한글 글꼴. 나눔고딕(SIL OFL, resources/fonts)을 한 번만 읽어 신청 서류·포스터 PDF가 같이 쓴다.
@Component
public class PdfFonts {
    private static final String FONT_PATH = "fonts/NanumGothic-Regular.ttf";

    private final BaseFont nanumGothic;

    public PdfFonts() {
        try (InputStream in = new ClassPathResource(FONT_PATH).getInputStream()) {
            this.nanumGothic = BaseFont.createFont("NanumGothic-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    true, in.readAllBytes(), null);
        } catch (IOException e) {
            throw new IllegalStateException("PDF 글꼴을 읽지 못했습니다: " + FONT_PATH, e);
        }
    }

    public BaseFont nanumGothic() {
        return nanumGothic;
    }
}
