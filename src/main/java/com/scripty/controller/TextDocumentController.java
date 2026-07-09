package com.scripty.controller;

import com.scripty.commandmodel.textdocument.TextDocumentCommandModel;
import com.scripty.dto.Block;
import com.scripty.dto.TextDocument;
import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.ProjectVersionService;
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

@Controller
@RequestMapping(value = "/project/documents")
public class TextDocumentController {

    @Autowired
    TextDocumentService textDocumentService;

    @Autowired
    ProjectVersionService projectVersionService;

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
        TextDocumentListViewModel listVm = textDocumentService.getListViewModel(projectId, currentUser(principal));
        if (listVm == null) {
            return "redirect:/project/list";
        }
        TextDocumentCommandModel commandModel = textDocumentService.getNewCommandModel(projectId, type);
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(commandModel.getDocumentType());
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
        model.addAttribute("isNew", false);
        model.addAttribute("isSong", isSong);
        model.addAttribute("listPath", listPath(isSong));
        model.addAttribute("canEditScript", projectAccess.canEditScript(viewModel.getProjectId(), user));
        return "project/documents/edit";
    }

    @RequestMapping(value = "/save", method = RequestMethod.POST)
    public String save(@Valid @ModelAttribute("commandModel") TextDocumentCommandModel commandModel,
                       BindingResult bindingResult,
                       @RequestParam(defaultValue = "false") boolean stay,
                       Model model,
                       Principal principal) {
        User user = currentUser(principal);
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(commandModel.getDocumentType());
        if (bindingResult.hasErrors()) {
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
            return "redirect:/project/list";
        }
        if (commandModel.getId() != null
                && textDocumentService.syncInsertedBlocks(saved.getId(), user)) {
            projectVersionService.autoSaveVersion(commandModel.getProjectId());
        }
        if (stay) {
            return "redirect:/project/documents/edit?id=" + saved.getId();
        }
        return "redirect:" + listUrl(commandModel.getProjectId(), isSong);
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public String delete(@RequestParam Integer id,
                         @RequestParam Integer projectId,
                         @RequestParam(required = false) String type,
                         Principal principal) {
        textDocumentService.delete(id, projectId, currentUser(principal));
        return "redirect:" + listUrl(projectId, TextDocument.TYPE_SONG.equalsIgnoreCase(normalizeListType(type)));
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
                             RedirectAttributes redirectAttributes) throws IOException {
        User user = currentUser(principal);
        boolean isSong = TextDocument.TYPE_SONG.equalsIgnoreCase(normalizeListType(type));
        TextDocument saved = textDocumentService.importFile(projectId, type, file, user);
        if (saved == null) {
            redirectAttributes.addFlashAttribute(
                    "documentImportMessage",
                    "Could not import that file. Check access and try a .txt, .fountain, .docx, or .doc file.");
            return "redirect:" + listUrl(projectId, isSong);
        }
        redirectAttributes.addFlashAttribute(
                "documentImportMessage",
                "Imported \"" + saved.getTitle() + "\".");
        return "redirect:/project/documents/edit?id=" + saved.getId();
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
        return isSong ? "/project/documents/songs" : "/project/documents/drafts";
    }

    private static String listUrl(Integer projectId, boolean isSong) {
        return listPath(isSong) + "?projectId=" + projectId;
    }

    private User currentUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userService.readByUsername(principal.getName());
    }
}
