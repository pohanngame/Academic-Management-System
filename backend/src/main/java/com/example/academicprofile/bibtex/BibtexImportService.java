package com.example.academicprofile.bibtex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.academicprofile.achievement.Paper;
import com.example.academicprofile.achievement.PaperMapper;
import com.example.academicprofile.common.exception.BusinessException;

@Service
public class BibtexImportService {

    private static final long MAX_BIB_FILE_SIZE = 2L * 1024 * 1024;
    private static final String STATUS_PARSED = "PARSED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_IGNORED = "IGNORED";
    private static final String STATUS_IMPORTED = "IMPORTED";
    private static final String DUPLICATE_NONE = "NONE";
    private static final String DUPLICATE_POSSIBLE = "POSSIBLE";

    private final BibtexImportTaskMapper taskMapper;
    private final BibtexImportItemMapper itemMapper;
    private final BibtexParserService parserService;
    private final PaperMapper paperMapper;

    public BibtexImportService(
            BibtexImportTaskMapper taskMapper,
            BibtexImportItemMapper itemMapper,
            BibtexParserService parserService,
            PaperMapper paperMapper) {
        this.taskMapper = taskMapper;
        this.itemMapper = itemMapper;
        this.parserService = parserService;
        this.paperMapper = paperMapper;
    }

    @Transactional
    public BibtexImportTaskResponse importText(Long teacherId, String content) {
        return createTask(teacherId, "TEXT", null, content);
    }

