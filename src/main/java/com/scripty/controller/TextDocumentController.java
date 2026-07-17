package com.scripty.controller;

import com.scripty.commandmodel.textdocument.TextDocumentCommandModel;
import com.scripty.dto.Block;
import com.scripty.dto.TextDocument;
import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.ProjectVersionService;
import com.scripty.service.ScriptImportException;
import com.scripty.service.SongBlockService;
import com.scripty.service.SongExportService;
import com.scripty.service.SongVersionService;
import com.scripty.service.TextDocumentService;
import com.scripty.service.UserService;
import com.scripty.viewmodel.textdocument.TextDocumentListViewModel;
import com.scripty.viewmodel.textdocument.TextDocumentViewModel;
import jakarta.validation.Valid;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.RequestHeader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping(value = "/project/documents")
public class TextDocumentController {

    @Autowired
    TextDocumentService textDocumentService;

    @Autowired
    SongBlockService songBlockService;

    @Autowired
    SongExportService songExportService;

    @Autowired
    ProjectVersionService projectVersionService;

    @Autowired
    SongVersionService songVersionService;

    @Autowired
    UserService userService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @RequestMapping(value = "/list")
    public String list(@RequestParam Integer projectId,
                       @RequestParam(required = false) String type,
                       Model model,
                       Principal principal) {
        String listType = normalizeListType(type);
        if (type == null || type.isBlank()) {
            return "redirect:/project/documents/songs?projectId=" + projectId;
        }
        return renderList(projectId, listType, model, principal);
    }

    @RequestMapping(value = "/songs")
    public String songs(@RequestParam Integer projectId, Model model, Principal principal) {
        return renderList(projectId, TextDocument.TYPE_SONG, model, principal);
    }

    @RequestMapping(value = "/notes")
    public String notes(@RequestParam Integer projectId, Model model, Principal principal) {
        return renderList(projectId, TextDocument.TYPE_NOTES, model, principal);
    }

    /** Alias for {@link #notes}; old bookmarks keep working. */
    @RequestMapping(value = "/drafts")
    public String drafts(@RequestParam Integer projectId, Model model, Principal principal) {
        return renderList(projectId, TextDocument.TYPE_NOTES, model, principal);
    }

    private String renderList(Integer projectId, String listType, Model model, Principal principal) {
        TextDocumentListViewModel viewModel = textDocumentService.getListViewModel(projectId, currentUser(principal));
        if (viewModel == null) {
            return "redirect:/project/list";
        }
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(listType);
        model.addAttribute("viewModel", viewModel);
        model.addAttribute("listType", isSong ? TextDocument.TYPE_SONG : TextDocument.TYPE_NOTES);
        model.addAttribute("isSongList", isSong);
        model.addAttribute("documents", isSong ? viewModel.getSongs() : viewModel.getDrafts());
        model.addAttribute("canEditScript", projectAccess.canEditScript(projectId, principal));
        return "project/documents/list";
    }

    @RequestMapping(value = "/create")
    public String create(@RequestParam Integer projectId,
                         @RequestParam(required = false, defaultValue = "SONG") String type,
                         Model model,
                         Principal principal) {
        User user = currentUser(principal);
        TextDocumentListViewModel listVm = textDocumentService.getListViewModel(projectId, user);
        if (listVm == null) {
            return "redirect:/project/list";
        }
        TextDocumentCommandModel commandModel = textDocumentService.getNewCommandModel(projectId, type);
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(commandModel.getDocumentType());
        // Songs are composed of lyric blocks, which need a persisted document to
        // attach to — create an empty song up front and open its block editor.
        if (isSong) {
            TextDocument created = textDocumentService.createEmptySong(projectId, user);
            if (created == null) {
                return "redirect:/project/list";
            }
            return "redirect:/project/documents/edit?id=" + created.getId();
        }
        model.addAttribute("projectId", projectId);
        model.addAttribute("projectTitle", listVm.getProjectTitle());
        model.addAttribute("commandModel", commandModel);
        model.addAttribute("isNew", true);
        model.addAttribute("isSong", isSong);
        model.addAttribute("listPath", listPath(isSong));
        model.addAttribute("canEditScript", projectAccess.canEditScript(projectId, principal));
        return "project/documents/edit";
    }

