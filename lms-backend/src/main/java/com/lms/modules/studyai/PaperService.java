package com.lms.modules.studyai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.modules.studyai.GenQuestion.Option;
import com.lms.modules.studyai.PaperRepository.ItemRow;
import com.lms.modules.studyai.PaperRepository.PaperRow;
import com.lms.modules.studyai.PaperRepository.SetRow;
import com.lms.modules.studyai.dto.*;
import com.lms.shared.OfferingStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Printable question papers: assembling them within their limits, and
 * printing shuffled sets with answer keys.
 *
 * <p>The limits hold at every step. A paper's own marks limit may not exceed
 * the offering's maximum, and no add, edit or reorder can take the paper past
 * its marks limit or the offering's question limit — the request is refused
 * with how far over it would go, or with {@code fitToLimit} the questions that
 * do not fit are left out.
 */
@Service
@RequiredArgsConstructor
public class PaperService {

    private final PaperRepository repository;
    private final GenerationRepository generation;
    private final PaperSettingsService settings;
    private final CourseContextReader context;
    private final PaperPdfRenderer renderer;
    private final OfferingStaff offeringStaff;
    private final ObjectMapper mapper;
    private final SecureRandom seeds = new SecureRandom();

    // ── Papers ───────────────────────────────────────────────────────────────

    public List<PaperResponse> list(UUID pscId, UUID actorId, boolean isAdmin) {
        offeringStaff.require(pscId, actorId, isAdmin);
        int maxQ = settings.settings(pscId).maxQuestions();
        return repository.list(pscId).stream().map(p -> repository.response(p.id(), maxQ, false)).toList();
    }

    @Transactional
    public PaperResponse create(UUID pscId, PaperRequest req, UUID actorId, boolean isAdmin) {
        offeringStaff.require(pscId, actorId, isAdmin);
        checkPaperLimits(pscId, req, BigDecimal.ZERO);
        UUID id = repository.create(pscId, actorId, req);
        return repository.response(id, settings.settings(pscId).maxQuestions(), true);
    }

    public PaperResponse get(UUID paperId, UUID actorId, boolean isAdmin) {
        PaperRow p = require(paperId, actorId, isAdmin);
        return repository.response(p.id(), settings.settings(p.pscId()).maxQuestions(), true);
    }

    @Transactional
    public PaperResponse update(UUID paperId, PaperRequest req, UUID actorId, boolean isAdmin) {
        PaperRow p = require(paperId, actorId, isAdmin);
        checkPaperLimits(p.pscId(), req, p.totalMarks());
        repository.update(paperId, req);
        return repository.response(paperId, settings.settings(p.pscId()).maxQuestions(), true);
    }

    public void delete(UUID paperId, UUID actorId, boolean isAdmin) {
        require(paperId, actorId, isAdmin);
        repository.delete(paperId);
    }

