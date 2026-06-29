/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.dao;

import com.scripty.dto.Block;
import com.scripty.dto.Person;
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
public class BlockDaoImpl implements BlockDao {
    
    JdbcTemplate jdbcTemplate;
    
    static String CREATE_QUERY = "INSERT INTO block (`order`, content, person_id, scene_id) VALUES (?,?,?,?)";
    static String READ_QUERY = "SELECT * FROM block WHERE id = ?";
    static String UPDATE_QUERY = "UPDATE block SET `order` = ?, content = ?, person_id = ?, scene_id = ? WHERE id = ?";
    static String DELETE_QUERY = "DELETE FROM block WHERE id = ?";
    static String LIST_QUERY = "SELECT * FROM block";
    static String GET_BLOCK_BY_ORDER_QUERY = "SELECT * FROM block WHERE `order` = ? AND scene_id = ?";
    static String GET_ORDER_QUERY = "SELECT `order` FROM block WHERE id = ?";
    static String UPDATE_ORDER_QUERY = "UPDATE block SET `order` = ? WHERE id = ?";
    static String ADD_ORDERS_QUERY = "UPDATE block SET `order` = `order` + 1 WHERE `order` > ? AND scene_id = ?";
    static String SUBTRACT_ORDERS_QUERY = "UPDATE block SET `order` = `order` - 1 WHERE `order` > ? AND scene_id = ?";
    static String GET_BLOCKS_BY_SCENE_QUERY = "SELECT * FROM block WHERE scene_id = ? ORDER BY `order`";
    static String GET_BLOCK_COUNT_BY_SCENE_QUERY = "SELECT COUNT(*) FROM block WHERE scene_id = ?";
    
    @Inject
    public BlockDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Block create(Block block) {
        
        Integer personId = null;
        
        if (block.getPerson() != null) {
            personId = block.getPerson().getId();
        }
        
        Integer sceneId = null;
        
        if (block.getScene() != null) {
            sceneId = block.getScene().getId();
        }
        
        Integer order = jdbcTemplate.queryForObject(GET_BLOCK_COUNT_BY_SCENE_QUERY, Integer.class, sceneId) + 1;
        
        jdbcTemplate.update(CREATE_QUERY,
                            order,
                            block.getContent(),
                            personId,
                            sceneId
        );
        
        int createdId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        
        block.setId(createdId);
        
        return block;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Block createBelow(Block block) {
        
        Integer personId = null;
        
        if (block.getPerson() != null) {
            personId = block.getPerson().getId();
        }
        
        Integer order = null;
        
        if (block.getOrder() != null) {
            order = block.getOrder() + 1;
        }
        
        Integer sceneId = null;
        
        if (block.getScene() != null) {
            sceneId = block.getScene().getId();
        }
        
        jdbcTemplate.update(ADD_ORDERS_QUERY,
                            block.getOrder(),
                            sceneId
        );
        
        jdbcTemplate.update(CREATE_QUERY,
                            order,
                            block.getContent(),
                            personId,
                            sceneId
        );
        
        int createdId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        
        block.setId(createdId);
        
        return block;
    }

    @Override
    public Block read(Integer id) {
        
        try {
            Block block = jdbcTemplate.queryForObject(READ_QUERY, new BlockMapper(), id);
            return block;
        } catch (EmptyResultDataAccessException ex) {}
        
        return null;
        
    }

    @Override
    public void update(Block block) {
        
        Integer personId = null;
        
        if (block.getPerson() != null) {
            personId = block.getPerson().getId();
        }
        
        Integer sceneId = null;
        
        if (block.getScene() != null) {
            sceneId = block.getScene().getId();
        }
        
        jdbcTemplate.update(UPDATE_QUERY,
                            block.getOrder(),
                            block.getContent(),
                            personId,
                            sceneId,
                            block.getId()
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(Block block) {
        
        jdbcTemplate.update(DELETE_QUERY, block.getId());
        
        jdbcTemplate.update(SUBTRACT_ORDERS_QUERY,
                            block.getOrder(),
                            block.getScene().getId()
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void moveUp(Block block) {
        
        Block blockAbove = readByOrder(block.getOrder() - 1, block.getScene().getId());
        
        if (blockAbove != null) {
            jdbcTemplate.update(UPDATE_ORDER_QUERY,
                                blockAbove.getOrder(),
                                block.getId()
            );

            jdbcTemplate.update(UPDATE_ORDER_QUERY,
                                block.getOrder(),
                                blockAbove.getId()
            );
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void moveDown(Block block) {
        
        Block blockBelow = readByOrder(block.getOrder() + 1, block.getScene().getId());
        
        if (blockBelow != null) {
            jdbcTemplate.update(UPDATE_ORDER_QUERY,
                                blockBelow.getOrder(),
                                block.getId()
            );

            jdbcTemplate.update(UPDATE_ORDER_QUERY,
                                block.getOrder(),
                                blockBelow.getId()
            );
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void moveTo(Block block, int newOrder) {
        int currentOrder = block.getOrder();
        int sceneId = block.getScene().getId();

        if (newOrder == currentOrder) return;

        if (newOrder < currentOrder) {
            jdbcTemplate.update(
                "UPDATE block SET `order` = `order` + 1 WHERE `order` >= ? AND `order` < ? AND scene_id = ?",
                newOrder, currentOrder, sceneId);
        } else {
            jdbcTemplate.update(
                "UPDATE block SET `order` = `order` - 1 WHERE `order` > ? AND `order` <= ? AND scene_id = ?",
                currentOrder, newOrder, sceneId);
        }

        jdbcTemplate.update(UPDATE_ORDER_QUERY, newOrder, block.getId());
    }

    @Override
    public List<Block> list() {
        return jdbcTemplate.query(LIST_QUERY, new BlockMapper());
    }

    @Override
    public List<Block> getBlocksByScene(Scene scene) {
        return jdbcTemplate.query(GET_BLOCKS_BY_SCENE_QUERY,
                                  new BlockMapper(),
                                  scene.getId()
        );
    }
    
    private class BlockMapper implements RowMapper<Block> {
        
        @Override
        public Block mapRow(ResultSet resultSet, int i) throws SQLException {
            
            Block block = new Block();
            
            block.setId(resultSet.getInt("id"));
            block.setOrder(resultSet.getInt("order"));
            block.setContent(resultSet.getString("content"));
            
            Integer personId = resultSet.getInt("person_id");
            
            if (personId != null) {
                Person person = new Person();
                person.setId(personId);
                
                block.setPerson(person);
            }
            
            Integer sceneId = resultSet.getInt("scene_id");
            
            if (sceneId != null) {
                Scene scene = new Scene();
                scene.setId(sceneId);
                
                block.setScene(scene);
            }
            
            return block;
        }
    }
    
    private Block readByOrder(Integer order, Integer sceneId) {
        
        try {
            Block block = jdbcTemplate.queryForObject(GET_BLOCK_BY_ORDER_QUERY,
                                                      new BlockMapper(),
                                                      order,
                                                      sceneId);
            return block;
        } catch (EmptyResultDataAccessException ex) {}
        
        return null;
    }
    
}