    @Transactional
    public BibtexImportTaskResponse importFile(Long teacherId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("BibTeX file is required");
        }
        if (file.getSize() > MAX_BIB_FILE_SIZE) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "BibTeX file size exceeds 2MB");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".bib")) {
            throw new BusinessException("Only .bib files are supported");
        }
        try {
            return createTask(teacherId, "FILE", originalName, new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read BibTeX file");
        }
    }

    public List<BibtexImportTaskResponse> listTasks(Long teacherId) {
        return taskMapper.selectList(new LambdaQueryWrapper<BibtexImportTask>()
                        .eq(BibtexImportTask::getTeacherId, teacherId)
                        .orderByDesc(BibtexImportTask::getId))
                .stream()
                .map(BibtexImportTaskResponse::from)
                .toList();
    }

    public BibtexImportTaskResponse getTask(Long teacherId, Long taskId) {
        BibtexImportTask task = getOwnedTask(teacherId, taskId);
        List<BibtexImportItemResponse> items = listItems(teacherId, taskId).stream()
                .map(BibtexImportItemResponse::from)
                .toList();
        return BibtexImportTaskResponse.from(task, items);
    }

    @Transactional
    public BibtexImportItemResponse updateItem(Long teacherId, Long itemId, BibtexImportItemRequest request) {
        BibtexImportItem item = getOwnedItem(teacherId, itemId);
        if (STATUS_IMPORTED.equals(item.getStatus())) {
            throw new BusinessException("Imported item cannot be edited");
        }
        applyRequest(item, request);
        if (STATUS_IGNORED.equals(item.getStatus())) {
            item.setStatus(STATUS_PARSED);
        }
        validateAndDetectDuplicate(teacherId, item);
        itemMapper.updateById(item);
        refreshTaskCounts(item.getTaskId(), teacherId);
        return BibtexImportItemResponse.from(item);
    }

    @Transactional
    public void ignoreItem(Long teacherId, Long itemId) {
        BibtexImportItem item = getOwnedItem(teacherId, itemId);
        if (STATUS_IMPORTED.equals(item.getStatus())) {
            throw new BusinessException("Imported item cannot be ignored");
        }
        item.setStatus(STATUS_IGNORED);
        item.setSelected(Boolean.FALSE);
        itemMapper.updateById(item);
        refreshTaskCounts(item.getTaskId(), teacherId);
    }

    @Transactional
    public BibtexConfirmResponse confirmTask(Long teacherId, Long taskId, BibtexConfirmRequest request) {
        getOwnedTask(teacherId, taskId);
        Set<Long> selectedIds = request.itemIds() == null ? Set.of() : new HashSet<>(request.itemIds());
        List<BibtexImportItem> items = listItems(teacherId, taskId).stream()
                .filter(item -> selectedIds.isEmpty() || selectedIds.contains(item.getId()))
                .filter(item -> Boolean.TRUE.equals(item.getSelected()))
                .filter(item -> STATUS_PARSED.equals(item.getStatus()) || STATUS_FAILED.equals(item.getStatus()))
                .toList();

        int imported = 0;
        int skipped = 0;
        boolean forceDuplicates = Boolean.TRUE.equals(request.forceDuplicates());
        for (BibtexImportItem item : items) {
            validateAndDetectDuplicate(teacherId, item);
            if (STATUS_FAILED.equals(item.getStatus())) {
                itemMapper.updateById(item);
                skipped++;
                continue;
            }
            if (DUPLICATE_POSSIBLE.equals(item.getDuplicateStatus()) && !forceDuplicates) {
                itemMapper.updateById(item);
                skipped++;
                continue;
            }
            Paper paper = toPaper(teacherId, item);
            paperMapper.insert(paper);
            item.setCreatedPaperId(paper.getId());
            item.setStatus(STATUS_IMPORTED);
            item.setSelected(Boolean.FALSE);
            itemMapper.updateById(item);
            imported++;
        }
        refreshTaskCounts(taskId, teacherId);
        return new BibtexConfirmResponse(imported, skipped);
    }

    private BibtexImportTaskResponse createTask(Long teacherId, String sourceType, String fileName, String content) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("BibTeX content is required");
        }
        BibtexImportTask task = new BibtexImportTask();
        task.setTeacherId(teacherId);
        task.setSourceType(sourceType);
        task.setFileName(blankToNull(fileName));
        task.setStatus("PARSING");
        task.setTotalCount(0);
        task.setSuccessCount(0);
        task.setFailedCount(0);
        taskMapper.insert(task);

        List<BibtexParsedEntry> parsedEntries = parserService.parse(content);
        for (BibtexParsedEntry parsed : parsedEntries) {
            BibtexImportItem item = toItem(task.getId(), teacherId, parsed);
            validateAndDetectDuplicate(teacherId, item);
            itemMapper.insert(item);
        }
        refreshTaskCounts(task.getId(), teacherId);
        return getTask(teacherId, task.getId());
    }

    private BibtexImportItem toItem(Long taskId, Long teacherId, BibtexParsedEntry parsed) {
        BibtexImportItem item = new BibtexImportItem();
        item.setTaskId(taskId);
        item.setTeacherId(teacherId);
        item.setStatus(parsed.failed() ? STATUS_FAILED : STATUS_PARSED);
        item.setErrorMessage(parsed.errorMessage());
        item.setDuplicateStatus(DUPLICATE_NONE);
        item.setRawEntry(limit(parsed.rawEntry(), 5000));
        item.setEntryType(limit(parsed.entryType(), 64));
        item.setBibKey(limit(parsed.bibKey(), 255));
        item.setTitle(limit(parsed.title(), 500));
        item.setAuthors(limit(parsed.authors(), 5000));
        item.setJournal(limit(parsed.journal(), 255));
        item.setBooktitle(limit(parsed.booktitle(), 255));
        item.setYear(parsed.year());
        item.setDoi(normalizeDoi(limit(parsed.doi(), 255)));
        item.setVolume(limit(parsed.volume(), 64));
        item.setNumber(limit(parsed.number(), 64));
        item.setPages(limit(parsed.pages(), 64));
        item.setPublisher(limit(parsed.publisher(), 255));
        item.setUrl(limit(parsed.url(), 1024));
        item.setAbstractText(limit(parsed.abstractText(), 20000));
        item.setKeywords(limit(parsed.keywords(), 1000));
        item.setSelected(!parsed.failed());
        return item;
    }

    private void applyRequest(BibtexImportItem item, BibtexImportItemRequest request) {
        item.setEntryType(blankToNull(limit(request.entryType(), 64)));
        item.setBibKey(blankToNull(limit(request.bibKey(), 255)));
        item.setTitle(blankToNull(limit(request.title(), 500)));
        item.setAuthors(blankToNull(limit(request.authors(), 5000)));
        item.setJournal(blankToNull(limit(request.journal(), 255)));
        item.setBooktitle(blankToNull(limit(request.booktitle(), 255)));
        item.setYear(request.year());
        item.setDoi(normalizeDoi(limit(request.doi(), 255)));
        item.setVolume(blankToNull(limit(request.volume(), 64)));
        item.setNumber(blankToNull(limit(request.number(), 64)));
        item.setPages(blankToNull(limit(request.pages(), 64)));
        item.setPublisher(blankToNull(limit(request.publisher(), 255)));
        item.setUrl(blankToNull(limit(request.url(), 1024)));
        item.setAbstractText(blankToNull(limit(request.abstractText(), 20000)));
        item.setKeywords(blankToNull(limit(request.keywords(), 1000)));
        item.setSelected(request.selected() == null || Boolean.TRUE.equals(request.selected()));
    }

    private void validateAndDetectDuplicate(Long teacherId, BibtexImportItem item) {
        if (!StringUtils.hasText(item.getTitle())) {
            item.setStatus(STATUS_FAILED);
            item.setErrorMessage("Missing required field: title");
            item.setSelected(Boolean.FALSE);
        } else if (!STATUS_IGNORED.equals(item.getStatus()) && !STATUS_IMPORTED.equals(item.getStatus())) {
            item.setStatus(STATUS_PARSED);
            item.setErrorMessage(null);
            if (item.getSelected() == null) {
                item.setSelected(Boolean.TRUE);
            }
        }

        Paper duplicate = findDuplicate(teacherId, item);
        if (duplicate == null) {
            item.setDuplicateStatus(DUPLICATE_NONE);
            item.setDuplicatePaperId(null);
        } else {
            item.setDuplicateStatus(DUPLICATE_POSSIBLE);
            item.setDuplicatePaperId(duplicate.getId());
        }
    }

    private Paper findDuplicate(Long teacherId, BibtexImportItem item) {
        if (StringUtils.hasText(item.getDoi())) {
            Paper byDoi = paperMapper.selectOne(new LambdaQueryWrapper<Paper>()
                    .eq(Paper::getTeacherId, teacherId)
                    .eq(Paper::getDoi, item.getDoi())
                    .last("LIMIT 1"));
            if (byDoi != null) {
                return byDoi;
            }
        }

        if (!StringUtils.hasText(item.getTitle()) || item.getYear() == null) {
            return null;
        }
        String normalizedTitle = normalizeTitle(item.getTitle());
        String firstAuthor = firstAuthor(item.getAuthors());
        return paperMapper.selectList(new LambdaQueryWrapper<Paper>()
                        .eq(Paper::getTeacherId, teacherId)
                        .eq(Paper::getPublishYear, item.getYear()))
                .stream()
                .filter(paper -> normalizedTitle.equals(normalizeTitle(paper.getTitle())))
                .filter(paper -> {
                    if (!StringUtils.hasText(firstAuthor)) {
                        return true;
                    }
                    return firstAuthor.equals(firstAuthor(paper.getAuthors()));
                })
                .findFirst()
                .orElse(null);
    }

    private Paper toPaper(Long teacherId, BibtexImportItem item) {
        Paper paper = new Paper();
        paper.setTeacherId(teacherId);
        paper.setTitle(item.getTitle());
        paper.setAuthors(blankToNull(item.getAuthors()));
        paper.setPublicationName(firstText(item.getJournal(), item.getBooktitle()));
        paper.setPublicationType(blankToNull(item.getEntryType()));
        paper.setPublishYear(item.getYear());
        paper.setDoi(blankToNull(item.getDoi()));
        paper.setVolume(blankToNull(item.getVolume()));
        paper.setIssue(blankToNull(item.getNumber()));
        paper.setPages(blankToNull(item.getPages()));
        paper.setPublisher(blankToNull(item.getPublisher()));
        paper.setUrl(blankToNull(item.getUrl()));
        paper.setAbstractText(blankToNull(item.getAbstractText()));
        paper.setKeywords(blankToNull(item.getKeywords()));
        paper.setSortOrder(0);
        paper.setIsPublic(Boolean.TRUE);
        return paper;
    }

    private void refreshTaskCounts(Long taskId, Long teacherId) {
        BibtexImportTask task = getOwnedTask(teacherId, taskId);
        List<BibtexImportItem> items = listItems(teacherId, taskId);
        int total = items.size();
        int failed = (int) items.stream().filter(item -> STATUS_FAILED.equals(item.getStatus())).count();
        int imported = (int) items.stream().filter(item -> STATUS_IMPORTED.equals(item.getStatus())).count();
        int ignored = (int) items.stream().filter(item -> STATUS_IGNORED.equals(item.getStatus())).count();
        task.setTotalCount(total);
        task.setFailedCount(failed);
        task.setSuccessCount(total - failed - ignored);
        task.setStatus(imported > 0 && imported + failed + ignored >= total ? "CONFIRMED" : "PARSED");
        taskMapper.updateById(task);
    }

    private BibtexImportTask getOwnedTask(Long teacherId, Long taskId) {
        BibtexImportTask task = taskMapper.selectById(taskId);
        if (task == null || !teacherId.equals(task.getTeacherId())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "BibTeX import task not found");
        }
        return task;
    }

    private BibtexImportItem getOwnedItem(Long teacherId, Long itemId) {
        BibtexImportItem item = itemMapper.selectById(itemId);
        if (item == null || !teacherId.equals(item.getTeacherId())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "BibTeX import item not found");
        }
        return item;
    }

    private List<BibtexImportItem> listItems(Long teacherId, Long taskId) {
        return itemMapper.selectList(new LambdaQueryWrapper<BibtexImportItem>()
                .eq(BibtexImportItem::getTeacherId, teacherId)
                .eq(BibtexImportItem::getTaskId, taskId)
                .orderByAsc(BibtexImportItem::getId));
    }

    private String normalizeDoi(String doi) {
        if (!StringUtils.hasText(doi)) {
            return null;
        }
        return doi.trim()
                .replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return "";
        }
        return title.toLowerCase(Locale.ROOT)
                .replaceAll("[{}]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String firstAuthor(String authors) {
        if (!StringUtils.hasText(authors)) {
            return "";
        }
        String first = authors.split("(?i)\\s+and\\s+|;|,")[0];
        return first.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return blankToNull(second);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