    private void checkPaperLimits(UUID pscId, PaperRequest req, BigDecimal currentTotal) {
        PaperSettingsService.Settings s = settings.settings(pscId);
        if (req.maxTotalMarks().compareTo(s.maxTotalMarks()) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A paper in this course may carry at most " + plain(s.maxTotalMarks()) + " marks.");
        }
        if (req.durationMinutes() != null && req.durationMinutes() > s.maxDurationMinutes()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A paper in this course may last at most " + s.maxDurationMinutes() + " minutes.");
        }
        if (currentTotal.compareTo(req.maxTotalMarks()) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The paper already has " + plain(currentTotal) + " marks of questions; remove some before "
                    + "lowering its limit to " + plain(req.maxTotalMarks()) + ".");
        }
    }

    // ── Items ────────────────────────────────────────────────────────────────

    record Candidate(GenQuestion question, UUID sourceQuestionId, UUID sourceDraftId) {}

    @Transactional
    public AddPaperItemsResponse addItems(UUID paperId, AddPaperItemsRequest req, UUID actorId, boolean isAdmin) {
        PaperRow paper = require(paperId, actorId, isAdmin);
        List<Candidate> candidates = new ArrayList<>();
        if (req.draftIds() != null && !req.draftIds().isEmpty()) {
            for (GenerationRepository.DraftRow d : generation.findDrafts(req.draftIds())) {
                if (!d.pscId().equals(paper.pscId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A draft belongs to another course");
                }
                candidates.add(new Candidate(d.question(), null, d.id()));
            }
        }
        if (req.quizQuestionIds() != null && !req.quizQuestionIds().isEmpty()) {
            var found = repository.quizQuestions(paper.pscId(), req.quizQuestionIds());
            if (found.size() != new HashSet<>(req.quizQuestionIds()).size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A quiz question belongs to another course");
            }
            found.forEach(e -> candidates.add(new Candidate(e.getValue(), e.getKey(), null)));
        }
        if (req.questions() != null) {
            for (Map<String, Object> raw : req.questions()) {
                candidates.add(new Candidate(manual(raw), null, null));
            }
        }
        if (candidates.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing to add");
        return add(paper, candidates, req.fitToLimit());
    }

    /** Adds questions within the paper's limits; used by the generator's "save to paper". */
    AddPaperItemsResponse add(PaperRow paper, List<Candidate> candidates, boolean fitToLimit) {
        PaperSettingsService.Settings s = settings.settings(paper.pscId());
        int existing = repository.itemCount(paper.id());
        BigDecimal room = paper.maxTotalMarks().subtract(paper.totalMarks());
        int slots = s.maxQuestions() - existing;

        List<Candidate> chosen;
        if (fitToLimit) {
            chosen = PaperAssembler.fit(candidates, c -> c.question().getMarks(), c -> c.question().getCloIds(),
                    c -> c.question().getBloomLevel(), room, Math.max(0, slots), List.of());
        } else {
            BigDecimal adding = PaperAssembler.total(candidates, c -> c.question().getMarks());
            if (adding.compareTo(room) > 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Adding these questions (" + plain(adding) + " marks) would bring the paper to "
                        + plain(paper.totalMarks().add(adding)) + " marks, over its " + plain(paper.maxTotalMarks())
                        + "-mark limit by " + plain(paper.totalMarks().add(adding).subtract(paper.maxTotalMarks()))
                        + ". Remove some, lower their marks, or raise the paper's limit.");
            }
            if (candidates.size() > slots) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "A paper in this course may have at most " + s.maxQuestions() + " questions; it has "
                        + existing + " and you are adding " + candidates.size() + ".");
            }
            chosen = candidates;
        }

        Map<String, Integer> nextOrder = new HashMap<>();
        for (ItemRow r : repository.items(paper.id())) nextOrder.merge(r.section(), r.orderIndex() + 1, Math::max);
        for (Candidate c : chosen) {
            GenQuestion q = c.question().toBuilder().id(null).build();
            String section = section(q.getType());
            int order = nextOrder.getOrDefault(section, 0);
            nextOrder.put(section, order + 1);
            repository.addItem(paper.id(), section, order, q.getMarks(), q, c.sourceQuestionId(), c.sourceDraftId());
        }
        repository.afterItemsChanged(paper.id());
        return new AddPaperItemsResponse(chosen.size(), candidates.size() - chosen.size(),
                repository.response(paper.id(), s.maxQuestions(), true));
    }

    @Transactional
    public PaperResponse updateItem(UUID itemId, UpdatePaperItemRequest req, UUID actorId, boolean isAdmin) {
        UUID paperId = repository.paperOfItem(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paper question not found"));
        PaperRow paper = require(paperId, actorId, isAdmin);
        ItemRow item = repository.items(paperId).stream().filter(i -> i.id().equals(itemId)).findFirst().orElseThrow();
        BigDecimal newTotal = paper.totalMarks().subtract(item.marks()).add(req.marks());
        if (newTotal.compareTo(paper.maxTotalMarks()) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "That would bring the paper to " + plain(newTotal) + " marks, over its "
                    + plain(paper.maxTotalMarks()) + "-mark limit.");
        }
        GenQuestion q = item.question();
        q.setMarks(req.marks());
        if (q.isWritten()) q.setRubric(rescale(q.getRubric(), req.marks()));
        repository.updateItemMarks(itemId, req.marks(), q);
        repository.afterItemsChanged(paperId);
        return repository.response(paperId, settings.settings(paper.pscId()).maxQuestions(), true);
    }

    @Transactional
    public PaperResponse deleteItem(UUID itemId, UUID actorId, boolean isAdmin) {
        UUID paperId = repository.paperOfItem(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paper question not found"));
        PaperRow paper = require(paperId, actorId, isAdmin);
        repository.deleteItem(itemId);
        repository.afterItemsChanged(paperId);
        return repository.response(paperId, settings.settings(paper.pscId()).maxQuestions(), true);
    }

    @Transactional
    public PaperResponse reorder(UUID paperId, ReorderPaperItemsRequest req, UUID actorId, boolean isAdmin) {
        PaperRow paper = require(paperId, actorId, isAdmin);
        Set<UUID> ids = new HashSet<>();
        repository.items(paperId).forEach(i -> ids.add(i.id()));
        if (!ids.equals(new HashSet<>(req.itemIds()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "List every question of the paper exactly once");
        }
        for (int i = 0; i < req.itemIds().size(); i++) repository.setOrder(req.itemIds().get(i), i);
        repository.afterItemsChanged(paperId);
        return repository.response(paperId, settings.settings(paper.pscId()).maxQuestions(), true);
    }

    // ── Printing ─────────────────────────────────────────────────────────────

    @Transactional
    public PaperResponse print(UUID paperId, PrintPaperRequest req, UUID actorId, boolean isAdmin) {
        PaperRow paper = require(paperId, actorId, isAdmin);
        List<ItemRow> items = repository.items(paperId);
        if (items.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Add questions before printing");
        List<PaperShuffler.Layout> layouts = PaperShuffler.layouts(shufflerItems(items), req.sets(),
                req.shuffleQuestions(), req.shuffleOptions(), seeds::nextLong);
        repository.replaceSets(paperId, layouts, actorId);
        return repository.response(paperId, settings.settings(paper.pscId()).maxQuestions(), true);
    }

    public byte[] setPdf(UUID paperId, String setCode, boolean answerKey, UUID actorId, boolean isAdmin) {
        PaperRow paper = require(paperId, actorId, isAdmin);
        SetRow set = repository.sets(paperId).stream().filter(s -> s.setCode().equalsIgnoreCase(setCode)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Set " + setCode + " has not been printed; print the paper first."));
        return render(paper, set, repository.items(paperId), answerKey);
    }

    /** Every set and its answer key in one archive. */
    public byte[] zip(UUID paperId, UUID actorId, boolean isAdmin) {
        PaperRow paper = require(paperId, actorId, isAdmin);
        List<SetRow> sets = repository.sets(paperId);
        if (sets.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Print the paper first");
        List<ItemRow> items = repository.items(paperId);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
            String base = fileBase(paper.title());
            for (SetRow s : sets) {
                zip.putNextEntry(new ZipEntry(base + "-Set-" + s.setCode() + ".pdf"));
                zip.write(render(paper, s, items, false));
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry(base + "-Set-" + s.setCode() + "-answer-key.pdf"));
                zip.write(render(paper, s, items, true));
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not build the paper archive", e);
        }
    }

    public String fileName(UUID paperId, String suffix) {
        return repository.find(paperId).map(p -> fileBase(p.title())).orElse("paper") + suffix;
    }

    private byte[] render(PaperRow paper, SetRow set, List<ItemRow> items, boolean answerKey) {
        Map<UUID, ItemRow> byId = new HashMap<>();
        items.forEach(i -> byId.put(i.id(), i));
        List<PaperPdfRenderer.PrintedQuestion> printed = new ArrayList<>();
        int n = 1;
        for (UUID id : set.questionOrder()) {
            ItemRow item = byId.get(id);
            if (item == null) continue;
            List<Option> options = new ArrayList<>();
            List<String> order = set.optionOrders().getOrDefault(id, List.of());
            Map<String, Option> byOption = new HashMap<>();
            item.question().getOptions().forEach(o -> byOption.put(o.id(), o));
            for (String oid : order) if (byOption.containsKey(oid)) options.add(byOption.get(oid));
            // Options added after printing would be missing from the order; keep them visible.
            item.question().getOptions().stream().filter(o -> !order.contains(o.id())).forEach(options::add);
            printed.add(new PaperPdfRenderer.PrintedQuestion(n++, item.section(), item.question(), item.marks(), options));
        }
        CourseContextReader.Offering o = context.offering(paper.pscId());
        PaperPdfRenderer.Paper p = new PaperPdfRenderer.Paper(o.courseCode(), o.courseName(), paper.title(),
                paper.instructions(), paper.durationMinutes(), paper.totalMarks());
        return answerKey ? renderer.answerKey(p, set.setCode(), printed) : renderer.questionPaper(p, set.setCode(), printed);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private PaperRow require(UUID paperId, UUID actorId, boolean isAdmin) {
        PaperRow p = repository.find(paperId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question paper not found"));
        offeringStaff.require(p.pscId(), actorId, isAdmin);
        return p;
    }

    PaperRow requireRow(UUID paperId, UUID actorId, boolean isAdmin) {
        return require(paperId, actorId, isAdmin);
    }

    private static List<PaperShuffler.Item> shufflerItems(List<ItemRow> items) {
        return items.stream().map(i -> new PaperShuffler.Item(i.id(), i.section(), i.orderIndex(),
                i.question().getOptions().stream().map(Option::id).toList())).toList();
    }

    static String section(String type) {
        return switch (type) {
            case "SHORT_ANSWER" -> "SHORT";
            case "LONG_ANSWER" -> "LONG";
            default -> "CHOICE";
        };
    }

    /** A hand-written question for a paper, checked for the same basics as a quiz question. */
    private GenQuestion manual(Map<String, Object> raw) {
        GenQuestion q;
        try {
            q = mapper.convertValue(raw, GenQuestion.class);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unreadable question: " + e.getMessage());
        }
        if (q.getType() == null || !MarksPolicy.TYPES.contains(q.getType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown question type");
        }
        if (q.getQuestionText() == null || q.getQuestionText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every question needs text");
        }
        if (q.getMarks() == null || q.getMarks().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every question needs marks");
        }
        if (q.isWritten()) {
            if (q.getModelAnswer() == null || q.getModelAnswer().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A written question needs a model answer");
            }
        } else if (q.getOptions().size() < 2 || q.getCorrectAnswer().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A choice question needs at least two options and a correct answer");
        }
        q.setSource("MANUAL");
        return q;
    }

    private static List<GenQuestion.RubricItem> rescale(List<GenQuestion.RubricItem> rubric, BigDecimal marks) {
        if (rubric == null || rubric.isEmpty()) return rubric;
        BigDecimal sum = rubric.stream().map(GenQuestion.RubricItem::marks).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.signum() == 0) return rubric;
        List<GenQuestion.RubricItem> out = new ArrayList<>();
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < rubric.size(); i++) {
            GenQuestion.RubricItem r = rubric.get(i);
            BigDecimal m = i == rubric.size() - 1 ? marks.subtract(assigned)
                    : MarksPolicy.roundHalf(marks.multiply(r.marks()).divide(sum, 4, java.math.RoundingMode.HALF_UP));
            if (m.signum() <= 0) continue;
            assigned = assigned.add(m);
            out.add(new GenQuestion.RubricItem(r.criterion(), m.stripTrailingZeros(), r.keywords()));
        }
        return out;
    }

    private static String fileBase(String title) {
        String s = title.replaceAll("[^A-Za-z0-9 _-]", "").strip().replaceAll("\\s+", "-");
        return s.isEmpty() ? "paper" : s.length() > 60 ? s.substring(0, 60) : s;
    }

    static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
