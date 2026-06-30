package com.scripty.service;

import com.scripty.commandmodel.scene.createscene.CreateSceneCommandModel;
import com.scripty.commandmodel.scene.createscenebelow.CreateSceneBelowCommandModel;
import com.scripty.commandmodel.scene.editscene.EditSceneCommandModel;
import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.Scene;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.SceneRepository;
import com.scripty.viewmodel.scene.allscenes.AllScenesViewModel;
import com.scripty.viewmodel.scene.createscene.CreateSceneViewModel;
import com.scripty.viewmodel.scene.createscenebelow.CreateSceneBelowViewModel;
import com.scripty.viewmodel.scene.editscene.EditSceneViewModel;
import com.scripty.viewmodel.scene.sceneprofile.BlockViewModel;
import com.scripty.viewmodel.scene.sceneprofile.SceneProfileViewModel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SceneServiceImpl implements SceneService {

    private final SceneRepository sceneRepository;
    private final BlockRepository blockRepository;
    private final ProjectRepository projectRepository;
    private final PersonRepository personRepository;

    @Autowired
    public SceneServiceImpl(SceneRepository sceneRepository,
                            BlockRepository blockRepository,
                            ProjectRepository projectRepository,
                            PersonRepository personRepository) {
        this.sceneRepository = sceneRepository;
        this.blockRepository = blockRepository;
        this.projectRepository = projectRepository;
        this.personRepository = personRepository;
    }

    @Override
    public Scene read(Integer id) {
        return sceneRepository.findById(id).orElse(null);
    }

    @Override
    public SceneProfileViewModel getSceneProfileViewModel(Integer id) {
        SceneProfileViewModel vm = new SceneProfileViewModel();
        Scene scene = sceneRepository.findById(id).orElse(null);
        List<Block> blocks = blockRepository.findBySceneIdOrderByOrderAsc(scene.getId());

        Project project = null;
        if (scene.getProject() != null) {
            project = projectRepository.findById(scene.getProject().getId()).orElse(null);
        }

        Scene previousScene = sceneRepository
            .findByProjectIdAndOrder(scene.getProject().getId(), scene.getOrder() - 1)
            .orElse(null);
        Scene nextScene = sceneRepository
            .findByProjectIdAndOrder(scene.getProject().getId(), scene.getOrder() + 1)
            .orElse(null);

        vm.setId(scene.getId());
        vm.setName(scene.getName());

        if (project != null) {
            vm.setProjectId(project.getId());
            vm.setProjectTitle(project.getTitle());
        }

        if (previousScene != null) {
            vm.setPreviousSceneId(previousScene.getId());
            vm.setPreviousSceneName(previousScene.getName());
        }

        if (nextScene != null) {
            vm.setNextSceneId(nextScene.getId());
            vm.setNextSceneName(nextScene.getName());
        }

        List<BlockViewModel> blockViewModels = new ArrayList<>();
        for (Block block : blocks) {
            BlockViewModel bvm = new BlockViewModel();
            bvm.setId(block.getId());
            bvm.setOrder(block.getOrder());
            bvm.setContent(block.getContent());
            if (block.getPerson() != null) {
                Person person = personRepository.findById(block.getPerson().getId()).orElse(null);
                if (person != null) {
                    bvm.setPersonId(person.getId());
                    bvm.setPersonName(person.getName());
                }
            }
            blockViewModels.add(bvm);
        }
        vm.setBlocks(blockViewModels);
        return vm;
    }

    @Override
    public AllScenesViewModel getAllScenesViewModel(Integer projectId) {
        AllScenesViewModel vm = new AllScenesViewModel();
        Project project = projectRepository.findById(projectId).orElse(null);
        List<Scene> scenes = sceneRepository.findByProjectIdOrderByOrderAsc(projectId);

        vm.setProjectId(project.getId());
        vm.setProjectTitle(project.getTitle());

        List<SceneProfileViewModel> sceneProfiles = new ArrayList<>();
        for (Scene scene : scenes) {
            sceneProfiles.add(getSceneProfileViewModel(scene.getId()));
        }
        vm.setScenes(sceneProfiles);
        return vm;
    }

    @Override
    public CreateSceneViewModel getCreateSceneViewModel(Integer projectId) {
        CreateSceneViewModel vm = new CreateSceneViewModel();
        CreateSceneCommandModel commandModel = new CreateSceneCommandModel();
        commandModel.setProjectId(projectId);
        vm.setCreateSceneCommandModel(commandModel);
        vm.setProjectId(projectId);
        return vm;
    }

    @Override
    public CreateSceneBelowViewModel getCreateSceneBelowViewModel(Integer id) {
        CreateSceneBelowViewModel vm = new CreateSceneBelowViewModel();
        Scene existingScene = sceneRepository.findById(id).orElse(null);
        Project project = projectRepository.findBySceneId(existingScene.getId());

        CreateSceneBelowCommandModel commandModel = new CreateSceneBelowCommandModel();
        commandModel.setId(existingScene.getId());
        commandModel.setProjectId(project.getId());
        vm.setCreateSceneBelowCommandModel(commandModel);
        vm.setProjectId(project.getId());
        return vm;
    }

    @Override
    public EditSceneViewModel getEditSceneViewModel(Integer id) {
        EditSceneViewModel vm = new EditSceneViewModel();
        Scene scene = sceneRepository.findById(id).orElse(null);
        Project project = projectRepository.findById(scene.getProject().getId()).orElse(null);

        vm.setId(id);
        EditSceneCommandModel commandModel = new EditSceneCommandModel();
        commandModel.setId(scene.getId());
        commandModel.setName(scene.getName());
        commandModel.setProjectId(project.getId());
        vm.setEditSceneCommandModel(commandModel);
        return vm;
    }

    @Override
    @Transactional
    public Scene saveCreateSceneCommandModel(CreateSceneCommandModel cmd) {
        Scene scene = new Scene();
        Project project = projectRepository.findById(cmd.getProjectId()).orElse(null);
        scene.setName(cmd.getName());
        if (project != null) {
            scene.setProject(project);
        }
        int order = sceneRepository.countByProjectId(project != null ? project.getId() : null) + 1;
        scene.setOrder(order);
        return sceneRepository.save(scene);
    }

    @Override
    @Transactional
    public Scene saveCreateSceneBelowCommandModel(CreateSceneBelowCommandModel cmd) {
        Scene existingScene = sceneRepository.findById(cmd.getId()).orElse(null);
        Project project = projectRepository.findById(existingScene.getProject().getId()).orElse(null);

        Scene scene = new Scene();
        scene.setName(cmd.getName());
        scene.setProject(project);
        scene.setOrder(existingScene.getOrder());

        int newOrder = existingScene.getOrder() + 1;
        sceneRepository.incrementOrdersAbove(existingScene.getOrder(), project.getId());
        scene.setOrder(newOrder);
        return sceneRepository.save(scene);
    }

    @Override
    public Scene saveEditSceneCommandModel(EditSceneCommandModel cmd) {
        Scene scene = sceneRepository.findById(cmd.getId()).orElse(null);
        Project project = projectRepository.findById(cmd.getProjectId()).orElse(null);
        scene.setName(cmd.getName());
        scene.setProject(project);
        sceneRepository.save(scene);
        return scene;
    }

    @Override
    @Transactional
    public Scene deleteScene(Integer id) {
        Scene scene = sceneRepository.findById(id).orElse(null);
        sceneRepository.delete(scene);
        sceneRepository.decrementOrdersAbove(scene.getOrder(), scene.getProject().getId());
        return scene;
    }

    @Override
    @Transactional
    public Scene moveSceneUp(Integer id) {
        Scene scene = sceneRepository.findById(id).orElse(null);
        Scene previousScene = sceneRepository
            .findByProjectIdAndOrder(scene.getProject().getId(), scene.getOrder() - 1)
            .orElse(null);
        if (previousScene != null) {
            int tempOrder = previousScene.getOrder();
            previousScene.setOrder(scene.getOrder());
            scene.setOrder(tempOrder);
            sceneRepository.save(previousScene);
            sceneRepository.save(scene);
        }
        return scene;
    }

    @Override
    @Transactional
    public Scene moveSceneDown(Integer id) {
        Scene scene = sceneRepository.findById(id).orElse(null);
        Scene nextScene = sceneRepository
            .findByProjectIdAndOrder(scene.getProject().getId(), scene.getOrder() + 1)
            .orElse(null);
        if (nextScene != null) {
            int tempOrder = nextScene.getOrder();
            nextScene.setOrder(scene.getOrder());
            scene.setOrder(tempOrder);
            sceneRepository.save(nextScene);
            sceneRepository.save(scene);
        }
        return scene;
    }
}
