package dev.hidly.nubi.promotion.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import dev.hidly.nubi.common.pdf.PdfFonts;
import lombok.extern.slf4j.Slf4j;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.SplitCharacter;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfChunk;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfGState;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// 행사 홍보 포스터(A4 한 장).
// 위: 대표 사진(없으면 분야 색 배경)을 크게 깔고 분야·한 줄 소개·행사 이름.
// 가운데: 언제·어디서·몇 명 카드, 쉬운 말 홍보문, "이렇게 준비했어요" 배지(지원·편의시설).
// 아래 띠: 신청 방법·마감·문의, 신청 링크가 있으면 QR 코드, 주최.
@Slf4j
@Component
public class PosterPdfRenderer {
    private static final float W = PageSize.A4.getWidth();
    private static final float H = PageSize.A4.getHeight();
    private static final float M = 36;
    private static final float HERO_MIN_BOTTOM = 400;
    private static final float HERO_MAX_BOTTOM = 560;
    private static final float FOOTER_TOP = 112;
    private static final float CARD_HEIGHT = 96;

    private static final Color INK = new Color(0x11, 0x18, 0x27);
    private static final Color BODY = new Color(0x37, 0x41, 0x51);
    private static final Color MUTED = new Color(0x6B, 0x72, 0x80);
    private static final Color CARD = new Color(0xF4, 0xF6, 0xFA);
    private static final Color LINE = new Color(0xE5, 0xE7, 0xEB);

    // 분야별 색: 주색, 진한 색(아래 띠), 옅은 색(배지)
    private record Palette(Color main, Color dark, Color light, String label) {
    }

    private static final Palette DEFAULT_PALETTE = new Palette(rgb(0x3B6EF5), rgb(0x1E3A8A), rgb(0xDBEAFE), "함께해요");
    private static final Map<String, Palette> PALETTES = Map.of(
            "CULTURE", new Palette(rgb(0x7C3AED), rgb(0x4C1D95), rgb(0xEDE9FE), "문화·예술"),
            "SPORTS", new Palette(rgb(0x059669), rgb(0x064E3B), rgb(0xD1FAE5), "체육·나들이"),
            "EDUCATION", new Palette(rgb(0x2563EB), rgb(0x1E3A8A), rgb(0xDBEAFE), "배움"),
            "HEALTH", new Palette(rgb(0xE11D48), rgb(0x881337), rgb(0xFFE4E6), "건강"),
            "BENEFIT", new Palette(rgb(0xD97706), rgb(0x78350F), rgb(0xFEF3C7), "혜택"));

    // 한글 낱말 중간에서 줄이 끊기지 않게 띄어쓰기에서만 줄을 바꾼다.
    private static final SplitCharacter SPLIT_AT_SPACE = (start, current, end, cc, ck) -> {
        char c = ck == null ? cc[current] : (char) ck[Math.min(current, ck.length - 1)].getUnicodeEquivalent(cc[current]);
        return c == ' ' || c == '-' || c == '/';
    };

    private final BaseFont regular;
    private final BaseFont bold;
    private final BaseFont extraBold;

    public PosterPdfRenderer(PdfFonts pdfFonts) {
        this.regular = pdfFonts.nanumGothic();
        this.bold = pdfFonts.nanumGothicBold();
        this.extraBold = pdfFonts.nanumGothicExtraBold();
    }

    public record Poster(String title, String category, String hook, String hostName,
                         LocalDateTime startAt, LocalDateTime endAt, String venueName, String venueAddress,
                         Integer capacity, LocalDateTime deadlineAt, String applyMethod, String applyUrl, String contactPhone,
                         String easy, List<String> supports, List<String> facilities, byte[] photo) {
    }

