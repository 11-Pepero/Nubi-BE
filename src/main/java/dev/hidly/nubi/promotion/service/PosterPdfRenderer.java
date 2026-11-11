package dev.hidly.nubi.promotion.service;

import dev.hidly.nubi.common.pdf.PdfFonts;
import lombok.extern.slf4j.Slf4j;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

// 행사 홍보 포스터(A4 한 장): 대표 사진 → 행사 이름 → 일시·장소·정원 → 쉬운 말 홍보문 → 주최.
@Slf4j
@Component
public class PosterPdfRenderer {
    private static final float MARGIN = 48;
    private static final float PHOTO_MAX_HEIGHT = 300;
    private static final Color ACCENT = new Color(0x3B, 0x6E, 0xF5);
    private static final Color MUTED = new Color(0x55, 0x5B, 0x66);

    private final BaseFont baseFont;

    public PosterPdfRenderer(PdfFonts pdfFonts) {
        this.baseFont = pdfFonts.nanumGothic();
    }

    public record Poster(String title, List<String> infoLines, String easy, String hostName, byte[] photo) {
    }

    public byte[] render(Poster poster) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
        PdfWriter.getInstance(document, out);
        document.open();
        float contentWidth = PageSize.A4.getWidth() - MARGIN * 2;

        Image photo = photo(poster.photo());
        if (photo != null) {
            photo.scaleToFit(contentWidth, PHOTO_MAX_HEIGHT);
            photo.setAlignment(Element.ALIGN_CENTER);
            photo.setSpacingAfter(20);
            document.add(photo);
        } else {
            document.add(spacer(60));
        }

        Paragraph title = new Paragraph(poster.title(), new Font(baseFont, 28, Font.NORMAL, ACCENT));
        title.setAlignment(Element.ALIGN_CENTER);
        title.setLeading(36);
        title.setSpacingAfter(16);
        document.add(title);

        Font infoFont = new Font(baseFont, 14, Font.NORMAL, MUTED);
        for (String line : poster.infoLines()) {
            Paragraph info = new Paragraph(line, infoFont);
            info.setAlignment(Element.ALIGN_CENTER);
            info.setSpacingAfter(4);
            document.add(info);
        }
        document.add(spacer(20));

        Font easyFont = new Font(baseFont, 16, Font.NORMAL);
        for (String line : poster.easy().lines().toList()) {
            Paragraph paragraph = line.isBlank() ? spacer(8) : new Paragraph(line, easyFont);
            paragraph.setAlignment(Element.ALIGN_CENTER);
            paragraph.setLeading(24);
            document.add(paragraph);
        }

        if (poster.hostName() != null) {
            document.add(spacer(24));
            Paragraph host = new Paragraph("주최 " + poster.hostName(), new Font(baseFont, 12, Font.NORMAL, MUTED));
            host.setAlignment(Element.ALIGN_CENTER);
            document.add(host);
        }
        document.close();
        return out.toByteArray();
    }

    // JPG·PNG만 넣을 수 있다. WEBP처럼 못 읽는 사진은 빼고 글만 배치한다.
    private static Image photo(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            return Image.getInstance(bytes);
        } catch (Exception e) {
            log.warn("[poster] 대표 사진을 넣지 못해 글만 배치: {}", e.getMessage());
            return null;
        }
    }

    private static Paragraph spacer(float height) {
        Paragraph spacer = new Paragraph(new Chunk(" "));
        spacer.setLeading(height);
        return spacer;
    }
}
