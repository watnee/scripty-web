package com.scripty.viewmodel.block.editblock;

import com.scripty.commandmodel.block.editblock.EditBlockCommandModel;
import java.util.List;

public class EditBlockViewModel {

    private int projectId;
    private List<EditPersonViewModel> persons;

    // Specifically to handle redisplaying when validation errors happen
    private EditBlockCommandModel createBlockCommandModel;

    public EditBlockCommandModel getEditBlockCommandModel() {
        return createBlockCommandModel;
    }

    public void setEditBlockCommandModel(EditBlockCommandModel createBlockCommandModel) {
        this.createBlockCommandModel = createBlockCommandModel;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public List<EditPersonViewModel> getPersons() {
        return persons;
    }

    public void setPersons(List<EditPersonViewModel> persons) {
        this.persons = persons;
    }
}
