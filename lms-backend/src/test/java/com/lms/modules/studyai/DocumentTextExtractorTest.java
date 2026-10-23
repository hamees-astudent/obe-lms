package com.lms.modules.studyai;

import com.lms.modules.studyai.DocumentTextExtractor.Format;
import com.lms.modules.studyai.DocumentTextExtractor.NoTextException;
import com.lms.modules.studyai.DocumentTextExtractor.Segment;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor();

    @Test
    void detectsFormatFromMimeTypeOrExtension() {
        assertThat(DocumentTextExtractor.detect("application/pdf", "x")).isEqualTo(Format.PDF);
        assertThat(DocumentTextExtractor.detect(null, "Lecture 3.PPTX")).isEqualTo(Format.PPTX);
        assertThat(DocumentTextExtractor.detect(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", null)).isEqualTo(Format.DOCX);
        assertThat(DocumentTextExtractor.detect("video/mp4", "talk.mp4")).isNull();
    }

    @Test
    void readsPdfPageByPage() throws Exception {
        byte[] pdf = pdf(List.of(
                List.of("Normalisation", "Normalisation removes redundancy from relational tables."),
                List.of("Indexes", "A B-tree index keeps keys sorted for logarithmic lookups.")));

        List<Segment> pages = extractor.extract(pdf, Format.PDF, 100);

        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).locationType()).isEqualTo("PAGE");
        assertThat(pages.get(0).number()).isEqualTo(1);
        assertThat(pages.get(0).heading()).isEqualTo("Normalisation");
        assertThat(pages.get(1).text()).contains("logarithmic lookups");
    }

    @Test
    void aPdfWithoutTextIsReportedAsAScan() throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            assertThatThrownBy(() -> extractor.extract(out.toByteArray(), Format.PDF, 100))
                    .isInstanceOf(NoTextException.class)
                    .hasMessageContaining("scan");
        }
    }

    @Test
    void readsPptxSlideBySlideWithTitlesAndNotes() throws Exception {
        byte[] pptx;
        try (XMLSlideShow show = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSLFSlideLayout layout = show.getSlideMasters().get(0).getLayout(SlideLayout.TITLE_AND_CONTENT);
            XSLFSlide slide = show.createSlide(layout);
            slide.getPlaceholder(0).setText("TCP handshake");
            slide.getPlaceholder(1).setText("SYN is sent by the client\nSYN-ACK is returned by the server");
            for (XSLFShape s : show.getNotesSlide(slide).getShapes()) {
                if (s instanceof XSLFTextShape ts && ts.getPlaceholder() == org.apache.poi.sl.usermodel.Placeholder.BODY) {
                    ts.setText("Mention the final ACK.");
                }
            }
            show.write(out);
            pptx = out.toByteArray();
        }

        List<Segment> slides = extractor.extract(pptx, Format.PPTX, 100);

        assertThat(slides).hasSize(1);
        assertThat(slides.get(0).locationType()).isEqualTo("SLIDE");
        assertThat(slides.get(0).heading()).isEqualTo("TCP handshake");
        assertThat(slides.get(0).text()).contains("SYN-ACK is returned by the server").contains("final ACK");
    }

    @Test
    void readsDocxIntoSectionsAtHeadings() throws Exception {
        byte[] docx;
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            heading(doc, "Processes");
            doc.createParagraph().createRun().setText("A process is a program in execution with its own address space.");
            heading(doc, "Threads");
            doc.createParagraph().createRun().setText("Threads share the address space of their process.");
            doc.write(out);
            docx = out.toByteArray();
        }

        List<Segment> sections = extractor.extract(docx, Format.DOCX, 100);

        assertThat(sections).extracting(Segment::heading).containsExactly("Processes", "Threads");
        assertThat(sections).extracting(Segment::locationType).containsOnly("SECTION");
        assertThat(sections.get(1).text()).contains("share the address space");
    }

    private static void heading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setStyle("Heading1");
        p.createRun().setText(text);
    }

    static byte[] pdf(List<List<String>> pages) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (List<String> lines : pages) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(font, 12);
                    cs.newLineAtOffset(50, 700);
                    for (String line : lines) {
                        cs.showText(line);
                        cs.newLineAtOffset(0, -20);
                    }
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
