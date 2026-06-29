package com.scripty.viewmodel.user.edituser;

import com.scripty.commandmodel.user.edituser.EditUserCommandModel;

public class EditUserViewModel {

    private int id;
    private EditUserCommandModel editUserCommandModel;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public EditUserCommandModel getEditUserCommandModel() {
        return editUserCommandModel;
    }

    public void setEditUserCommandModel(EditUserCommandModel editUserCommandModel) {
        this.editUserCommandModel = editUserCommandModel;
    }

}
