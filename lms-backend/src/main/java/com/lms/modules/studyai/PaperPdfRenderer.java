package com.lms.modules.studyai;

import com.lms.modules.studyai.GenQuestion.Option;
import com.lms.modules.studyai.GenQuestion.RubricItem;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;

/**
 * Prints a question paper set, and its answer key, as PDF (OpenPDF).
 *
 * <p>Every page carries the set code in its header and footer, so a loose
 * page can always be matched to its key. Choice questions print their options
 * as A, B, C… in the set's shuffled order; short answers get ruled lines to
 * write on; long answers go in the answer booklet.
 */
@Component
class PaperPdfRenderer {

    private static final Font TITLE   = new Font(Font.HELVETICA, 15, Font.BOLD);
    private static final Font SUB     = new Font(Font.HELVETICA, 11, Font.NORMAL);
    private static final Font LABEL   = new Font(Font.HELVETICA, 9, Font.BOLD);
    private static final Font BODY    = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font BOLD    = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font SMALL   = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
    private static final Font SECTION = new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE);
    private static final Font SET_BIG = new Font(Font.HELVETICA, 20, Font.BOLD);
    private static final Color SECTION_BG = new Color(55, 65, 81);
    private static final Color KEY_BG = new Color(236, 253, 245);

    record Paper(String courseCode, String courseName, String title, String instructions,
                 Integer durationMinutes, BigDecimal totalMarks) {}

    record PrintedQuestion(int number, String section, GenQuestion question, BigDecimal marks,
                           List<Option> printedOptions) {}

    byte[] questionPaper(Paper paper, String setCode, List<PrintedQuestion> questions) {
        return render(paper, setCode, false, doc -> {
            String current = null;
            for (PrintedQuestion pq : questions) {
                if (!pq.section().equals(current)) {
                    current = pq.section();
                    sectionHeader(doc, current, questions);
                }
                question(doc, pq);
            }
        });
    }

    byte[] answerKey(Paper paper, String setCode, List<PrintedQuestion> questions) {
        return render(paper, setCode, true, doc -> {
            PdfPTable table = new PdfPTable(new float[]{1.1f, 2.2f, 10f, 1.4f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String h : List.of("Q", "Answer", "Model answer / marking scheme", "Marks")) {
                PdfPCell c = new PdfPCell(new Phrase(h, LABEL));
                c.setBackgroundColor(KEY_BG);
                c.setPadding(5);
                table.addCell(c);
            }
            for (PrintedQuestion pq : questions) {
                GenQuestion q = pq.question();
                table.addCell(cell(String.valueOf(pq.number()), BOLD));
                if (q.isWritten()) {
                    table.addCell(cell("Written", BODY));
                    Paragraph p = new Paragraph(q.getModelAnswer() == null ? "" : q.getModelAnswer(), BODY);
                    PdfPCell detail = new PdfPCell();
                    detail.setPadding(5);
                    detail.addElement(p);
                    if (q.getRubric() != null && !q.getRubric().isEmpty()) {
                        com.lowagie.text.List list = new com.lowagie.text.List(false, 10);
                        for (RubricItem r : q.getRubric()) {
                            String kws = r.keywords() == null || r.keywords().isEmpty() ? ""
                                    : "  (" + String.join(", ", r.keywords()) + ")";
                            list.add(new ListItem(r.criterion() + " — " + plain(r.marks()) + kws, SMALL));
                        }
                        detail.addElement(list);
                    }
                    table.addCell(detail);
                } else {
                    List<String> letters = PaperShuffler.correctLetters(
                            pq.printedOptions().stream().map(Option::id).toList(), q.getCorrectAnswer());
                    table.addCell(cell(String.join(", ", letters), BOLD));
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < pq.printedOptions().size(); i++) {
                        Option o = pq.printedOptions().get(i);
                        if (q.getCorrectAnswer().contains(o.id())) {
                            if (!sb.isEmpty()) sb.append("; ");
                            sb.append((char) ('A' + i)).append(") ").append(o.text());
                        }
                    }
                    table.addCell(cell(sb.toString(), BODY));
                }
                table.addCell(cell(plain(pq.marks()), BODY));
            }
            doc.add(table);
        });
    }

    private interface Body { void write(Document doc) throws DocumentException; }

    private byte[] render(Paper paper, String setCode, boolean key, Body body) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 46, 46, 50, 54);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new Footer(paper, setCode, key));
            doc.addTitle(paper.title() + " — Set " + setCode + (key ? " answer key" : ""));
            doc.open();
            header(doc, paper, setCode, key);
            body.write(doc);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not render the paper PDF", e);
        }
    }

    private void header(Document doc, Paper paper, String setCode, boolean key) throws DocumentException {
        PdfPTable top = new PdfPTable(new float[]{8f, 2f});
        top.setWidthPercentage(100);
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.addElement(new Paragraph(paper.courseCode() + " — " + paper.courseName(), SUB));
        left.addElement(new Paragraph(paper.title() + (key ? " — ANSWER KEY" : ""), TITLE));
        StringBuilder meta = new StringBuilder();
        if (paper.durationMinutes() != null) meta.append("Time allowed: ").append(minutes(paper.durationMinutes())).append("     ");
        meta.append("Total marks: ").append(plain(paper.totalMarks()));
        left.addElement(new Paragraph(meta.toString(), BODY));
        top.addCell(left);

        PdfPCell set = new PdfPCell();
        set.setBorderWidth(1.5f);
        set.setHorizontalAlignment(Element.ALIGN_CENTER);
        set.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Paragraph label = new Paragraph("SET", LABEL);
        label.setAlignment(Element.ALIGN_CENTER);
        Paragraph code = new Paragraph(setCode, SET_BIG);
        code.setAlignment(Element.ALIGN_CENTER);
        set.addElement(label);
        set.addElement(code);
        set.setPaddingBottom(8);
        top.addCell(set);
        doc.add(top);

        if (!key) {
            PdfPTable student = new PdfPTable(new float[]{1.3f, 4f, 1.2f, 3f});
            student.setWidthPercentage(100);
            student.setSpacingBefore(10);
            for (String[] pair : new String[][]{{"Name", ""}, {"Roll No.", ""}}) {
                PdfPCell l = cell(pair[0], LABEL);
                l.setBorder(Rectangle.NO_BORDER);
                student.addCell(l);
                PdfPCell v = new PdfPCell(new Phrase(" ", BODY));
                v.setBorder(Rectangle.BOTTOM);
                v.setFixedHeight(20);
                student.addCell(v);
            }
            doc.add(student);
            if (paper.instructions() != null && !paper.instructions().isBlank()) {
                Paragraph ins = new Paragraph("Instructions: " + paper.instructions(), BODY);
                ins.setSpacingBefore(8);
                doc.add(ins);
            }
        }
        Paragraph rule = new Paragraph(" ");
        rule.setSpacingAfter(2);
        doc.add(rule);
    }

    private void sectionHeader(Document doc, String section, List<PrintedQuestion> all) throws DocumentException {
        BigDecimal marks = all.stream().filter(q -> q.section().equals(section)).map(PrintedQuestion::marks)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String name = switch (section) {
            case "CHOICE" -> "Section A — Multiple choice";
            case "SHORT" -> "Section B — Short answers";
            default -> "Section C — Long answers";
        };
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(10);
        t.setSpacingAfter(6);
        PdfPCell c = new PdfPCell(new Phrase(name + "   (" + plain(marks) + " marks)", SECTION));
        c.setBackgroundColor(SECTION_BG);
        c.setPadding(5);
        c.setBorder(Rectangle.NO_BORDER);
        t.addCell(c);
        doc.add(t);
    }

    private void question(Document doc, PrintedQuestion pq) throws DocumentException {
        GenQuestion q = pq.question();
        PdfPTable row = new PdfPTable(new float[]{0.6f, 9f, 1.4f});
        row.setWidthPercentage(100);
        row.setKeepTogether(true);
        row.setSpacingBefore(4);
        row.addCell(plainCell(pq.number() + ".", BOLD));

        PdfPCell body = plainCell(null, BODY);
        body.addElement(new Paragraph(q.getQuestionText(), BODY));
        if (!q.isWritten()) {
            for (int i = 0; i < pq.printedOptions().size(); i++) {
                Paragraph o = new Paragraph("(" + (char) ('A' + i) + ")  " + pq.printedOptions().get(i).text(), BODY);
                o.setIndentationLeft(10);
                o.setSpacingBefore(2);
                body.addElement(o);
            }
            if ("MSQ".equals(q.getType())) body.addElement(new Paragraph("Select all that apply.", SMALL));
        } else if ("SHORT_ANSWER".equals(q.getType())) {
            for (int i = 0; i < 4; i++) {
                Paragraph line = new Paragraph("_____________________________________________________________________________", SMALL);
                line.setSpacingBefore(8);
                body.addElement(line);
            }
        } else {
            body.addElement(new Paragraph("Answer in the answer booklet.", SMALL));
        }
        row.addCell(body);

        PdfPCell marks = plainCell("[" + plain(pq.marks()) + "]", LABEL);
        marks.setHorizontalAlignment(Element.ALIGN_RIGHT);
        row.addCell(marks);
        doc.add(row);
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setPadding(5);
        return c;
    }

    private static PdfPCell plainCell(String text, Font font) {
        PdfPCell c = text == null ? new PdfPCell() : new PdfPCell(new Phrase(text, font));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingTop(2);
        return c;
    }

    static String plain(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private static String minutes(int m) {
        if (m < 60) return m + " minutes";
        int h = m / 60, r = m % 60;
        return h + (h == 1 ? " hour" : " hours") + (r == 0 ? "" : " " + r + " minutes");
    }

    /** "Set A · Page 2" on every page. */
    private static final class Footer extends PdfPageEventHelper {
        private final Paper paper;
        private final String setCode;
        private final boolean key;

        Footer(Paper paper, String setCode, boolean key) {
            this.paper = paper;
            this.setCode = setCode;
            this.key = key;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            String text = paper.courseCode() + " · " + paper.title() + " · Set " + setCode
                    + (key ? " · ANSWER KEY" : "") + " · Page " + writer.getPageNumber();
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase(text, SMALL),
                    (document.left() + document.right()) / 2, document.bottom() - 22, 0);
        }
    }
}
