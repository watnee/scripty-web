/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.dao;

import com.scripty.dto.Project;
import com.scripty.dto.Scene;
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
public class ProjectDaoImpl implements ProjectDao {
    
    JdbcTemplate jdbcTemplate;
    
    static String CREATE_QUERY = "INSERT INTO project (title) VALUES (?)";
    static String READ_QUERY = "SELECT * FROM project WHERE id = ?";
    static String UPDATE_QUERY = "UPDATE project SET title = ? WHERE id = ?";
    static String DELETE_QUERY = "DELETE FROM project WHERE id = ?";
    static String LIST_QUERY = "SELECT * FROM project ORDER BY title";
    static String GET_PROJECT_BY_SCENE_QUERY = "SELECT * FROM project p " +
                                               "INNER JOIN scene s on p.id = s.project_id " +
                                               "WHERE s.id = ?";
    
    
    @Inject
    public ProjectDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Project create(Project project) {
        
        jdbcTemplate.update(CREATE_QUERY,
                            project.getTitle()
        );
        
        int createId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        
        project.setId(createId);
        
        return project;
    }

    @Override
    public Project read(Integer id) {
        
        try {
            Project project = jdbcTemplate.queryForObject(READ_QUERY, new ProjectMapper(), id);
            return project;
        } catch(EmptyResultDataAccessException ex) {}
        
        return null;
    }

    @Override
    public void update(Project project) {
        
        jdbcTemplate.update(UPDATE_QUERY,
                            project.getTitle(),
                            project.getId()
        );
    }

    @Override
    public void delete(Project project) {
        jdbcTemplate.update(DELETE_QUERY, project.getId());
    }

    @Override
    public List<Project> list() {
        return jdbcTemplate.query(LIST_QUERY, new ProjectMapper());
    }

    @Override
    public Project getProjectByScene(Scene scene) {
        
        try {
            Project project = jdbcTemplate.queryForObject(GET_PROJECT_BY_SCENE_QUERY, new ProjectMapper(), scene.getId());
            return project;
        } catch(EmptyResultDataAccessException ex) {}
        
        return null;
    }
    
    private class ProjectMapper implements RowMapper<Project> {
        
        @Override
        public Project mapRow(ResultSet resultSet, int it) throws SQLException {
            
            Project project = new Project();
            
            project.setId(resultSet.getInt("id"));
            project.setTitle(resultSet.getString("title"));
            
            return project;
        }
    }
    
}
