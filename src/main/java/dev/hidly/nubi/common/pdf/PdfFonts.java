package dev.hidly.nubi.common.pdf;

import org.openpdf.text.pdf.BaseFont;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

// PDF에 넣는 한글 글꼴. 나눔고딕(SIL OFL, resources/fonts)을 한 번만 읽어 신청 서류·포스터 PDF가 같이 쓴다.
@Component
public class PdfFonts {
    private final BaseFont nanumGothic;
    private final BaseFont nanumGothicBold;
    private final BaseFont nanumGothicExtraBold;

    public PdfFonts() {
        this.nanumGothic = load("NanumGothic-Regular.ttf");
        this.nanumGothicBold = load("NanumGothic-Bold.ttf");
        this.nanumGothicExtraBold = load("NanumGothic-ExtraBold.ttf");
    }

    private static BaseFont load(String fileName) {
        String path = "fonts/" + fileName;
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return BaseFont.createFont(fileName, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        } catch (IOException e) {
            throw new IllegalStateException("PDF 글꼴을 읽지 못했습니다: " + path, e);
        }
    }

    public BaseFont nanumGothic() {
        return nanumGothic;
    }

    public BaseFont nanumGothicBold() {
        return nanumGothicBold;
    }

    public BaseFont nanumGothicExtraBold() {
        return nanumGothicExtraBold;
    }
}
