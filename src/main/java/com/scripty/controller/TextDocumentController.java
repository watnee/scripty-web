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
    public String list(@RequestParam Integer projectId, Model model, Principal principal) {
        TextDocumentListViewModel viewModel = textDocumentService.getListViewModel(projectId, currentUser(principal));
        if (viewModel == null) {
            return "redirect:/project/list";
        }
        model.addAttribute("viewModel", viewModel);
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
        model.addAttribute("projectId", projectId);
        model.addAttribute("projectTitle", listVm.getProjectTitle());
        model.addAttribute("commandModel", commandModel);
        model.addAttribute("isNew", true);
        model.addAttribute("isSong", "SONG".equalsIgnoreCase(commandModel.getDocumentType()));
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
        model.addAttribute("projectId", viewModel.getProjectId());
        model.addAttribute("projectTitle", viewModel.getProjectTitle());
        model.addAttribute("commandModel", commandModel);
        model.addAttribute("isNew", false);
        model.addAttribute("isSong", "SONG".equalsIgnoreCase(commandModel.getDocumentType()));
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
        if (bindingResult.hasErrors()) {
            TextDocumentListViewModel listVm = textDocumentService.getListViewModel(commandModel.getProjectId(), user);
            if (listVm == null) {
                return "redirect:/project/list";
            }
            model.addAttribute("projectId", commandModel.getProjectId());
            model.addAttribute("projectTitle", listVm.getProjectTitle());
            model.addAttribute("commandModel", commandModel);
            model.addAttribute("isNew", commandModel.getId() == null);
            model.addAttribute("isSong", "SONG".equalsIgnoreCase(commandModel.getDocumentType()));
            return "project/documents/edit";
        }

        TextDocument saved = textDocumentService.save(commandModel, user);
        if (saved == null) {
            return "redirect:/project/list";
        }
        if (stay) {
            return "redirect:/project/documents/edit?id=" + saved.getId();
        }
        return "redirect:/project/documents/list?projectId=" + commandModel.getProjectId();
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public String delete(@RequestParam Integer id, @RequestParam Integer projectId, Principal principal) {
        textDocumentService.delete(id, projectId, currentUser(principal));
        return "redirect:/project/documents/list?projectId=" + projectId;
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
        if (!projectAccess.canEditScript(viewModel.getProjectId(), user)) {
            return "redirect:/project/documents/list?projectId=" + viewModel.getProjectId();
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
        TextDocument saved = textDocumentService.importFile(projectId, type, file, user);
        if (saved == null) {
            redirectAttributes.addFlashAttribute(
                    "documentImportMessage",
                    "Could not import that file. Check access and try a .txt, .fountain, .docx, or .doc file.");
            return "redirect:/project/documents/list?projectId=" + projectId;
        }
        redirectAttributes.addFlashAttribute(
                "documentImportMessage",
                "Imported \"" + saved.getTitle() + "\".");
        return "redirect:/project/documents/edit?id=" + saved.getId();
    }

    private User currentUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userService.readByUsername(principal.getName());
    }
}
