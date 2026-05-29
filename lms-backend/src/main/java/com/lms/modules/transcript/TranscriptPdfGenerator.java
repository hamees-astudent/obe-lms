package com.lms.modules.transcript;

import com.lms.modules.transcript.dto.CloAttainmentDetail;
import com.lms.modules.transcript.dto.CourseTranscriptDetail;
import com.lms.modules.transcript.dto.PloAttainmentDetail;
import com.lms.modules.transcript.dto.TranscriptSnapshotData;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Generates a PDF transcript from {@link TranscriptSnapshotData} using OpenPDF.
 */
@Component
public class TranscriptPdfGenerator {

    // ── Colours & fonts ──────────────────────────────────────────────────────

    private static final Color HEADER_BG  = new Color(33, 97, 140);
    private static final Color ROW_ALT_BG = new Color(235, 245, 251);
    private static final Color TABLE_HDR  = new Color(52, 152, 219);

    private static final Font TITLE_FONT  = new Font(Font.HELVETICA, 18, Font.BOLD,  Color.WHITE);
    private static final Font LABEL_FONT  = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font VALUE_FONT  = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font TH_FONT     = new Font(Font.HELVETICA,  9, Font.BOLD,  Color.WHITE);
    private static final Font TD_FONT     = new Font(Font.HELVETICA,  9, Font.NORMAL);
    private static final Font SECTION_FT  = new Font(Font.HELVETICA, 11, Font.BOLD);

    // ── Public API ───────────────────────────────────────────────────────────

    public byte[] generatePdf(TranscriptSnapshotData data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 50, 50);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            addHeader(doc, data);
            addStudentInfo(doc, data);
            doc.add(Chunk.NEWLINE);
            addCoursesTable(doc, data.getCourses());
            addGpaSummary(doc, data);
            if (data.getCourses() != null) {
                addCloAttainment(doc, data.getCourses());
            }
            if (data.getPloAttainment() != null && !data.getPloAttainment().isEmpty()) {
                addPloAttainment(doc, data.getPloAttainment());
            }
            addFooter(doc);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate transcript PDF", e);
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void addHeader(Document doc, TranscriptSnapshotData data) throws DocumentException {
        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell(new Phrase("ACADEMIC TRANSCRIPT", TITLE_FONT));
        cell.setBackgroundColor(HEADER_BG);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(12);
        cell.setBorder(Rectangle.NO_BORDER);
        header.addCell(cell);

        cell = new PdfPCell(new Phrase(data.getProgramName(), new Font(Font.HELVETICA, 11, Font.NORMAL, Color.WHITE)));
        cell.setBackgroundColor(HEADER_BG);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPaddingBottom(10);
        cell.setBorder(Rectangle.NO_BORDER);
        header.addCell(cell);

        doc.add(header);
        doc.add(Chunk.NEWLINE);
    }

    private void addStudentInfo(Document doc, TranscriptSnapshotData data) throws DocumentException {
        PdfPTable infoTable = new PdfPTable(new float[]{1, 2, 1, 2});
        infoTable.setWidthPercentage(100);

        addInfoRow(infoTable, "Student Name",   data.getStudentName());
        addInfoRow(infoTable, "Student No.",    data.getStudentNumber());
        addInfoRow(infoTable, "Semester",       data.getSemesterName());
        addInfoRow(infoTable, "Grading Scale",  data.getGradingScaleName());

        doc.add(infoTable);
    }

    private void addInfoRow(PdfPTable table, String label, String value) {
        PdfPCell lCell = new PdfPCell(new Phrase(label, LABEL_FONT));
        lCell.setBorder(Rectangle.NO_BORDER);
        lCell.setPadding(4);
        table.addCell(lCell);

        PdfPCell vCell = new PdfPCell(new Phrase(value != null ? value : "—", VALUE_FONT));
        vCell.setBorder(Rectangle.NO_BORDER);
        vCell.setPadding(4);
        table.addCell(vCell);
    }

    private void addCoursesTable(Document doc, List<CourseTranscriptDetail> courses)
            throws DocumentException {
        doc.add(new Paragraph("Course Grades", SECTION_FT));
        doc.add(Chunk.NEWLINE);

        if (courses == null || courses.isEmpty()) {
            doc.add(new Paragraph("No courses found.", VALUE_FONT));
            doc.add(Chunk.NEWLINE);
            return;
        }

        String[] headers = {"Code", "Course Name", "Credits", "Attend%", "Marks", "Pct%", "Grade", "Points"};
        float[]  widths  = {8f,     25f,            6f,        8f,        10f,     8f,      7f,      8f};

        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);

        for (String h : headers) {
            PdfPCell hCell = new PdfPCell(new Phrase(h, TH_FONT));
            hCell.setBackgroundColor(TABLE_HDR);
            hCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            hCell.setPadding(5);
            table.addCell(hCell);
        }

        boolean alt = false;
        for (CourseTranscriptDetail c : courses) {
            Color bg = alt ? ROW_ALT_BG : Color.WHITE;
            addCourseRow(table, c, bg);
            alt = !alt;
        }

