package com.scripty.viewmodel.block.createblockbelow;

import com.scripty.commandmodel.block.createblockbelow.CreateBlockBelowCommandModel;
import com.scripty.viewmodel.block.createblock.CreatePersonViewModel;
import java.util.List;

public class CreateBlockBelowViewModel {

    private int projectId;
    private List<CreatePersonViewModel> persons;

    // Specifically to handle redisplaying when validation errors happen
    private CreateBlockBelowCommandModel createBlockBelowCommandModel;

    public CreateBlockBelowCommandModel getCreateBlockBelowCommandModel() {
        return createBlockBelowCommandModel;
    }

    public void setCreateBlockBelowCommandModel(CreateBlockBelowCommandModel createBlockBelowCommandModel) {
        this.createBlockBelowCommandModel = createBlockBelowCommandModel;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public List<CreatePersonViewModel> getPersons() {
        return persons;
    }

    public void setPersons(List<CreatePersonViewModel> persons) {
        this.persons = persons;
    }
}
