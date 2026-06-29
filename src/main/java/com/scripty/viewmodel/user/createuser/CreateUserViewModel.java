package com.scripty.viewmodel.user.createuser;

import com.scripty.commandmodel.user.createuser.CreateUserCommandModel;

public class CreateUserViewModel {

    private CreateUserCommandModel createUserCommandModel;

    public CreateUserCommandModel getCreateUserCommandModel() {
        return createUserCommandModel;
    }

    public void setCreateUserCommandModel(CreateUserCommandModel createUserCommandModel) {
        this.createUserCommandModel = createUserCommandModel;
    }

}