        doc.add(table);
        doc.add(Chunk.NEWLINE);
    }

    private void addCourseRow(PdfPTable table, CourseTranscriptDetail c, Color bg) {
        String[] values = {
                c.getCourseCode(),
                c.getCourseName(),
                String.valueOf(c.getCreditHours()),
                fmt1(c.getAttendancePercentage()),
                fmt1(c.getMarksObtained()) + "/" + fmt1(c.getTotalMarks()),
                fmt1(c.getPercentage()),
                c.getGradeLetter() != null ? c.getGradeLetter() : "—",
                fmt2(c.getGradePoints())
        };
        for (String v : values) {
            PdfPCell cell = new PdfPCell(new Phrase(v, TD_FONT));
            cell.setBackgroundColor(bg);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(4);
            table.addCell(cell);
        }
    }

    private void addGpaSummary(Document doc, TranscriptSnapshotData data) throws DocumentException {
        doc.add(new Paragraph("GPA Summary", SECTION_FT));
        doc.add(Chunk.NEWLINE);

        PdfPTable t = new PdfPTable(new float[]{1, 1, 1, 1});
        t.setWidthPercentage(60);
        t.setHorizontalAlignment(Element.ALIGN_LEFT);

        String[] labels = {"Semester GPA", "Cumulative GPA", "Total Credits", "Earned Credits"};
        String[] vals   = {
                fmt2(data.getSemesterGpa()),
                fmt2(data.getCumulativeGpa()),
                String.valueOf(data.getTotalCreditHours()),
                String.valueOf(data.getEarnedCreditHours())
        };

        for (String l : labels) {
            PdfPCell h = new PdfPCell(new Phrase(l, TH_FONT));
            h.setBackgroundColor(TABLE_HDR);
            h.setHorizontalAlignment(Element.ALIGN_CENTER);
            h.setPadding(5);
            t.addCell(h);
        }
        for (String v : vals) {
            PdfPCell c = new PdfPCell(new Phrase(v, new Font(Font.HELVETICA, 10, Font.BOLD)));
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setPadding(5);
            t.addCell(c);
        }

        doc.add(t);
        doc.add(Chunk.NEWLINE);
    }

    private void addCloAttainment(Document doc, List<CourseTranscriptDetail> courses)
            throws DocumentException {
        boolean hasClo = courses.stream()
                .anyMatch(c -> c.getCloAttainment() != null && !c.getCloAttainment().isEmpty());
        if (!hasClo) return;

        doc.add(new Paragraph("CLO Attainment by Course", SECTION_FT));
        doc.add(Chunk.NEWLINE);

        for (CourseTranscriptDetail course : courses) {
            if (course.getCloAttainment() == null || course.getCloAttainment().isEmpty()) continue;

            doc.add(new Paragraph(course.getCourseCode() + " — " + course.getCourseName(),
                    new Font(Font.HELVETICA, 9, Font.BOLD)));

            PdfPTable t = new PdfPTable(new float[]{10f, 40f, 15f});
            t.setWidthPercentage(70);
            t.setHorizontalAlignment(Element.ALIGN_LEFT);

            for (String h : new String[]{"CLO", "Title", "Attainment %"}) {
                PdfPCell hc = new PdfPCell(new Phrase(h, TH_FONT));
                hc.setBackgroundColor(TABLE_HDR);
                hc.setPadding(4);
                t.addCell(hc);
            }
            for (CloAttainmentDetail clo : course.getCloAttainment()) {
                t.addCell(makeCell(clo.getCloCode()));
                t.addCell(makeCell(clo.getCloTitle()));
                t.addCell(makeCell(fmt1(clo.getAttainmentPercentage())));
            }
            doc.add(t);
            doc.add(Chunk.NEWLINE);
        }
    }

    private void addPloAttainment(Document doc, List<PloAttainmentDetail> ploList)
            throws DocumentException {
        doc.add(new Paragraph("PLO Attainment", SECTION_FT));
        doc.add(Chunk.NEWLINE);

        PdfPTable t = new PdfPTable(new float[]{10f, 50f, 15f});
        t.setWidthPercentage(80);

        for (String h : new String[]{"PLO", "Title", "Attainment %"}) {
            PdfPCell hc = new PdfPCell(new Phrase(h, TH_FONT));
            hc.setBackgroundColor(TABLE_HDR);
            hc.setPadding(4);
            t.addCell(hc);
        }
        boolean alt = false;
        for (PloAttainmentDetail plo : ploList) {
            Color bg = alt ? ROW_ALT_BG : Color.WHITE;
            PdfPCell code = makeCell(plo.getPloCode()); code.setBackgroundColor(bg);
            PdfPCell title = makeCell(plo.getPloTitle()); title.setBackgroundColor(bg);
            PdfPCell pct = makeCell(fmt1(plo.getAttainmentPercentage())); pct.setBackgroundColor(bg);
            t.addCell(code); t.addCell(title); t.addCell(pct);
            alt = !alt;
        }
        doc.add(t);
        doc.add(Chunk.NEWLINE);
    }

    private void addFooter(Document doc) throws DocumentException {
        doc.add(Chunk.NEWLINE);
        Paragraph footer = new Paragraph(
                "This is an auto-generated transcript. Verify with the Registrar for official records.",
                new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY));
        footer.setAlignment(Element.ALIGN_CENTER);
        doc.add(footer);
    }

    // ── Formatting helpers ────────────────────────────────────────────────────

    private PdfPCell makeCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "—", TD_FONT));
        cell.setPadding(4);
        return cell;
    }

    private String fmt1(Double v) {
        return v == null ? "—" : String.format("%.1f", v);
    }

    private String fmt2(Double v) {
        return v == null ? "—" : String.format("%.2f", v);
    }
}