    public byte[] render(Poster poster) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 0, 0, 0, 0);
        PdfWriter writer = PdfWriter.getInstance(document, out);
        document.open();
        PdfContentByte cb = writer.getDirectContent();
        Palette palette = poster.category() == null ? DEFAULT_PALETTE : PALETTES.getOrDefault(poster.category(), DEFAULT_PALETTE);

        // 흰 바탕을 먼저 깐다 (투명 효과가 있는 PDF를 어떤 뷰어는 검은 바탕으로 보여 준다)
        fill(cb, Color.WHITE, 0, 0, W, H);

        // 내용이 적으면 사진을 크게, 많으면 작게: 가운데 내용이 들어갈 높이를 먼저 잰다
        float width = W - M * 2 - 8;
        float easyHeight = measure(easyElements(poster), width);
        float badgesHeight = badgesHeight(poster);
        float body = 24 + CARD_HEIGHT + 22 + easyHeight + (badgesHeight > 0 ? 18 + badgesHeight : 0) + 22;
        float heroBottom = Math.max(HERO_MIN_BOTTOM, Math.min(HERO_MAX_BOTTOM, FOOTER_TOP + body));

        drawHero(cb, poster, palette, heroBottom);
        float cardsBottom = drawInfoCards(cb, poster, palette, heroBottom);
        float badgesTop = drawBadges(cb, poster, palette);
        drawEasy(cb, poster, cardsBottom - 22, badgesTop + 18);
        drawFooter(cb, poster, palette);

        document.close();
        return out.toByteArray();
    }

    // ---------- 위: 사진 + 행사 이름 ----------

    private void drawHero(PdfContentByte cb, Poster poster, Palette palette, float heroBottom) {
        float heroHeight = H - heroBottom;
        Image photo = photo(poster.photo());
        cb.saveState();
        cb.rectangle(0, heroBottom, W, heroHeight);
        cb.clip();
        cb.newPath();
        if (photo != null) {
            // 사진이 칸을 꽉 채우도록 잘라 넣는다
            float scale = Math.max(W / photo.getWidth(), heroHeight / photo.getHeight());
            float w = photo.getWidth() * scale;
            float h = photo.getHeight() * scale;
            photo.scaleAbsolute(w, h);
            photo.setAbsolutePosition((W - w) / 2, heroBottom + (heroHeight - h) / 2);
            try {
                cb.addImage(photo);
            } catch (Exception e) {
                log.warn("[poster] 대표 사진을 그리지 못함: {}", e.getMessage());
                fill(cb, palette.main(), 0, heroBottom, W, heroHeight);
            }
            // 글자가 잘 읽히게 아래쪽을 점점 어둡게, 위쪽도 살짝
            gradient(cb, Color.BLACK, heroBottom, heroHeight * 0.68f, 0.82f, true);
            gradient(cb, Color.BLACK, H - 90, 90, 0.35f, false);
        } else {
            drawPatternBackground(cb, palette, heroBottom, heroHeight);
        }
        cb.restoreState();

        // 위 왼쪽: 분야, 위 오른쪽: 주최
        float chipY = H - M - 26;
        float chipWidth = pill(cb, M, chipY, palette.label(), bold, 12, Color.WHITE, palette.main(), 1f);
        if (poster.hostName() != null) {
            text(cb, regular, 11.5f, Color.WHITE, 0.9f, W - M, chipY + 9, poster.hostName(), Element.ALIGN_RIGHT);
        }
        if (chipWidth > 0 && poster.startAt() != null) {
            pill(cb, M + chipWidth + 8, chipY, "%d월 %d일 %s요일".formatted(poster.startAt().getMonthValue(),
                    poster.startAt().getDayOfMonth(), dayShort(poster.startAt())), bold, 12, Color.BLACK, Color.WHITE, 0.32f);
        }

        // 아래: 한 줄 소개 + 행사 이름(굵게, 최대 3줄이 되도록 글자 크기를 줄인다)
        float width = W - M * 2;
        float titleSize = 42;
        Paragraph title = titleParagraph(poster.title(), titleSize);
        while (titleSize > 26 && measure(title, width) > titleSize * 1.18f * 3 + 2) {
            titleSize -= 4;
            title = titleParagraph(poster.title(), titleSize);
        }
        float titleHeight = measure(title, width);
        float titleBottom = heroBottom + 30;
        column(cb, title, M, titleBottom, W - M, titleBottom + titleHeight + 4);

        if (poster.hook() != null && !poster.hook().isBlank()) {
            Paragraph hook = paragraph(poster.hook(), bold, 16, new Color(255, 255, 255, 235), 22, Element.ALIGN_LEFT);
            float hookBottom = titleBottom + titleHeight + 8;
            column(cb, hook, M, hookBottom, W - M, hookBottom + 26);
        }
    }

    private Paragraph titleParagraph(String title, float size) {
        return paragraph(title, extraBold, size, Color.WHITE, size * 1.18f, Element.ALIGN_LEFT);
    }

    // 사진이 없을 때: 분야 색 위에 큰 원과 점무늬
    private static void drawPatternBackground(PdfContentByte cb, Palette palette, float heroBottom, float heroHeight) {
        fill(cb, palette.main(), 0, heroBottom, W, heroHeight);
        cb.saveState();
        opacity(cb, 0.14f);
        cb.setColorFill(Color.WHITE);
        cb.circle(W - 40, H - 30, 200);
        cb.fill();
        cb.circle(W - 150, heroBottom + 80, 70);
        cb.fill();
        opacity(cb, 0.10f);
        cb.circle(-30, H - 210, 130);
        cb.fill();
        opacity(cb, 0.22f);
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 8; col++) {
                cb.circle(W - 210 + col * 18, H - 150 - row * 18, 2.2f);
            }
        }
        cb.fill();
        cb.restoreState();
        gradient(cb, palette.dark(), heroBottom, heroHeight * 0.6f, 0.55f, true);
    }

    // ---------- 가운데: 언제·어디서·몇 명 ----------

    private float drawInfoCards(PdfContentByte cb, Poster poster, Palette palette, float heroBottom) {
        float top = heroBottom - 24;
        float bottom = top - CARD_HEIGHT;
        float gap = 10;
        float cardWidth = (W - M * 2 - gap * 2) / 3;

        String whenMain = poster.startAt() == null ? "일정 문의"
                : "%d월 %d일 (%s)".formatted(poster.startAt().getMonthValue(), poster.startAt().getDayOfMonth(), dayShort(poster.startAt()));
        String whenSub = poster.startAt() == null ? "주최 기관에 물어봐 주세요" : timeRange(poster.startAt(), poster.endAt());
        String whereMain = poster.venueName() == null ? "장소 문의" : poster.venueName();
        String whereSub = poster.venueAddress() == null ? "" : poster.venueAddress();
        String whoMain = poster.capacity() == null ? "누구나" : poster.capacity() + "명";
        String whoSub = poster.deadlineAt() == null ? (poster.capacity() == null ? "함께 오세요" : "자리가 차면 마감해요")
                : "%d월 %d일(%s)까지 신청".formatted(poster.deadlineAt().getMonthValue(), poster.deadlineAt().getDayOfMonth(),
                dayShort(poster.deadlineAt()));

        infoCard(cb, palette, M, bottom, cardWidth, "언제", whenMain, whenSub);
        infoCard(cb, palette, M + cardWidth + gap, bottom, cardWidth, "어디서", whereMain, whereSub);
        infoCard(cb, palette, M + (cardWidth + gap) * 2, bottom, cardWidth, "몇 명", whoMain, whoSub);
        return bottom;
    }

    private void infoCard(PdfContentByte cb, Palette palette, float x, float y, float w, String label, String main, String sub) {
        cb.saveState();
        cb.setColorFill(CARD);
        cb.roundRectangle(x, y, w, CARD_HEIGHT, 14);
        cb.fill();
        cb.setColorFill(palette.main());
        cb.roundRectangle(x + 14, y + CARD_HEIGHT - 26, 4, 12, 2);
        cb.fill();
        cb.restoreState();
        text(cb, bold, 11.5f, palette.main(), 1f, x + 23, y + CARD_HEIGHT - 24, label, Element.ALIGN_LEFT);

        float mainSize = main.length() > 12 ? 14 : 17;
        Paragraph mainText = paragraph(main, extraBold, mainSize, INK, mainSize + 4, Element.ALIGN_LEFT);
        Paragraph subText = paragraph(sub, regular, 10.5f, MUTED, 14, Element.ALIGN_LEFT);
        subText.setSpacingBefore(3);
        ColumnText ct = new ColumnText(cb);
        ct.setSimpleColumn(x + 14, y + 8, x + w - 12, y + CARD_HEIGHT - 32);
        ct.addElement(mainText);
        if (!sub.isBlank()) {
            ct.addElement(subText);
        }
        go(ct);
    }

    // ---------- 가운데: 쉬운 말 홍보문 ----------

    private void drawEasy(PdfContentByte cb, Poster poster, float top, float bottom) {
        if (top - bottom < 30) {
            return;
        }
        ColumnText ct = new ColumnText(cb);
        ct.setSimpleColumn(M + 4, bottom, W - M - 4, top);
        easyElements(poster).forEach(ct::addElement);
        go(ct);
    }

    // 날짜·장소는 카드에 있으니 "언제:", "어디:" 줄은 빼고, 첫 줄은 크게
    private List<Paragraph> easyElements(Poster poster) {
        List<String> lines = poster.easy().lines().map(String::trim)
                .filter(line -> !line.startsWith("언제:") && !line.startsWith("어디:"))
                .toList();
        List<Paragraph> elements = new ArrayList<>();
        boolean first = true;
        boolean pendingGap = false;
        for (String line : lines) {
            if (line.isBlank()) {
                pendingGap = !first;
                continue;
            }
            if (pendingGap) {
                Paragraph gap = new Paragraph(new Chunk(" "));
                gap.setLeading(8);
                elements.add(gap);
                pendingGap = false;
            }
            Paragraph paragraph = first
                    ? paragraph(line, extraBold, 19, INK, 27, Element.ALIGN_LEFT)
                    : paragraph(line, regular, 14, BODY, 22, Element.ALIGN_LEFT);
            if (first) {
                paragraph.setSpacingAfter(6);
            }
            elements.add(paragraph);
            first = false;
        }
        return elements;
    }

    // ---------- 가운데 아래: 이렇게 준비했어요 ----------

    private record Badge(String label, boolean support) {
    }

    private static final float BADGE_SIZE = 12.5f;
    private static final float BADGE_HEIGHT = 28;
    private static final float BADGE_GAP = 8;
    private static final float BADGE_HEADING = 30;

    // 배지를 줄 단위로 나눈다 (최대 3줄)
    private List<List<Badge>> badgeRows(Poster poster) {
        List<Badge> badges = new ArrayList<>();
        poster.supports().forEach(label -> badges.add(new Badge(label, true)));
        poster.facilities().stream().filter(label -> !poster.supports().contains(label))
                .forEach(label -> badges.add(new Badge(label, false)));
        List<List<Badge>> rows = new ArrayList<>();
        if (badges.isEmpty()) {
            return rows;
        }
        List<Badge> row = new ArrayList<>();
        float x = M;
        for (Badge badge : badges) {
            float w = badgeWidth(badge.label(), BADGE_SIZE);
            if (x + w > W - M && !row.isEmpty()) {
                rows.add(row);
                row = new ArrayList<>();
                x = M;
            }
            row.add(badge);
            x += w + BADGE_GAP;
        }
        rows.add(row);
        return rows.size() > 3 ? rows.subList(0, 3) : rows;
    }

    private float badgesHeight(Poster poster) {
        int rows = badgeRows(poster).size();
        return rows == 0 ? 0 : rows * (BADGE_HEIGHT + BADGE_GAP) + BADGE_HEADING - BADGE_GAP;
    }

    private float drawBadges(PdfContentByte cb, Poster poster, Palette palette) {
        float bottom = FOOTER_TOP + 22;
        List<List<Badge>> rows = badgeRows(poster);
        if (rows.isEmpty()) {
            return bottom;
        }
        float y = bottom;
        for (int i = rows.size() - 1; i >= 0; i--) {
            float bx = M;
            for (Badge badge : rows.get(i)) {
                Color bg = badge.support() ? palette.light() : Color.WHITE;
                Color fg = badge.support() ? palette.dark() : INK;
                float w = badgeWidth(badge.label(), BADGE_SIZE);
                cb.saveState();
                cb.setColorFill(bg);
                cb.roundRectangle(bx, y, w, BADGE_HEIGHT, BADGE_HEIGHT / 2);
                cb.fill();
                if (!badge.support()) {
                    cb.setColorStroke(LINE);
                    cb.setLineWidth(1);
                    cb.roundRectangle(bx + 0.5f, y + 0.5f, w - 1, BADGE_HEIGHT - 1, (BADGE_HEIGHT - 1) / 2);
                    cb.stroke();
                }
                check(cb, bx + 14, y + BADGE_HEIGHT / 2, palette.main());
                cb.restoreState();
                text(cb, bold, BADGE_SIZE, fg, 1f, bx + 27, y + 9.5f, badge.label(), Element.ALIGN_LEFT);
                bx += w + BADGE_GAP;
            }
            y += BADGE_HEIGHT + BADGE_GAP;
        }

        float headingY = y + 4;
        text(cb, extraBold, 15, INK, 1f, M, headingY, "이렇게 준비했어요", Element.ALIGN_LEFT);
        text(cb, regular, 10.5f, MUTED, 1f, M + extraBold.getWidthPoint("이렇게 준비했어요", 15) + 8, headingY + 1,
                "주최 기관이 준비한 지원과 장소 편의시설", Element.ALIGN_LEFT);
        return headingY + 15;
    }

    private float badgeWidth(String label, float size) {
        return bold.getWidthPoint(label, size) + 26 + 12;
    }

    private static void check(PdfContentByte cb, float cx, float cy, Color color) {
        cb.setColorFill(color);
        cb.circle(cx, cy, 6.5f);
        cb.fill();
        cb.setColorStroke(Color.WHITE);
        cb.setLineWidth(1.6f);
        cb.setLineCap(PdfContentByte.LINE_CAP_ROUND);
        cb.setLineJoin(PdfContentByte.LINE_JOIN_ROUND);
        cb.moveTo(cx - 3, cy + 0.2f);
        cb.lineTo(cx - 0.8f, cy - 2.2f);
        cb.lineTo(cx + 3.2f, cy + 2.4f);
        cb.stroke();
    }

    // ---------- 아래 띠: 신청 방법 ----------

    private void drawFooter(PdfContentByte cb, Poster poster, Palette palette) {
        fill(cb, palette.dark(), 0, 0, W, FOOTER_TOP);

        float qrSize = 84;
        boolean hasQr = poster.applyUrl() != null && !poster.applyUrl().isBlank();
        if (hasQr && drawQr(cb, poster.applyUrl(), W - M - qrSize, (FOOTER_TOP - qrSize) / 2, qrSize)) {
            text(cb, bold, 11, Color.WHITE, 0.9f, W - M - qrSize - 12, FOOTER_TOP / 2 + 4, "휴대폰 카메라로", Element.ALIGN_RIGHT);
            text(cb, bold, 11, Color.WHITE, 0.9f, W - M - qrSize - 12, FOOTER_TOP / 2 - 12, "찍으면 신청 화면", Element.ALIGN_RIGHT);
        }

        text(cb, bold, 11, Color.WHITE, 0.7f, M, FOOTER_TOP - 30, "참여 신청", Element.ALIGN_LEFT);
        String method = poster.applyMethod() == null ? "주최 기관에 물어보세요" : poster.applyMethod();
        text(cb, extraBold, 22, Color.WHITE, 1f, M, FOOTER_TOP - 58, method, Element.ALIGN_LEFT);

        List<String> details = new ArrayList<>();
        if (poster.deadlineAt() != null) {
            details.add("%d월 %d일(%s)까지".formatted(poster.deadlineAt().getMonthValue(), poster.deadlineAt().getDayOfMonth(),
                    dayShort(poster.deadlineAt())));
        }
        if (poster.contactPhone() != null && !poster.contactPhone().isBlank()) {
            details.add("문의 " + poster.contactPhone());
        }
        if (!details.isEmpty()) {
            text(cb, bold, 13, Color.WHITE, 0.95f, M, FOOTER_TOP - 80, String.join("  ·  ", details), Element.ALIGN_LEFT);
        }
        if (poster.hostName() != null) {
            text(cb, regular, 10.5f, Color.WHITE, 0.7f, M, 16, "주최 " + poster.hostName(), Element.ALIGN_LEFT);
        }
    }

    private static boolean drawQr(PdfContentByte cb, String url, float x, float y, float size) {
        BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.MARGIN, 0,
                    EncodeHintType.CHARACTER_SET, "UTF-8",
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
        } catch (WriterException | IllegalArgumentException e) {
            log.warn("[poster] QR 코드를 만들지 못함: {}", e.getMessage());
            return false;
        }
        cb.saveState();
        cb.setColorFill(Color.WHITE);
        cb.roundRectangle(x, y, size, size, 8);
        cb.fill();
        float padding = 7;
        float module = (size - padding * 2) / matrix.getWidth();
        cb.setColorFill(INK);
        for (int row = 0; row < matrix.getHeight(); row++) {
            for (int col = 0; col < matrix.getWidth(); col++) {
                if (matrix.get(col, row)) {
                    cb.rectangle(x + padding + col * module, y + size - padding - (row + 1) * module, module + 0.05f, module + 0.05f);
                }
            }
        }
        cb.fill();
        cb.restoreState();
        return true;
    }

    // ---------- 그리기 도구 ----------

    private Paragraph paragraph(String value, BaseFont font, float size, Color color, float leading, int align) {
        Chunk chunk = new Chunk(value, new Font(font, size, Font.NORMAL, color));
        chunk.setSplitCharacter(SPLIT_AT_SPACE);
        Paragraph paragraph = new Paragraph(chunk);
        paragraph.setLeading(leading);
        paragraph.setAlignment(align);
        return paragraph;
    }

    private static float measure(Paragraph paragraph, float width) {
        return measure(List.of(paragraph), width);
    }

    private static float measure(List<Paragraph> paragraphs, float width) {
        if (paragraphs.isEmpty()) {
            return 0;
        }
        ColumnText ct = new ColumnText(null);
        ct.setSimpleColumn(0, 0, width, 10_000);
        paragraphs.forEach(ct::addElement);
        try {
            ct.go(true);
        } catch (Exception e) {
            return 0;
        }
        return 10_000 - ct.getYLine();
    }

    private static void column(PdfContentByte cb, Paragraph paragraph, float llx, float lly, float urx, float ury) {
        ColumnText ct = new ColumnText(cb);
        ct.setSimpleColumn(llx, lly, urx, ury);
        ct.addElement(paragraph);
        go(ct);
    }

    private static void go(ColumnText ct) {
        try {
            ct.go();
        } catch (Exception e) {
            log.warn("[poster] 글을 배치하지 못함: {}", e.getMessage());
        }
    }

    private static void text(PdfContentByte cb, BaseFont font, float size, Color color, float alpha,
                             float x, float y, String value, int align) {
        cb.saveState();
        opacity(cb, alpha);
        cb.beginText();
        cb.setColorFill(color);
        cb.setFontAndSize(font, size);
        cb.showTextAligned(align, value, x, y, 0);
        cb.endText();
        cb.restoreState();
    }

    // 둥근 알약 모양 글자 칸. 너비를 돌려준다.
    private static float pill(PdfContentByte cb, float x, float y, String label, BaseFont font, float size,
                              Color bg, Color fg, float alpha) {
        float w = font.getWidthPoint(label, size) + 24;
        float h = 26;
        cb.saveState();
        opacity(cb, alpha);
        cb.setColorFill(bg);
        cb.roundRectangle(x, y, w, h, h / 2);
        cb.fill();
        cb.restoreState();
        text(cb, font, size, fg, 1f, x + 12, y + 8.5f, label, Element.ALIGN_LEFT);
        return w;
    }

    private static void fill(PdfContentByte cb, Color color, float x, float y, float w, float h) {
        cb.saveState();
        cb.setColorFill(color);
        cb.rectangle(x, y, w, h);
        cb.fill();
        cb.restoreState();
    }

    // 투명도가 부드럽게 바뀌는 그림을 깔아 그라데이션을 만든다. fromBottom이면 아래가 가장 진하다.
    private static void gradient(PdfContentByte cb, Color color, float y, float height, float maxAlpha, boolean fromBottom) {
        int steps = 256;
        java.awt.image.BufferedImage shade = new java.awt.image.BufferedImage(1, steps, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < steps; i++) {
            float t = (float) i / (steps - 1);
            // 그림의 위쪽(i=0)이 PDF에서도 위쪽이다
            float strength = fromBottom ? t : 1 - t;
            int alpha = Math.round(255 * maxAlpha * strength * strength);
            shade.setRGB(0, i, (alpha << 24) | (color.getRGB() & 0xFFFFFF));
        }
        try {
            Image image = Image.getInstance(shade, null);
            image.scaleAbsolute(W, height);
            image.setAbsolutePosition(0, y);
            cb.addImage(image);
        } catch (Exception e) {
            log.warn("[poster] 그라데이션을 그리지 못함: {}", e.getMessage());
        }
    }

    private static void opacity(PdfContentByte cb, float alpha) {
        PdfGState state = new PdfGState();
        state.setFillOpacity(alpha);
        state.setStrokeOpacity(alpha);
        cb.setGState(state);
    }

    private static String timeRange(LocalDateTime start, LocalDateTime end) {
        String from = clock(start);
        if (end == null || !end.toLocalDate().equals(start.toLocalDate())) {
            return from + " 시작";
        }
        return from + " ~ " + clock(end);
    }

    // 오후 1시, 오전 10시 30분
    private static String clock(LocalDateTime at) {
        int hour = at.getHour();
        String half = hour < 12 ? "오전" : "오후";
        int h12 = hour % 12 == 0 ? 12 : hour % 12;
        return at.getMinute() == 0 ? "%s %d시".formatted(half, h12) : "%s %d시 %d분".formatted(half, h12, at.getMinute());
    }

    private static String dayShort(LocalDateTime at) {
        return at.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
    }

    private static Color rgb(int hex) {
        return new Color(hex);
    }

    // JPG·PNG만 넣을 수 있다. WEBP처럼 못 읽는 사진은 빼고 분야 색 배경을 쓴다.
    private static Image photo(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            return Image.getInstance(bytes);
        } catch (Exception e) {
            log.warn("[poster] 대표 사진을 넣지 못해 색 배경 사용: {}", e.getMessage());
            return null;
        }
    }
}
