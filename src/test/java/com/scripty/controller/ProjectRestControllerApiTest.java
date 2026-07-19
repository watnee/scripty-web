package com.scripty.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.api.ProjectResource;
import com.scripty.api.ProjectResourceAssembler;
import com.scripty.commandmodel.project.editproject.EditProjectCommandModel;
import com.scripty.commandmodel.project.titlepage.TitlePageCommandModel;
import com.scripty.dto.Project;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.FountainImportService;
import com.scripty.service.ProjectService;
import com.scripty.service.ScriptEditionService;
import com.scripty.viewmodel.project.projectprofile.ProjectProfileViewModel;
import java.util.Map;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

class ProjectRestControllerApiTest {

    private final ProjectRestController controller = new ProjectRestController();
    private final ProjectService projectService = mock(ProjectService.class);
    private final ProjectResourceAssembler assembler = mock(ProjectResourceAssembler.class);
    private final ProjectAccessSupport projectAccess = mock(ProjectAccessSupport.class);
    private final FountainImportService fountainImportService = mock(FountainImportService.class);
    private final ScriptEditionService scriptEditionService = mock(ScriptEditionService.class);
    private final Principal principal = () -> "writer";

    private Project project;

    @BeforeEach
    void setUp() {
        controller.projectService = projectService;
        controller.projectResourceAssembler = assembler;
        controller.projectAccess = projectAccess;
        controller.fountainImportService = fountainImportService;
        controller.scriptEditionService = scriptEditionService;

        project = new Project();
        project.setId(7);
        project.setTitle("Untitled Project");
    }

    private EditProjectCommandModel command() {
        EditProjectCommandModel cmd = new EditProjectCommandModel();
        cmd.setTitle("Untitled Project");
        return cmd;
    }

    private BindingResult bindingFor(EditProjectCommandModel cmd) {
        return new BeanPropertyBindingResult(cmd, "commandModel");
    }

    private TitlePageCommandModel storedTitlePage() {
        TitlePageCommandModel stored = new TitlePageCommandModel();
        stored.setId(7);
        stored.setScreenplayTitle("THE OLD TITLE");
        stored.setWriters("Jane Doe");
        stored.setContactInfo("jane@example.com");
        stored.setScreenplayVersion("First Draft");
        return stored;
    }

    @Test
    void updateWithoutTitlePageFieldsSkipsTheTitlePageService() {
        when(projectAccess.canAccessProject(7, principal)).thenReturn(true);
        when(projectService.saveEditProjectCommandModel(any())).thenReturn(project);
        when(assembler.toModel(project)).thenReturn(EntityModel.of(new ProjectResource()));

        EditProjectCommandModel cmd = command();
        ResponseEntity<?> response = controller.update(7, cmd, bindingFor(cmd), principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(projectService, never()).saveTitlePageCommandModel(any());
    }

    @Test
    void suppliedTitlePageFieldsOverlayTheStoredOnes() {
        when(projectAccess.canAccessProject(7, principal)).thenReturn(true);
        when(projectAccess.canEditScript(7, principal)).thenReturn(true);
        when(projectService.saveEditProjectCommandModel(any())).thenReturn(project);
        when(projectService.getTitlePageCommandModel(7)).thenReturn(storedTitlePage());
        when(projectService.saveTitlePageCommandModel(any())).thenReturn(project);
        when(assembler.toModel(project)).thenReturn(EntityModel.of(new ProjectResource()));

        EditProjectCommandModel cmd = command();
        cmd.setScreenplayTitle("THE NEW TITLE");
        cmd.setWriters("");

        ResponseEntity<?> response = controller.update(7, cmd, bindingFor(cmd), principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<TitlePageCommandModel> captor = ArgumentCaptor.forClass(TitlePageCommandModel.class);
        verify(projectService).saveTitlePageCommandModel(captor.capture());
        TitlePageCommandModel saved = captor.getValue();
        assertEquals("THE NEW TITLE", saved.getScreenplayTitle());
        assertEquals("", saved.getWriters());
        // Omitted fields keep whatever was stored.
        assertEquals("jane@example.com", saved.getContactInfo());
        assertEquals("First Draft", saved.getScreenplayVersion());
    }

    @Test
    void titlePageFieldsRequireScriptEditPermission() {
        when(projectAccess.canAccessProject(7, principal)).thenReturn(true);
        when(projectAccess.canEditScript(7, principal)).thenReturn(false);

        EditProjectCommandModel cmd = command();
        cmd.setScreenplayTitle("THE NEW TITLE");

        ResponseEntity<?> response = controller.update(7, cmd, bindingFor(cmd), principal);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(projectService, never()).saveTitlePageCommandModel(any());
    }

    private MultipartFile file() {
        return new MockMultipartFile("file", "script.fountain", "text/plain", "INT. ROOM - DAY".getBytes());
    }

    @Test
    void importScriptRequiresScriptEditPermission() {
        when(projectService.read(7)).thenReturn(project);
        when(projectAccess.canEditScript(7, principal)).thenReturn(false);

        ResponseEntity<?> response = controller.importScript(7, file(), null, principal);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void importScriptReturnsTheUpdatedProject() throws Exception {
        ProjectProfileViewModel profile = new ProjectProfileViewModel();
        profile.setId(7);

        when(projectService.read(7)).thenReturn(project);
        when(projectAccess.canEditScript(7, principal)).thenReturn(true);
        when(fountainImportService.importFileIntoProjectWithStatus(eq(7), eq(null), any()))
                .thenReturn(new FountainImportService.ImportOutcome(true, "Script imported."));
        when(projectService.getProjectProfileViewModel(7)).thenReturn(profile);
        when(assembler.toModel(profile)).thenReturn(EntityModel.of(new ProjectResource()));

        ResponseEntity<?> response = controller.importScript(7, file(), null, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(fountainImportService).importFileIntoProjectWithStatus(eq(7), eq(null), any());
    }

    @Test
    void unparseableFileIsAValidationErrorNotAFailure() throws Exception {
        when(projectService.read(7)).thenReturn(project);
        when(projectAccess.canEditScript(7, principal)).thenReturn(true);
        when(fountainImportService.importFileIntoProjectWithStatus(eq(7), eq(null), any()))
                .thenReturn(new FountainImportService.ImportOutcome(false, "That file was empty."));

        ResponseEntity<?> response = controller.importScript(7, file(), null, principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("That file was empty.", ((Map<?, ?>) response.getBody()).get("file"));
    }
}
