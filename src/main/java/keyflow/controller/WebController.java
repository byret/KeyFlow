package keyflow.controller;

import jakarta.servlet.http.HttpSession;
import keyflow.model.*;
import keyflow.service.ExportService;
import keyflow.service.MessageCompareService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Controller
public class WebController {

    private static final String SESSION_RESULT_KEY = "comparisonResult";
    private static final String SESSION_INPUTS_KEY = "storedInputs";

    private final MessageCompareService messageCompareService;
    private final ExportService exportService;

    public WebController(MessageCompareService messageCompareService, ExportService exportService) {
        this.messageCompareService = messageCompareService;
        this.exportService = exportService;
    }

    @GetMapping("/")
    public String index(HttpSession session, Model model) {
        populateCommonModel(model, session);
        return "index";
    }

    @PostMapping("/compare")
    public String compare(@RequestParam(name = "file1", required = false) MultipartFile file1,
                          @RequestParam(name = "file2", required = false) MultipartFile file2,
                          @RequestParam(name = "prefix", defaultValue = "eventmessage") String prefix,
                          @RequestParam(name = "transformationRules", defaultValue = "") String transformationRules,
                          @RequestParam(name = "mergeIgnoreTerms", defaultValue = "") String mergeIgnoreTerms,
                          @RequestParam(name = "ignoreWhitespace", defaultValue = "false") boolean ignoreWhitespace,
                          @RequestParam(name = "ignoreCase", defaultValue = "false") boolean ignoreCase,
                          @RequestParam(name = "mergeStrategy", defaultValue = "PREFER_SECOND") MergeStrategy mergeStrategy,
                          HttpSession session,
                          Model model) {
        try {
            StoredInputs existingInputs = (StoredInputs) session.getAttribute(SESSION_INPUTS_KEY);
            StoredUpload storedFile1 = resolveUpload(file1, existingInputs != null ? existingInputs.file1() : null);
            StoredUpload storedFile2 = resolveUpload(file2, existingInputs != null ? existingInputs.file2() : null);

            if (storedFile1 == null || storedFile2 == null) {
                populateCommonModel(model, session);
                return "index";
            }

            CompareOptions options = new CompareOptions(prefix, transformationRules, mergeIgnoreTerms, ignoreWhitespace, ignoreCase, mergeStrategy);
            ComparisonResult result = messageCompareService.compare(storedFile1.content(), storedFile2.content(), options);

            session.setAttribute(SESSION_INPUTS_KEY, new StoredInputs(storedFile1, storedFile2));
            session.setAttribute(SESSION_RESULT_KEY, result);

            populateCommonModel(model, session);
            return "index";
        } catch (Exception e) {
            populateCommonModel(model, session);
            model.addAttribute("error", "Could not compare files: " + e.getMessage());
            return "index";
        }
    }

    @PostMapping("/clear-result")
    public String clearResult(HttpSession session) {
        session.removeAttribute(SESSION_RESULT_KEY);
        return "redirect:/";
    }

    @GetMapping("/download/{section}/{format}")
    public ResponseEntity<byte[]> download(@PathVariable String section,
                                           @PathVariable ExportFormat format,
                                           @RequestParam(name = "differentViewMode", defaultValue = "PRETTY_DIFF_TEXT") DifferentViewMode differentViewMode,
                                           HttpSession session) throws IOException {
        ComparisonResult result = getResultFromSession(session);
        byte[] bytes = exportService.exportSection(result, section, format, differentViewMode);

        String fileName = section.equals("workbook")
                ? "comparison.xlsx"
                : section + (section.equals("different") && differentViewMode == DifferentViewMode.PRETTY_DIFF_TEXT ? "-pretty" : "") + "." + format.getExtension();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .contentType(MediaType.parseMediaType(format.getContentType()))
                .body(bytes);
    }

