/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.dao;

import com.scripty.dto.Actor;
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
public class ActorDaoImpl implements ActorDao {

    JdbcTemplate jdbcTemplate;

    static String CREATE_QUERY = "INSERT INTO actor (first_name, last_name, phone, email) VALUES (?,?,?,?)";
    static String READ_QUERY = "SELECT * FROM actor WHERE id = ?";
    static String UPDATE_QUERY = "UPDATE actor SET first_name = ?, last_name = ?, phone = ?, email = ? WHERE id = ?";
    static String DELETE_QUERY = "DELETE FROM actor WHERE id = ?";
    static String LIST_QUERY = "SELECT * FROM actor ORDER BY first_name";

    @Inject
    public ActorDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Actor create(Actor actor) {

        jdbcTemplate.update(CREATE_QUERY,
                            actor.getFirstName(),
                            actor.getLastName(),
                            actor.getPhone(),
                            actor.getEmail()
        );

        int createdId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);

        actor.setId(createdId);

        return actor;
    }

    @Override
    public Actor read(Integer id) {

        try {
            Actor actor = jdbcTemplate.queryForObject(READ_QUERY, new ActorMapper(), id);
            return actor;
        } catch (EmptyResultDataAccessException ex) {}

        return null;
    }

    @Override
    public void update(Actor actor) {

        jdbcTemplate.update(UPDATE_QUERY,
                            actor.getFirstName(),
                            actor.getLastName(),
                            actor.getPhone(),
                            actor.getEmail(),
                            actor.getId()
        );
    }

    @Override
    public void delete(Actor actor) {
        jdbcTemplate.update(DELETE_QUERY, actor.getId());
    }

    @Override
    public List<Actor> list() {
        return jdbcTemplate.query(LIST_QUERY, new ActorMapper());
    }

    private class ActorMapper implements RowMapper<Actor> {

        @Override
        public Actor mapRow(ResultSet resultSet, int i) throws SQLException {

            Actor actor = new Actor();

            actor.setId(resultSet.getInt("id"));
            actor.setFirstName(resultSet.getString("first_name"));
            actor.setLastName(resultSet.getString("last_name"));
            actor.setPhone(resultSet.getString("phone"));
            actor.setEmail(resultSet.getString("email"));

            return actor;
        }
    }
    
}
