package dev.hidly.nubi.document.service;

import dev.hidly.nubi.common.pdf.PdfFonts;
import dev.hidly.nubi.document.data.enums.DocumentType;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.List;

// 서류 내용을 A4 PDF로 만든다. 한글은 나눔고딕(PdfFonts)을 넣어서 쓴다.
@Component
public class DocumentPdfRenderer {
    private final BaseFont baseFont;

    public DocumentPdfRenderer(PdfFonts pdfFonts) {
        this.baseFont = pdfFonts.nanumGothic();
    }

    public static String titleOf(DocumentType type) {
        return switch (type) {
            case FORM -> "프로그램 참가 신청서";
            case INQUIRY -> "문의 문자";
            case CALL_SCRIPT -> "통화할 때 이렇게 말해요";
        };
    }

    public byte[] render(DocumentType type, String content) {
        List<String> lines = content.lines().toList();
        // 신청서는 첫 줄이 제목이라 본문에서 뺀다.
        List<String> body = type == DocumentType.FORM && !lines.isEmpty() && lines.getFirst().equals(titleOf(type))
                ? lines.subList(1, lines.size()) : lines;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 56, 56, 64, 64);
        PdfWriter.getInstance(document, out);
        document.open();
        Paragraph title = new Paragraph(titleOf(type), new Font(baseFont, 18, Font.BOLD));
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(24);
        document.add(title);
        Font bodyFont = new Font(baseFont, 11, Font.NORMAL);
        for (String line : body) {
            Paragraph paragraph = new Paragraph(line, bodyFont);
            paragraph.setLeading(18);
            paragraph.setSpacingAfter(8);
            document.add(paragraph);
        }
        document.close();
        return out.toByteArray();
    }
}
