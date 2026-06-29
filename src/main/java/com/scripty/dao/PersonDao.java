/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.dao;

import com.scripty.dto.Person;
import com.scripty.dto.Project;
import java.util.List;

/**
 *
 * @author chris
 */
public interface PersonDao {
    
    public Person create(Person person);
    public Person read(Integer id);
    public void update(Person person);
    public void delete(Person person);
    public List<Person> list();
    public List<Person> getPersonsByProject(Project project);
    
}
