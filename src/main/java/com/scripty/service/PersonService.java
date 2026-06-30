package com.scripty.service;

import com.scripty.commandmodel.person.createperson.CreatePersonCommandModel;
import com.scripty.commandmodel.person.editperson.EditPersonCommandModel;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.viewmodel.person.createperson.CreatePersonViewModel;
import com.scripty.viewmodel.person.editperson.EditPersonViewModel;
import com.scripty.viewmodel.person.personlist.PersonListViewModel;
import com.scripty.viewmodel.person.personprofile.PersonProfileViewModel;
import java.util.List;

public interface PersonService {

    Person create(Person person);
    Person read(Integer id);
    List<Person> getPersonsByProject(Project project);

    PersonListViewModel getPersonListViewModel(Integer projectId);
    PersonProfileViewModel getPersonProfileViewModel(Integer id);

    CreatePersonViewModel getCreatePersonViewModel(Integer projectId);
    EditPersonViewModel getEditPersonViewModel(Integer id);

    Person saveCreatePersonCommandModel(CreatePersonCommandModel createPersonCommandModel);
    Person saveEditPersonCommandModel(EditPersonCommandModel editPersonCommandModel);

    Person deletePerson(Integer id);
}
