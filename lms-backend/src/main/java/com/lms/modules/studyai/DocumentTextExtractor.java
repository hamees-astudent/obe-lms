package com.lms.modules.studyai;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the text out of an uploaded PDF, PowerPoint or Word file, keeping
 * where each piece came from so answers can cite "p. 4" or "Slide 12".
 *
 * <ul>
 *   <li>PDF — one segment per page (PDFBox). A page's first short line is
 *       taken as its heading, which suits slide decks exported to PDF.</li>
 *   <li>PPTX — one segment per slide (Apache POI): the title is the heading,
 *       the body text, tables and speaker notes are the text.</li>
 *   <li>DOCX — one segment per section, a section starting at each Heading 1/2
 *       paragraph. Word files have no fixed pages, so citations name the
 *       section.</li>
 * </ul>
 */
@Component
class DocumentTextExtractor {

    enum Format { PDF, PPTX, DOCX }

    /** A page, slide or section of text. */
    record Segment(String locationType, int number, String heading, String text) {}

    /** The file has no text to read — typically a scanned PDF. */
    static class NoTextException extends RuntimeException {
        NoTextException(String message) { super(message); }
    }

    static Format detect(String mimeType, String fileName) {
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (mime.equals("application/pdf") || name.endsWith(".pdf")) return Format.PDF;
        if (mime.contains("presentationml") || name.endsWith(".pptx")) return Format.PPTX;
        if (mime.contains("wordprocessingml") || name.endsWith(".docx")) return Format.DOCX;
        return null;
    }

    List<Segment> extract(byte[] data, Format format, int maxPages) {
        List<Segment> segments = switch (format) {
            case PDF -> pdf(data, maxPages);
            case PPTX -> pptx(data, maxPages);
            case DOCX -> docx(data);
        };
        int chars = segments.stream().mapToInt(s -> s.text().strip().length()).sum();
        if (chars < 40) {
            throw new NoTextException(format == Format.PDF
                    ? "No text could be read from this PDF. It is probably a scan; upload a PDF with selectable text."
                    : "This file contains no readable text.");
        }
        return segments;
    }

    private List<Segment> pdf(byte[] data, int maxPages) {
        try (PDDocument doc = Loader.loadPDF(data)) {
            int pages = Math.min(doc.getNumberOfPages(), maxPages);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<Segment> out = new ArrayList<>();
            for (int p = 1; p <= pages; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = clean(stripper.getText(doc));
                if (text.isBlank()) continue;
                out.add(new Segment("PAGE", p, firstLineHeading(text), text));
            }
            return out;
        } catch (NoTextException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read this PDF: " + e.getMessage(), e);
        }
    }

    private List<Segment> pptx(byte[] data, int maxSlides) {
        try (XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(data))) {
            List<Segment> out = new ArrayList<>();
            int n = 0;
            for (XSLFSlide slide : show.getSlides()) {
                if (++n > maxSlides) break;
                String title = slide.getTitle();
                StringBuilder body = new StringBuilder();
                for (XSLFShape shape : slide.getShapes()) appendShape(shape, title, body);
                XSLFNotes notes = slide.getNotes();
                if (notes != null) {
                    StringBuilder noteText = new StringBuilder();
                    for (XSLFShape shape : notes.getShapes()) {
                        // Only the notes body: not the slide image, number or header.
                        if (shape instanceof XSLFTextShape ts
                                && ts.getPlaceholder() == org.apache.poi.sl.usermodel.Placeholder.BODY) {
                            String t = ts.getText();
                            if (t != null && !t.isBlank() && !t.startsWith("Click to edit Master")) {
                                noteText.append(t).append('\n');
                            }
                        }
                    }
                    if (!noteText.toString().isBlank()) body.append("\n\n").append(noteText);
                }
                String text = clean(body.toString());
                String heading = title == null ? null : clean(title);
                if (text.isBlank() && (heading == null || heading.isBlank())) continue;
                out.add(new Segment("SLIDE", n, heading == null || heading.isBlank() ? null : heading,
                        text.isBlank() ? heading : text));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read this PowerPoint file: " + e.getMessage(), e);
        }
    }

