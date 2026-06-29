/*
 * To change this license header, choose License Headers in Scene Properties.
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
public class SceneDaoImpl implements SceneDao {

    JdbcTemplate jdbcTemplate;
    
    static String CREATE_QUERY = "INSERT INTO scene (`order`, `name`, project_id) VALUES (?,?,?)";
    static String READ_QUERY = "SELECT * FROM scene WHERE id = ?";
    static String UPDATE_QUERY = "UPDATE scene SET `order` = ?, `name` = ?, project_id = ? WHERE id = ?";
    static String DELETE_QUERY = "DELETE FROM scene WHERE id = ?";
    static String LIST_QUERY = "SELECT * FROM scene";
    static String GET_SCENE_BY_ORDER_QUERY = "SELECT * FROM scene WHERE `order` = ? AND project_id = ?";
    static String GET_ORDER_QUERY = "SELECT `order` FROM scene WHERE id = ?";
    static String UPDATE_ORDER_QUERY = "UPDATE scene SET `order` = ? WHERE id = ?";
    static String ADD_ORDERS_QUERY = "UPDATE scene SET `order` = `order` + 1 WHERE `order` > ? AND project_id = ?";
    static String SUBTRACT_ORDERS_QUERY = "UPDATE scene SET `order` = `order` - 1 WHERE `order` > ? AND project_id = ?";
    static String GET_SCENES_BY_PROJECT_QUERY = "SELECT * FROM scene WHERE project_id = ? ORDER BY `order`";
    static String GET_SCENE_COUNT_BY_PROJECT_QUERY = "SELECT COUNT(*) FROM scene WHERE project_id = ?";
    
    @Inject
    public SceneDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Scene create(Scene scene) {
        
        Integer projectId = null;
        
        if (scene.getProject() != null) {
            projectId = scene.getProject().getId();
        }
        
        int order = jdbcTemplate.queryForObject(GET_SCENE_COUNT_BY_PROJECT_QUERY, Integer.class, projectId) + 1;
        
        jdbcTemplate.update(CREATE_QUERY,
                            order,
                            scene.getName(),
                            projectId
        );
        
        int createId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        
        scene.setId(createId);
        
        return scene;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Scene createBelow(Scene scene) {
        
        Integer projectId = null;
        
        if (scene.getProject() != null) {
            projectId = scene.getProject().getId();
        }
        
        Integer order = null;
        
        if (scene.getOrder() != null) {
            order = scene.getOrder() + 1;
        }
        
        jdbcTemplate.update(ADD_ORDERS_QUERY,
                            scene.getOrder(),
                            projectId
        );
        
        jdbcTemplate.update(CREATE_QUERY,
                            order,
                            scene.getName(),
                            projectId
        );
        
        int createdId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        
        scene.setId(createdId);
        
        return scene;
    }

    @Override
    public Scene read(Integer id) {
        
        try {
            Scene scene = jdbcTemplate.queryForObject(READ_QUERY, new SceneMapper(), id);
            return scene;
        } catch(EmptyResultDataAccessException ex) {}
        
        return null;
    }

    @Override
    public void update(Scene scene) {
        
        Integer projectId = null;
        
        if (scene.getProject() != null) {
            projectId = scene.getProject().getId();
        }
        
        jdbcTemplate.update(UPDATE_QUERY,
                            scene.getOrder(),
                            scene.getName(),
                            projectId,
                            scene.getId()
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(Scene scene) {
        
        jdbcTemplate.update(DELETE_QUERY, scene.getId());
        
        jdbcTemplate.update(SUBTRACT_ORDERS_QUERY,
                            scene.getOrder(),
                            scene.getProject().getId()
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void moveUp(Scene scene) {
        
        Scene previousScene = getSceneByProjectAndOrder(scene.getProject(), scene.getOrder() - 1);
        
        if (previousScene != null) {
            jdbcTemplate.update(UPDATE_ORDER_QUERY,
                                previousScene.getOrder(),
                                scene.getId()
            );

            jdbcTemplate.update(UPDATE_ORDER_QUERY,
                                scene.getOrder(),
                                previousScene.getId()
            );
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void moveDown(Scene scene) {
        
        Scene nextScene = getSceneByProjectAndOrder(scene.getProject(), scene.getOrder() + 1);
        
        if (nextScene != null) {
            jdbcTemplate.update(UPDATE_ORDER_QUERY,
                                nextScene.getOrder(),
                                scene.getId()
            );

            jdbcTemplate.update(UPDATE_ORDER_QUERY,
                                scene.getOrder(),
                                nextScene.getId()
            );
        }
    }

    @Override
    public List<Scene> list() {
        return jdbcTemplate.query(LIST_QUERY, new SceneMapper());
    }

    @Override
    public Scene getPreviousScene(Scene scene) {
        
        try {
            Scene previousScene = getSceneByProjectAndOrder(scene.getProject(), scene.getOrder() - 1);
            return previousScene;
        } catch(EmptyResultDataAccessException ex) {}
        
        return null;
    }

    @Override
    public Scene getNextScene(Scene scene) {
        
        try {
            Scene nextScene = getSceneByProjectAndOrder(scene.getProject(), scene.getOrder() + 1);
            return nextScene;
        } catch(EmptyResultDataAccessException ex) {}
        
        return null;
    }

    @Override
    public List<Scene> getScenesByProject(Project project) {
        return jdbcTemplate.query(GET_SCENES_BY_PROJECT_QUERY,
                                  new SceneMapper(),
                                  project.getId()
        );
    }
    
    private Scene getSceneByProjectAndOrder(Project project, Integer order) {
        
        try {
            Scene scene = jdbcTemplate.queryForObject(GET_SCENE_BY_ORDER_QUERY,
                                                      new SceneMapper(),
                                                      order,
                                                      project.getId());
            return scene;
        } catch (EmptyResultDataAccessException ex) {}
        
        return null;
    }
    
    private class SceneMapper implements RowMapper<Scene> {
        
        @Override
        public Scene mapRow(ResultSet resultSet, int it) throws SQLException {
            
            Scene scene = new Scene();
            
            scene.setId(resultSet.getInt("id"));
            scene.setOrder(resultSet.getInt("order"));
            scene.setName(resultSet.getString("name"));
            
            Integer projectId = resultSet.getInt("project_id");
            
            if (projectId != null) {
                Project project = new Project();
                project.setId(projectId);
                
                scene.setProject(project);
            }
            
            return scene;
        }
    }
    
}