    @PostMapping("/download/merged/{format}/edited")
    public ResponseEntity<byte[]> downloadEditedMerged(@PathVariable ExportFormat format,
                                                       @RequestParam(name = "editedKeys") List<String> editedKeys,
                                                       @RequestParam(name = "editedValues") List<String> editedValues,
                                                       HttpSession session) throws IOException {
        ComparisonResult result = getResultFromSession(session);
        List<ComparisonRow> editedRows = mergeEditedRows(result.mergedRows(), editedKeys, editedValues);
        ComparisonResult updatedResult = new ComparisonResult(
                result.firstCount(),
                result.secondCount(),
                result.missingInSecondCount(),
                result.missingInFirstCount(),
                result.differentCount(),
                result.options(),
                editedRows,
                result.missingInSecondRows(),
                result.missingInFirstRows(),
                result.differentRows()
        );
        session.setAttribute(SESSION_RESULT_KEY, updatedResult);

        byte[] bytes = exportService.exportMerged(updatedResult, format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("merged-edited." + format.getExtension()).build().toString())
                .contentType(MediaType.parseMediaType(format.getContentType()))
                .body(bytes);
    }

    @GetMapping("/download/all.zip")
    public ResponseEntity<byte[]> downloadAll(HttpSession session) throws IOException {
        ComparisonResult result = getResultFromSession(session);
        byte[] bytes = exportService.exportAllAsZip(result);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("comparison-results.zip").build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    private StoredUpload resolveUpload(MultipartFile multipartFile, StoredUpload existing) throws IOException {
        if (multipartFile != null && !multipartFile.isEmpty()) {
            return new StoredUpload(multipartFile.getOriginalFilename(), multipartFile.getBytes());
        }
        return existing;
    }

    private ComparisonResult getResultFromSession(HttpSession session) {
        Object value = session.getAttribute(SESSION_RESULT_KEY);
        if (value instanceof ComparisonResult result) {
            return result;
        }
        throw new IllegalStateException("No comparison result found in session. Compare files first.");
    }


    private List<ComparisonRow> mergeEditedRows(List<ComparisonRow> originalRows, List<String> editedKeys, List<String> editedValues) {
        List<ComparisonRow> result = new ArrayList<>();
        int rowCount = Math.min(editedKeys.size(), editedValues.size());
        for (int i = 0; i < rowCount; i++) {
            result.add(new ComparisonRow(editedKeys.get(i), editedValues.get(i)));
        }
        if (rowCount == 0 && !originalRows.isEmpty()) {
            return new ArrayList<>(originalRows);
        }
        return result;
    }

    private void populateCommonModel(Model model, HttpSession session) {
        ComparisonResult result = (ComparisonResult) session.getAttribute(SESSION_RESULT_KEY);
        StoredInputs inputs = (StoredInputs) session.getAttribute(SESSION_INPUTS_KEY);

        CompareOptions options = result != null
                ? result.options()
                : new CompareOptions("eventmessage", "", "", false, false, MergeStrategy.PREFER_SECOND);

        model.addAttribute("result", result);
        model.addAttribute("prefix", options.prefix());
        model.addAttribute("transformationRules", options.transformationRules());
        model.addAttribute("mergeIgnoreTerms", options.mergeIgnoreTerms());
        model.addAttribute("ignoreWhitespace", options.ignoreWhitespace());
        model.addAttribute("ignoreCase", options.ignoreCase());
        model.addAttribute("mergeStrategy", options.mergeStrategy());
        model.addAttribute("mergeStrategies", MergeStrategy.values());
        model.addAttribute("ruleModes", RuleMode.values());
        model.addAttribute("displayRules", messageCompareService.parseRulesForDisplay(options.transformationRules()));
        model.addAttribute("differentViewModes", DifferentViewMode.values());
        model.addAttribute("exportFormats", ExportFormat.values());

        if (inputs != null) {
            model.addAttribute("storedFile1Name", inputs.file1() != null ? inputs.file1().fileName() : null);
            model.addAttribute("storedFile2Name", inputs.file2() != null ? inputs.file2().fileName() : null);
        }
    }
}
