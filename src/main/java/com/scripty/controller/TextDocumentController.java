package com.scripty.controller;

import com.scripty.commandmodel.textdocument.TextDocumentCommandModel;
import com.scripty.dto.Block;
import com.scripty.dto.SongEdition;
import com.scripty.dto.TextDocument;
import com.scripty.dto.TextDocumentFolder;
import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.DocumentFolderException;
import com.scripty.service.ProjectVersionService;
import com.scripty.service.ScriptImportException;
import com.scripty.service.SongBlockService;
import com.scripty.service.SongEditionService;
import com.scripty.service.SongExportService;
import com.scripty.service.SongVersionService;
import com.scripty.service.TextDocumentFolderService;
import com.scripty.service.TextDocumentService;
import com.scripty.service.UserService;
import com.scripty.viewmodel.textdocument.NoteWorkspacePaneViewModel;
import com.scripty.viewmodel.textdocument.SongWorkspacePaneViewModel;
import com.scripty.viewmodel.textdocument.TextDocumentListViewModel;
import com.scripty.viewmodel.textdocument.TextDocumentViewModel;
import jakarta.validation.Valid;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
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
    TextDocumentFolderService textDocumentFolderService;

    @Autowired
    SongBlockService songBlockService;

    @Autowired
    SongExportService songExportService;

    @Autowired
    ProjectVersionService projectVersionService;

    @Autowired
    SongVersionService songVersionService;

    @Autowired
    SongEditionService songEditionService;

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

    /**
     * Every song — or every note — in the project on one page, so a run of
     * edits that spans several of them does not mean bouncing back to the list
     * between each.
     *
     * <p>Two renders rather than one, because the two kinds are genuinely
     * different underneath: a song pane is a stacked block editor over an
     * edition, and a note pane is a title and a textarea. Editing goes through
     * the endpoints each kind already uses — /song/block/* for lyrics, the
     * ordinary document save for notes — so this route only assembles the
     * initial render.
     *
     * <p>{@code type} is optional and defaults to songs: this route was the
     * songs workspace before notes had one, and the links that still point at
     * it without a type mean what they always did.
     */
    @RequestMapping(value = "/songs/workspace")
    public String songsWorkspace(@RequestParam Integer projectId,
                                 @RequestParam(required = false) String type,
                                 Model model, Principal principal) {
        if (TextDocument.TYPE_NOTES.equalsIgnoreCase(type)) {
            return notesWorkspace(projectId, model, principal);
        }
        User user = currentUser(principal);
        TextDocumentListViewModel viewModel = textDocumentService.getListViewModel(projectId, user);
        if (viewModel == null) {
            return "redirect:/project/list";
        }
        boolean canEditScript = projectAccess.canEditScript(projectId, principal);
        List<SongWorkspacePaneViewModel> panes = new ArrayList<>();
        for (TextDocumentViewModel song : viewModel.getSongs()) {
            SongWorkspacePaneViewModel pane = new SongWorkspacePaneViewModel();
            pane.setId(song.getId());
            pane.setTitle(song.getTitle());
            pane.setUpdatedAt(song.getUpdatedAt());
            // Same edition rule as the single-song editor: writers see their
            // default version, everyone else is pinned to the published one.
            songEditionService.ensureDefaultEdition(song.getId());
            SongEdition active = songEditionService.resolveForAccess(song.getId(), null, canEditScript);
            Integer activeId = active != null ? active.getId() : null;
            pane.setEditionId(activeId);
            pane.setEditionName(active != null ? active.getName() : null);
            pane.setBlocks(songBlockService.getBlocks(song.getId(), activeId));
            panes.add(pane);
        }
        model.addAttribute("projectId", projectId);
        model.addAttribute("projectTitle", viewModel.getProjectTitle());
        model.addAttribute("panes", panes);
        model.addAttribute("canEditScript", canEditScript);
        return "project/documents/songsWorkspace";
    }

    /**
     * Every note in the project on one page.
     *
     * <p>Unlike the songs workspace this needs the documents' full content, and
     * the list view model only carries previews — so each pane is fetched in
     * turn. That is one query per note where the songs workspace is one per
     * song for its blocks, so the shape of the cost is the same.
     */
    private String notesWorkspace(Integer projectId, Model model, Principal principal) {
        User user = currentUser(principal);
        TextDocumentListViewModel viewModel = textDocumentService.getListViewModel(projectId, user);
        if (viewModel == null) {
            return "redirect:/project/list";
        }
        List<NoteWorkspacePaneViewModel> panes = new ArrayList<>();
        for (TextDocumentViewModel note : viewModel.getDrafts()) {
            TextDocumentViewModel full = textDocumentService.getViewModel(note.getId(), user);
            NoteWorkspacePaneViewModel pane = new NoteWorkspacePaneViewModel();
            pane.setId(note.getId());
            pane.setTitle(note.getTitle());
            pane.setUpdatedAt(note.getUpdatedAt());
            pane.setContent(full != null ? full.getContent() : null);
            panes.add(pane);
        }
        model.addAttribute("projectId", projectId);
        model.addAttribute("projectTitle", viewModel.getProjectTitle());
        model.addAttribute("panes", panes);
        model.addAttribute("canEditScript", projectAccess.canEditScript(projectId, principal));
        return "project/documents/notesWorkspace";
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
        // The same documents again, gathered under this list's folders. The
        // flat `documents` above stays what the page's counts, exports and
        // select-all read, so nothing that predates folders has to know about
        // them.
        model.addAttribute("folders", isSong ? viewModel.getSongFolders() : viewModel.getDraftFolders());
        model.addAttribute("unfiledDocuments",
                isSong ? viewModel.getUnfiledSongs() : viewModel.getUnfiledDrafts());
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
        // Nothing being written for the first time is archived. Bound anyway so
        // the strip below reads one attribute on both paths rather than relying
        // on a missing one being falsy.
        model.addAttribute("isArchived", false);
        model.addAttribute("listPath", listPath(isSong));
        model.addAttribute("canEditScript", projectAccess.canEditScript(projectId, principal));
        return "project/documents/edit";
    }

    @RequestMapping(value = "/edit")
    public String edit(@RequestParam Integer id,
                       @RequestParam(required = false) Integer editionId,
                       Model model,
                       Principal principal) {
        User user = currentUser(principal);
        TextDocumentCommandModel commandModel = textDocumentService.getCommandModel(id, user);
        TextDocumentViewModel viewModel = textDocumentService.getViewModel(id, user);
        if (commandModel == null || viewModel == null) {
            return "redirect:/project/list";
        }
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(commandModel.getDocumentType());
        boolean canEditScript = projectAccess.canEditScript(viewModel.getProjectId(), user);
        model.addAttribute("projectId", viewModel.getProjectId());
        model.addAttribute("projectTitle", viewModel.getProjectTitle());
        model.addAttribute("commandModel", commandModel);
        model.addAttribute("updatedAt", viewModel.getUpdatedAt());
        model.addAttribute("isNew", false);
        model.addAttribute("isSong", isSong);
        // An archived document opens here in place — that is the difference
        // between the archive and the trash — so this page can be the only thing
        // on screen when the question of where it lives comes up.
        model.addAttribute("isArchived", viewModel.getArchivedAt() != null);
        if (isSong) {
            // Writers may browse every version; everyone else is pinned to the
            // published one, matching the screenplay's edition access rule.
            songEditionService.ensureDefaultEdition(id);
            SongEdition active = songEditionService.resolveForAccess(id, editionId, canEditScript);
            Integer activeId = active != null ? active.getId() : null;
            model.addAttribute("blocks", songBlockService.getBlocks(id, activeId));
            model.addAttribute("focusBlockId", null);
            model.addAttribute("editionId", activeId);
            model.addAttribute("editionName", active != null ? active.getName() : null);
            model.addAttribute("canBrowseEditions", canEditScript);
            model.addAttribute("editions", songEditionService.getEditionViewModels(id, canEditScript));
        }
        model.addAttribute("listPath", listPath(isSong));
        model.addAttribute("canEditScript", canEditScript);
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

    /**
     * A project's songs — or its notes — as one file.
     *
     * <p>{@code type} is optional and defaults to songs, so every link minted
     * before notes could be exported still means what it did.
     */
    @RequestMapping(value = "/export-songs", method = RequestMethod.GET)
    public ResponseEntity<byte[]> exportSongs(@RequestParam Integer projectId,
                                              @RequestParam(required = false) String format,
                                              @RequestParam(required = false) List<Integer> ids,
                                              @RequestParam(required = false) String type,
                                              Principal principal) {
        SongExportService.SongExport export = songExportService.exportDocuments(
                projectId, ids, type, SongExportService.parseFormat(format), currentUser(principal));
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
        // Null edition resolves to the default version, which owns the shared title.
        if (isSong && commandModel.getId() != null) {
            songVersionService.autoSaveVersion(saved.getId(), null);
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

    /**
     * Dual-mode like {@link #save}: the songs workspace renames inline over fetch
     * and only needs an ack, while the list page posts a form and wants the
     * redirect back to itself.
     */
    @RequestMapping(value = "/rename", method = RequestMethod.POST)
    public Object rename(@RequestParam Integer id,
                         @RequestParam Integer projectId,
                         @RequestParam(required = false) String type,
                         @RequestParam String title,
                         @RequestHeader(value = "Accept", required = false) String acceptHeader,
                         Principal principal) {
        textDocumentService.rename(id, projectId, title, currentUser(principal));
        if (acceptHeader != null && acceptHeader.contains("application/json")) {
            return ResponseEntity.noContent().build();
        }
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

    @RequestMapping(value = "/archive-document", method = RequestMethod.POST)
    public String archiveDocument(@RequestParam Integer id,
                                  @RequestParam Integer projectId,
                                  @RequestParam(required = false) String type,
                                  Principal principal,
                                  RedirectAttributes redirectAttributes) {
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(normalizeListType(type));
        TextDocument archived = textDocumentService.archive(id, projectId, currentUser(principal));
        if (archived != null) {
            // documentArchiveMessage rather than the trash's: the banner it feeds
            // links to the archive, and "moved to the trash" would misdescribe it.
            redirectAttributes.addFlashAttribute(
                    "documentArchiveMessage",
                    "Archived \"" + archived.getTitle() + "\".");
        } else {
            redirectAttributes.addFlashAttribute(
                    "documentArchiveMessage",
                    "Could not archive that item. It may already be archived.");
        }
        return "redirect:" + listUrl(projectId, isSong);
    }

    @RequestMapping(value = "/archive")
    public String archive(@RequestParam Integer projectId,
                          @RequestParam(required = false) String type,
                          Model model,
                          Principal principal) {
        TextDocumentListViewModel viewModel =
                textDocumentService.getArchiveViewModel(projectId, currentUser(principal));
        if (viewModel == null) {
            return "redirect:/project/list";
        }
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(normalizeListType(type));
        model.addAttribute("viewModel", viewModel);
        model.addAttribute("listType", isSong ? TextDocument.TYPE_SONG : TextDocument.TYPE_NOTES);
        model.addAttribute("isSongList", isSong);
        model.addAttribute("documents", isSong ? viewModel.getSongs() : viewModel.getDrafts());
        model.addAttribute("otherCount", isSong ? viewModel.getDrafts().size() : viewModel.getSongs().size());
        return "project/documents/archive";
    }

    @RequestMapping(value = "/unarchive", method = RequestMethod.POST)
    public String unarchive(@RequestParam Integer id,
                            @RequestParam Integer projectId,
                            @RequestParam(required = false) String type,
                            Principal principal,
                            RedirectAttributes redirectAttributes) {
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(normalizeListType(type));
        TextDocument restored = textDocumentService.unarchive(id, projectId, currentUser(principal));
        if (restored == null) {
            redirectAttributes.addFlashAttribute(
                    "documentArchiveMessage",
                    "Could not bring that item back. It may already be out of the archive.");
            return "redirect:" + archiveUrl(projectId, isSong);
        }
        redirectAttributes.addFlashAttribute(
                "documentArchiveMessage",
                "Brought \"" + restored.getTitle() + "\" back from the archive.");
        return "redirect:" + listUrl(projectId, isSong);
    }

    /**
     * The archive's ticked rows, back into the list.
     *
     * <p>The mirror of {@link #archiveDocuments}, and the reason the archive
     * page has a checkbox column at all: a writer archives a batch at the end of
     * a draft and then wants a handful of it back. Ids that are not in this
     * archive are skipped, so a page left open while another device emptied it
     * still does what it can.
     *
     * <p>Redirects to the list rather than back here, as the single unarchive
     * does — what came back is what the writer is now looking for.
     */
    @RequestMapping(value = "/unarchive-documents", method = RequestMethod.POST)
    public String unarchiveDocuments(@RequestParam(name = "id", required = false) List<Integer> ids,
                                     @RequestParam Integer projectId,
                                     @RequestParam(required = false) String type,
                                     Principal principal,
                                     RedirectAttributes redirectAttributes) {
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(normalizeListType(type));
        int restored = textDocumentService.unarchiveDocuments(ids, projectId, currentUser(principal));
        if (restored == 0) {
            redirectAttributes.addFlashAttribute(
                    "documentArchiveMessage",
                    "Could not bring those back. They may already be out of the archive.");
            return "redirect:" + archiveUrl(projectId, isSong);
        }
        String noun = isSong
                ? (restored == 1 ? " song" : " songs")
                : (restored == 1 ? " note" : " notes");
        redirectAttributes.addFlashAttribute(
                "documentArchiveMessage",
                "Brought " + restored + noun + " back from the archive.");
        return "redirect:" + listUrl(projectId, isSong);
    }

    @RequestMapping(value = "/archive-documents", method = RequestMethod.POST)
    public String archiveDocuments(@RequestParam(name = "id", required = false) List<Integer> ids,
                                   @RequestParam Integer projectId,
                                   @RequestParam(required = false) String type,
                                   Principal principal,
                                   RedirectAttributes redirectAttributes) {
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(normalizeListType(type));
        int archived = textDocumentService.archiveDocuments(ids, projectId, currentUser(principal));
        if (archived > 0) {
            String noun = isSong
                    ? (archived == 1 ? " song" : " songs")
                    : (archived == 1 ? " note" : " notes");
            redirectAttributes.addFlashAttribute(
                    "documentArchiveMessage", "Archived " + archived + noun + ".");
        } else {
            redirectAttributes.addFlashAttribute(
                    "documentShareMessage", "Could not archive those items.");
        }
        return "redirect:" + listUrl(projectId, isSong);
    }

    /**
     * The list's ticked rows, to the trash.
     *
     * <p>{@code type} says which list they were ticked on, so the redirect and
     * the wording land back where the writer is. It is optional and defaults to
     * songs — the only list that could post here before notes had checkboxes.
     */
    @RequestMapping(value = "/delete-songs", method = RequestMethod.POST)
    public String deleteDocuments(@RequestParam(name = "id", required = false) List<Integer> ids,
                              @RequestParam Integer projectId,
                              @RequestParam(required = false) String type,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {
        boolean isSong = !TextDocument.TYPE_NOTES.equalsIgnoreCase(type);
        String one = isSong ? " song" : " note";
        String many = isSong ? " songs" : " notes";
        int deleted = textDocumentService.deleteDocuments(ids, projectId, currentUser(principal));
        if (deleted > 0) {
            // documentTrashMessage, not documentShareMessage: it carries the link
            // back to the trash, which is the whole point of the softer wording.
            redirectAttributes.addFlashAttribute(
                    "documentTrashMessage",
                    "Moved " + deleted + (deleted == 1 ? one : many) + " to the trash.");
        } else {
            redirectAttributes.addFlashAttribute("documentShareMessage",
                    "Could not delete those" + many + ".");
        }
        return "redirect:" + listUrl(projectId, isSong);
    }

    @RequestMapping(value = "/share-email", method = RequestMethod.POST)
    public String shareEmail(@RequestParam(name = "id", required = false) List<Integer> ids,
                             @RequestParam Integer projectId,
                             @RequestParam(required = false) String type,
                             @RequestParam String email,
                             Principal principal,
                             RedirectAttributes redirectAttributes) {
        boolean isSong = !TextDocument.TYPE_NOTES.equalsIgnoreCase(type);
        String many = isSong ? " songs" : " notes";
        List<TextDocument> shared = textDocumentService.shareDocumentsByEmail(ids, email, currentUser(principal));
        if (shared.size() == 1) {
            redirectAttributes.addFlashAttribute(
                    "documentShareMessage",
                    "Emailed \"" + shared.get(0).getTitle() + "\" to " + email.trim() + ".");
        } else if (!shared.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "documentShareMessage",
                    "Emailed " + shared.size() + many + " to " + email.trim() + ".");
        } else {
            redirectAttributes.addFlashAttribute(
                    "documentShareMessage",
                    "Could not email those" + many + ". Check the address and try again.");
        }
        return "redirect:" + listUrl(projectId, isSong);
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
                        "Could not import that file. Check access and try a .txt, .fountain, .docx, .doc, .fdx, .epub, .pdf, or .musicxml file.");
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
                    "Could not import that file. Check access and try a .txt, .fountain, .docx, .doc, .fdx, .epub, .pdf, or .musicxml file.");
            return "redirect:" + listUrl(projectId, isSong);
        }
    }

    // Folders. Four posts and no page of their own: a folder is a heading on
    // the list, and everything you can do to one is done from there.

    @RequestMapping(value = "/folder/create", method = RequestMethod.POST)
    public String createFolder(@RequestParam Integer projectId,
                               @RequestParam(required = false) String type,
                               @RequestParam String name,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        String listType = normalizeListType(type);
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(listType);
        try {
            TextDocumentFolder created = textDocumentFolderService.create(
                    projectId, listType, name, currentUser(principal));
            redirectAttributes.addFlashAttribute("documentFolderMessage", created != null
                    ? "Added the folder \"" + created.getName() + "\"."
                    : "Could not add that folder.");
        } catch (DocumentFolderException e) {
            redirectAttributes.addFlashAttribute("documentFolderMessage", e.getMessage());
        }
        return "redirect:" + listUrl(projectId, isSong);
    }

    @RequestMapping(value = "/folder/rename", method = RequestMethod.POST)
    public String renameFolder(@RequestParam Integer id,
                               @RequestParam Integer projectId,
                               @RequestParam(required = false) String type,
                               @RequestParam String name,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(normalizeListType(type));
        try {
            TextDocumentFolder renamed = textDocumentFolderService.rename(
                    id, projectId, name, currentUser(principal));
            if (renamed == null) {
                redirectAttributes.addFlashAttribute(
                        "documentFolderMessage", "Could not rename that folder.");
            }
        } catch (DocumentFolderException e) {
            redirectAttributes.addFlashAttribute("documentFolderMessage", e.getMessage());
        }
        return "redirect:" + listUrl(projectId, isSong);
    }

    /**
     * Removes a folder. Says how many documents it let go, because the one
     * thing a writer needs to know here is that they are still in the list.
     */
    @RequestMapping(value = "/folder/delete", method = RequestMethod.POST)
    public String deleteFolder(@RequestParam Integer id,
                               @RequestParam Integer projectId,
                               @RequestParam(required = false) String type,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(normalizeListType(type));
        int unfiled = textDocumentFolderService.delete(id, projectId, currentUser(principal));
        String message;
        if (unfiled < 0) {
            message = "Could not remove that folder.";
        } else if (unfiled == 0) {
            message = "Removed the folder.";
        } else {
            message = "Removed the folder. "
                    + (unfiled == 1 ? "Its document is" : "Its " + unfiled + " documents are")
                    + " still in the list.";
        }
        redirectAttributes.addFlashAttribute("documentFolderMessage", message);
        return "redirect:" + listUrl(projectId, isSong);
    }

    /**
     * Files one document, or the ticked ones, under a folder — or takes them
     * out of theirs, which is what a blank {@code folderId} means.
     *
     * <p>One handler for the row menu and the selection bar: the row menu sends
     * a single id and the bar sends several, and the service treats a list of
     * one as a list.
     */
    @RequestMapping(value = "/folder/move", method = RequestMethod.POST)
    public String moveToFolder(@RequestParam(name = "id") List<Integer> ids,
                               @RequestParam Integer projectId,
                               @RequestParam(required = false) String type,
                               @RequestParam(required = false) Integer folderId,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(normalizeListType(type));
        int moved = textDocumentFolderService.moveDocuments(
                ids, projectId, folderId, currentUser(principal));
        if (moved == 0) {
            redirectAttributes.addFlashAttribute(
                    "documentFolderMessage", "Nothing was moved.");
        }
        return "redirect:" + listUrl(projectId, isSong);
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

    private static String archiveUrl(Integer projectId, boolean isSong) {
        return "/project/documents/archive?projectId=" + projectId
                + "&type=" + (isSong ? TextDocument.TYPE_SONG : TextDocument.TYPE_NOTES);
    }

    private User currentUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userService.readByUsername(principal.getName());
    }
}
