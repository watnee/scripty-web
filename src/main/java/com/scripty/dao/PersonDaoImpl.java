/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.dao;

import com.scripty.dto.Actor;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.inject.Inject;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author chris
 */
public class PersonDaoImpl implements PersonDao {
    
    JdbcTemplate jdbcTemplate;
    
    static String CREATE_QUERY = "INSERT INTO person (`name`, full_name, actor_id, project_id) VALUES (?,?,?,?)";
    static String READ_QUERY = "SELECT * FROM person WHERE id = ?";
    static String UPDATE_QUERY = "UPDATE person SET `name` = ?, full_name = ?, actor_id = ?, project_id = ? WHERE id = ?";
    static String DELETE_QUERY = "DELETE FROM person WHERE id = ?";
    static String LIST_QUERY = "SELECT * FROM person";
    static String GET_PERSONS_BY_PROJECT_QUERY = "SELECT * FROM person where project_id = ? ORDER BY `name`";

    @Inject
    public PersonDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Person create(Person person) {
        
        Integer actorId = null;
        
        if (person.getActor() != null) {
            actorId = person.getActor().getId();
        }
        
        Integer projectId = null;
        
        if (person.getProject() != null) {
            projectId = person.getProject().getId();
        }
        
        jdbcTemplate.update(CREATE_QUERY,
                            person.getName(),
                            person.getFullName(),
                            actorId,
                            projectId
        );
        
        int createdId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        
        person.setId(createdId);
        
        return person;
    }

    @Override
    public Person read(Integer id) {
        
        try {
            Person person = jdbcTemplate.queryForObject(READ_QUERY, new PersonMapper(), id);
            return person;
        } catch (EmptyResultDataAccessException ex) {}
        
        return null;
    }

    @Override
    public void update(Person person) {
        
        Integer actorId = null;
        
        if (person.getActor() != null) {
            actorId = person.getActor().getId();
        }
        
        Integer projectId = null;
        
        if (person.getProject() != null) {
            projectId = person.getProject().getId();
        }
        
        jdbcTemplate.update(UPDATE_QUERY,
                            person.getName(),
                            person.getFullName(),
                            actorId,
                            projectId,
                            person.getId()
        );
    }

    @Override
    public void delete(Person person) {
        jdbcTemplate.update(DELETE_QUERY, person.getId());
    }

    @Override
    public List<Person> list() {
        return jdbcTemplate.query(LIST_QUERY, new PersonMapper());
    }

    @Override
    public List<Person> getPersonsByProject(Project project) {
        return jdbcTemplate.query(GET_PERSONS_BY_PROJECT_QUERY,
                                  new PersonMapper(),
                                  project.getId()
        );
    }
    
    private class PersonMapper implements RowMapper<Person> {
        
        @Override
        public Person mapRow(ResultSet resultSet, int i) throws SQLException {
            
            Person person = new Person();
            
            person.setId(resultSet.getInt("id"));
            person.setName(resultSet.getString("name"));
            person.setFullName(resultSet.getString("full_name"));
            
            Integer actorId = resultSet.getInt("actor_id");
            
            if (actorId != null) {
                Actor actor = new Actor();
                actor.setId(actorId);
                
                person.setActor(actor);
            }
            
            Integer projectId = resultSet.getInt("project_id");
            
            if (projectId != null) {
                Project project = new Project();
                project.setId(projectId);
                
                person.setProject(project);
            }
            
            return person;
        }
    }
    
}