    @RequestMapping(value = "/edit")
    public String edit(@RequestParam Integer id, Model model, Principal principal) {
        User user = currentUser(principal);
        TextDocumentCommandModel commandModel = textDocumentService.getCommandModel(id, user);
        TextDocumentViewModel viewModel = textDocumentService.getViewModel(id, user);
        if (commandModel == null || viewModel == null) {
            return "redirect:/project/list";
        }
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(commandModel.getDocumentType());
        model.addAttribute("projectId", viewModel.getProjectId());
        model.addAttribute("projectTitle", viewModel.getProjectTitle());
        model.addAttribute("commandModel", commandModel);
        model.addAttribute("updatedAt", viewModel.getUpdatedAt());
        model.addAttribute("isNew", false);
        model.addAttribute("isSong", isSong);
        if (isSong) {
            model.addAttribute("blocks", songBlockService.getBlocks(id));
            model.addAttribute("focusBlockId", null);
        }
        model.addAttribute("listPath", listPath(isSong));
        model.addAttribute("canEditScript", projectAccess.canEditScript(viewModel.getProjectId(), user));
        return "project/documents/edit";
    }

    @RequestMapping(value = "/text")
    public ResponseEntity<String> text(@RequestParam Integer id, Principal principal) {
        TextDocumentViewModel viewModel = textDocumentService.getViewModel(id, currentUser(principal));
        if (viewModel == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String content = viewModel.getContent() == null ? "" : viewModel.getContent();
        String body = viewModel.getTitle() + "\n\n" + content;
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(body);
    }

    @RequestMapping(value = "/export", method = RequestMethod.GET)
    public ResponseEntity<byte[]> exportSong(@RequestParam Integer id,
                                             @RequestParam(required = false) String format,
                                             Principal principal) {
        SongExportService.SongExport export = songExportService.exportSong(
                id, SongExportService.parseFormat(format), currentUser(principal));
        return serve(export);
    }

    @RequestMapping(value = "/export-songs", method = RequestMethod.GET)
    public ResponseEntity<byte[]> exportSongs(@RequestParam Integer projectId,
                                              @RequestParam(required = false) String format,
                                              @RequestParam(required = false) List<Integer> ids,
                                              Principal principal) {
        SongExportService.SongExport export = songExportService.exportSongs(
                projectId, ids, SongExportService.parseFormat(format), currentUser(principal));
        return serve(export);
    }

    private ResponseEntity<byte[]> serve(SongExportService.SongExport export) {
        if (export == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(export.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + export.filename() + "\"")
                .body(export.content());
    }

    @RequestMapping(value = "/save", method = RequestMethod.POST)
    public Object save(@Valid @ModelAttribute("commandModel") TextDocumentCommandModel commandModel,
                       BindingResult bindingResult,
                       @RequestParam(defaultValue = "false") boolean stay,
                       @RequestHeader(value = "Accept", required = false) String acceptHeader,
                       Model model,
                       Principal principal) {
        User user = currentUser(principal);
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(commandModel.getDocumentType());
        boolean wantsJson = acceptHeader != null && acceptHeader.contains("application/json");

        if (bindingResult.hasErrors()) {
            if (wantsJson) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                List<String> errors = bindingResult.getFieldErrors().stream()
                        .map(e -> e.getField() + ": " + e.getDefaultMessage())
                        .collect(Collectors.toList());
                errorResponse.put("errors", errors);
                return ResponseEntity.badRequest().body(errorResponse);
            }
            TextDocumentListViewModel listVm = textDocumentService.getListViewModel(commandModel.getProjectId(), user);
            if (listVm == null) {
                return "redirect:/project/list";
            }
            model.addAttribute("projectId", commandModel.getProjectId());
            model.addAttribute("projectTitle", listVm.getProjectTitle());
            model.addAttribute("commandModel", commandModel);
            model.addAttribute("isNew", commandModel.getId() == null);
            model.addAttribute("isSong", isSong);
            model.addAttribute("listPath", listPath(isSong));
            return "project/documents/edit";
        }

        TextDocument saved = textDocumentService.save(commandModel, user);
        if (saved == null) {
            if (wantsJson) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Project or user not found");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
            }
            return "redirect:/project/list";
        }
        if (commandModel.getId() != null
                && textDocumentService.syncInsertedBlocks(saved.getId(), user)) {
            projectVersionService.autoSaveVersion(commandModel.getProjectId());
        }
        // The song snapshot carries the title, so a rename from the editor is a
        // change worth capturing; block edits auto-save via SongBlockController.
        if (isSong && commandModel.getId() != null) {
            songVersionService.autoSaveVersion(saved.getId());
        }

        if (wantsJson) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", saved.getId());
            response.put("updatedAt", saved.getUpdatedAt() != null 
                    ? saved.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) 
                    : LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return ResponseEntity.ok(response);
        }

