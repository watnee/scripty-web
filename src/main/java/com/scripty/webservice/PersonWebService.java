/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.webservice;

import com.scripty.commandmodel.person.createperson.CreatePersonCommandModel;
import com.scripty.commandmodel.person.editperson.EditPersonCommandModel;
import com.scripty.dto.Person;
import com.scripty.viewmodel.person.createperson.CreatePersonViewModel;
import com.scripty.viewmodel.person.editperson.EditPersonViewModel;
import com.scripty.viewmodel.person.personlist.PersonListViewModel;
import com.scripty.viewmodel.person.personprofile.PersonProfileViewModel;

/**
 *
 * @author chris
 */
public interface PersonWebService {

    public PersonListViewModel getPersonListViewModel(Integer projectId);
    public PersonProfileViewModel getPersonProfileViewModel(Integer id);

    public CreatePersonViewModel getCreatePersonViewModel(Integer projectId);
    public EditPersonViewModel getEditPersonViewModel(Integer id);

    public Person saveCreatePersonCommandModel(CreatePersonCommandModel createPersonCommandModel);
    public Person saveEditPersonCommandModel(EditPersonCommandModel editPersonCommandModel);

    public Person deletePerson(Integer id);
    
}
