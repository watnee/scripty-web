package com.scripty.viewmodel.block.createblock;

import com.scripty.commandmodel.block.createblock.CreateBlockCommandModel;
import java.util.List;

public class CreateBlockViewModel {

    private int projectId;
    private List<CreatePersonViewModel> persons;

    // Specifically to handle redisplaying when validation errors happen
    private CreateBlockCommandModel createBlockCommandModel;

    public CreateBlockCommandModel getCreateBlockCommandModel() {
        return createBlockCommandModel;
    }

    public void setCreateBlockCommandModel(CreateBlockCommandModel createBlockCommandModel) {
        this.createBlockCommandModel = createBlockCommandModel;
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