        if (stay) {
            return "redirect:/project/documents/edit?id=" + saved.getId();
        }
        return "redirect:" + listUrl(commandModel.getProjectId(), isSong);
    }

    @RequestMapping(value = "/rename", method = RequestMethod.POST)
    public String rename(@RequestParam Integer id,
                         @RequestParam Integer projectId,
                         @RequestParam(required = false) String type,
                         @RequestParam String title,
                         Principal principal) {
        textDocumentService.rename(id, projectId, title, currentUser(principal));
        return "redirect:" + listUrl(projectId, TextDocument.TYPE_SONG.equalsIgnoreCase(normalizeListType(type)));
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public String delete(@RequestParam Integer id,
                         @RequestParam Integer projectId,
                         @RequestParam(required = false) String type,
                         Principal principal,
                         RedirectAttributes redirectAttributes) {
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(normalizeListType(type));
        TextDocument deleted = textDocumentService.delete(id, projectId, currentUser(principal));
        if (deleted != null) {
            redirectAttributes.addFlashAttribute(
                    "documentTrashMessage",
                    "Moved \"" + deleted.getTitle() + "\" to the trash.");
        }
        return "redirect:" + listUrl(projectId, isSong);
    }

    @RequestMapping(value = "/trash")
    public String trash(@RequestParam Integer projectId,
                        @RequestParam(required = false) String type,
                        Model model,
                        Principal principal) {
        TextDocumentListViewModel viewModel = textDocumentService.getTrashViewModel(projectId, currentUser(principal));
        if (viewModel == null) {
            return "redirect:/project/list";
        }
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(normalizeListType(type));
        model.addAttribute("viewModel", viewModel);
        model.addAttribute("listType", isSong ? TextDocument.TYPE_SONG : TextDocument.TYPE_NOTES);
        model.addAttribute("isSongList", isSong);
        model.addAttribute("documents", isSong ? viewModel.getSongs() : viewModel.getDrafts());
        model.addAttribute("otherCount", isSong ? viewModel.getDrafts().size() : viewModel.getSongs().size());
        return "project/documents/trash";
    }

    @RequestMapping(value = "/restore", method = RequestMethod.POST)
    public String restore(@RequestParam Integer id,
                          @RequestParam Integer projectId,
                          @RequestParam(required = false) String type,
                          Principal principal,
                          RedirectAttributes redirectAttributes) {
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(normalizeListType(type));
        TextDocument restored = textDocumentService.restore(id, projectId, currentUser(principal));
        if (restored == null) {
            redirectAttributes.addFlashAttribute(
                    "documentTrashMessage",
                    "Could not restore that item. It may already have been restored or purged.");
            return "redirect:" + trashUrl(projectId, isSong);
        }
        redirectAttributes.addFlashAttribute(
                "documentTrashMessage",
                "Restored \"" + restored.getTitle() + "\".");
        return "redirect:" + listUrl(projectId, isSong);
    }

    @RequestMapping(value = "/purge", method = RequestMethod.POST)
    public String purge(@RequestParam Integer id,
                        @RequestParam Integer projectId,
                        @RequestParam(required = false) String type,
                        Principal principal,
                        RedirectAttributes redirectAttributes) {
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(normalizeListType(type));
        boolean purged = textDocumentService.purge(id, projectId, currentUser(principal));
        redirectAttributes.addFlashAttribute(
                "documentTrashMessage",
                purged ? "Deleted permanently." : "Could not delete that item.");
        return "redirect:" + trashUrl(projectId, isSong);
    }

    @RequestMapping(value = "/share-email", method = RequestMethod.POST)
    public String shareEmail(@RequestParam(name = "id", required = false) List<Integer> ids,
                             @RequestParam Integer projectId,
                             @RequestParam String email,
                             Principal principal,
                             RedirectAttributes redirectAttributes) {
        List<TextDocument> shared = textDocumentService.shareSongsByEmail(ids, email, currentUser(principal));
        if (shared.size() == 1) {
            redirectAttributes.addFlashAttribute(
                    "documentShareMessage",
                    "Emailed \"" + shared.get(0).getTitle() + "\" to " + email.trim() + ".");
        } else if (!shared.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "documentShareMessage",
                    "Emailed " + shared.size() + " songs to " + email.trim() + ".");
        } else {
            redirectAttributes.addFlashAttribute(
                    "documentShareMessage",
                    "Could not email those songs. Check the address and try again.");
        }
        return "redirect:" + listUrl(projectId, true);
    }

    @RequestMapping(value = "/insert", method = RequestMethod.POST)
    public String insert(@RequestParam Integer id,
                         @RequestParam(required = false) Integer afterBlockId,
                         @RequestParam(required = false) String asType,
                         Principal principal,
                         RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        TextDocumentViewModel viewModel = textDocumentService.getViewModel(id, user);
        if (viewModel == null) {
            return "redirect:/project/list";
        }
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(viewModel.getDocumentType());
        if (!projectAccess.canEditScript(viewModel.getProjectId(), user)) {
            return "redirect:" + listUrl(viewModel.getProjectId(), isSong);
        }

        List<Block> created = textDocumentService.insertIntoScript(id, afterBlockId, asType, user);
        if (!created.isEmpty()) {
            projectVersionService.autoSaveVersion(viewModel.getProjectId());
            redirectAttributes.addFlashAttribute(
                    "documentInsertMessage",
                    "Inserted \"" + viewModel.getTitle() + "\" as "
                            + created.size() + (created.size() == 1 ? " block" : " blocks") + ".");
        } else {
            redirectAttributes.addFlashAttribute(
                    "documentInsertMessage",
                    "Nothing to insert from \"" + viewModel.getTitle() + "\".");
        }

        String redirect = "redirect:/project/show?id=" + viewModel.getProjectId();
        if (!created.isEmpty()) {
            redirect += "#block-" + created.get(0).getId();
        }
        return redirect;
    }

    @RequestMapping(value = "/import", method = RequestMethod.POST)
    public String importFile(@RequestParam Integer projectId,
                             @RequestParam(defaultValue = "SONG") String type,
                             @RequestParam("file") MultipartFile file,
                             Principal principal,
                             RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(normalizeListType(type));
        try {
            TextDocument saved = textDocumentService.importFile(projectId, type, file, user);
            if (saved == null) {
                redirectAttributes.addFlashAttribute(
                        "documentImportMessage",
                        "Could not import that file. Check access and try a .txt, .fountain, .docx, .doc, .fdx, .epub, or .pdf file.");
                return "redirect:" + listUrl(projectId, isSong);
            }
            redirectAttributes.addFlashAttribute(
                    "documentImportMessage",
                    "Imported \"" + saved.getTitle() + "\".");
            return "redirect:/project/documents/edit?id=" + saved.getId();
        } catch (ScriptImportException e) {
            redirectAttributes.addFlashAttribute("documentImportMessage", e.getUserMessage());
            return "redirect:" + listUrl(projectId, isSong);
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute(
                    "documentImportMessage",
                    "Could not import that file. Check access and try a .txt, .fountain, .docx, .doc, .fdx, .epub, or .pdf file.");
            return "redirect:" + listUrl(projectId, isSong);
        }
    }

    private static String normalizeListType(String type) {
        if (type == null || type.isBlank()) {
            return TextDocument.TYPE_SONG;
        }
        if ("DRAFT".equalsIgnoreCase(type)
                || "DRAFTS".equalsIgnoreCase(type)
                || TextDocument.TYPE_NOTES.equalsIgnoreCase(type)
                || TextDocument.TYPE_OTHER.equalsIgnoreCase(type)) {
            return TextDocument.TYPE_NOTES;
        }
        return TextDocument.TYPE_SONG;
    }

    private static String listPath(boolean isSong) {
        return isSong ? "/project/documents/songs" : "/project/documents/notes";
    }

    private static String listUrl(Integer projectId, boolean isSong) {
        return listPath(isSong) + "?projectId=" + projectId;
    }

    private static String trashUrl(Integer projectId, boolean isSong) {
        return "/project/documents/trash?projectId=" + projectId
                + "&type=" + (isSong ? TextDocument.TYPE_SONG : TextDocument.TYPE_NOTES);
    }

    private User currentUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userService.readByUsername(principal.getName());
    }
}