    private static void appendShape(XSLFShape shape, String title, StringBuilder out) {
        if (shape instanceof XSLFGroupShape group) {
            for (XSLFShape s : group.getShapes()) appendShape(s, title, out);
        } else if (shape instanceof XSLFTable table) {
            for (XSLFTableRow row : table.getRows()) {
                List<String> cells = new ArrayList<>();
                for (XSLFTableCell cell : row.getCells()) cells.add(cell.getText().strip());
                out.append(String.join(" | ", cells)).append('\n');
            }
            out.append('\n');
        } else if (shape instanceof XSLFTextShape text) {
            String t = text.getText();
            if (t == null || t.isBlank() || t.strip().equals(title == null ? null : title.strip())) return;
            // Each paragraph of a text box is a bullet; keep them as lines.
            for (XSLFTextParagraph p : text.getTextParagraphs()) {
                String line = p.getText().strip();
                if (!line.isEmpty()) out.append("• ").append(line).append('\n');
            }
            out.append('\n');
        }
    }

    private List<Segment> docx(byte[] data) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(data))) {
            List<Segment> out = new ArrayList<>();
            String heading = null;
            StringBuilder body = new StringBuilder();
            int section = 1;
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph p) {
                    String text = p.getText().strip();
                    if (text.isEmpty()) continue;
                    if (isHeading(p)) {
                        if (!body.toString().isBlank()) {
                            out.add(new Segment("SECTION", section++, heading, clean(body.toString())));
                        } else if (heading != null) {
                            // A heading straight after a heading: keep the outer one as context.
                            text = heading + " — " + text;
                        }
                        heading = text;
                        body.setLength(0);
                    } else {
                        boolean bullet = p.getNumID() != null;
                        body.append(bullet ? "• " : "").append(text).append(bullet ? "\n" : "\n\n");
                    }
                } else if (element instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        List<String> cells = new ArrayList<>();
                        for (XWPFTableCell cell : row.getTableCells()) cells.add(cell.getText().strip());
                        body.append(String.join(" | ", cells)).append('\n');
                    }
                    body.append('\n');
                }
            }
            if (!body.toString().isBlank()) out.add(new Segment("SECTION", section, heading, clean(body.toString())));
            return out;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read this Word file: " + e.getMessage(), e);
        }
    }

    private static boolean isHeading(XWPFParagraph p) {
        String style = p.getStyle();
        if (style == null) style = p.getStyleID();
        if (style == null) return false;
        String s = style.toLowerCase(Locale.ROOT).replace(" ", "");
        return s.startsWith("heading1") || s.startsWith("heading2") || s.equals("title")
                || s.startsWith("heading3");
    }

    /** A page's first line when it reads like a title: short and not a sentence. */
    private static String firstLineHeading(String text) {
        String first = text.strip().split("\n", 2)[0].strip();
        if (first.length() < 3 || first.length() > 80) return null;
        if (first.endsWith(".") || first.matches("^\\d+$")) return null;
        return first;
    }

    /**
     * Normalises whitespace and strips control characters, keeping line breaks
     * and the indentation at the start of a line (code depends on it).
     */
    static String clean(String text) {
        if (text == null) return "";
        return text.replace('\u00A0', ' ')
                .replace("\r\n", "\n")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "")
                .replaceAll("(?<=\\w)-\\n(?=\\w)", "")       // hyphenated line breaks
                .replace("\t", "    ")
                .replaceAll("(?<=\\S)[ ]{2,}", " ")             // runs of spaces inside a line, not before it
                .replaceAll(" +\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .replaceAll("^(?:[ ]*\\n)+", "")
                .stripTrailing();
    }
}
