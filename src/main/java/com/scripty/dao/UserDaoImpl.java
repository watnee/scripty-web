package com.scripty.dao;

import com.scripty.dto.User;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.inject.Inject;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class UserDaoImpl implements UserDao {

    JdbcTemplate jdbcTemplate;
    PasswordEncoder passwordEncoder;

    static String CREATE_USER_QUERY = "INSERT INTO `user` (username, `password`, enabled, first_name, last_name) VALUES (?,?,?,?,?)";
    static String CREATE_AUTHORITY_QUERY = "INSERT INTO authority (username, authority) VALUES (?,?)";
    static String READ_QUERY = "SELECT * FROM `user` WHERE id = ?";
    static String READ_AUTHORITY_QUERY = "SELECT authority FROM authority WHERE username = ?";
    static String UPDATE_QUERY = "UPDATE `user` SET username = ?, enabled = ?, first_name = ?, last_name = ? WHERE id = ?";
    static String UPDATE_PASSWORD_QUERY = "UPDATE `user` SET `password` = ? WHERE id = ?";
    static String DELETE_AUTHORITY_QUERY = "DELETE FROM authority WHERE username = ?";
    static String DELETE_QUERY = "DELETE FROM `user` WHERE id = ?";
    static String LIST_QUERY = "SELECT * FROM `user` ORDER BY username";

    @Inject
    public UserDaoImpl(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public User create(User user) {

        String encodedPassword = passwordEncoder.encode(user.getPassword());

        jdbcTemplate.update(CREATE_USER_QUERY,
                            user.getUsername(),
                            encodedPassword,
                            user.isEnabled() ? 1 : 0,
                            user.getFirstName(),
                            user.getLastName()
        );

        int createdId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        user.setId(createdId);

        jdbcTemplate.update(CREATE_AUTHORITY_QUERY, user.getUsername(), "ROLE_USER");
        if (user.isAdmin()) {
            jdbcTemplate.update(CREATE_AUTHORITY_QUERY, user.getUsername(), "ROLE_ADMIN");
        }

        return user;
    }

    @Override
    public User read(Integer id) {

        try {
            User user = jdbcTemplate.queryForObject(READ_QUERY, new UserMapper(), id);
            List<String> authorities = jdbcTemplate.queryForList(READ_AUTHORITY_QUERY, String.class, user.getUsername());
            user.setAdmin(authorities.contains("ROLE_ADMIN"));
            return user;
        } catch (EmptyResultDataAccessException ex) {}

        return null;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void update(User user) {

        jdbcTemplate.update(UPDATE_QUERY,
                            user.getUsername(),
                            user.isEnabled() ? 1 : 0,
                            user.getFirstName(),
                            user.getLastName(),
                            user.getId()
        );

        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            String encodedPassword = passwordEncoder.encode(user.getPassword());
            jdbcTemplate.update(UPDATE_PASSWORD_QUERY, encodedPassword, user.getId());
        }

        jdbcTemplate.update(DELETE_AUTHORITY_QUERY, user.getUsername());
        jdbcTemplate.update(CREATE_AUTHORITY_QUERY, user.getUsername(), "ROLE_USER");
        if (user.isAdmin()) {
            jdbcTemplate.update(CREATE_AUTHORITY_QUERY, user.getUsername(), "ROLE_ADMIN");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(User user) {
        jdbcTemplate.update(DELETE_AUTHORITY_QUERY, user.getUsername());
        jdbcTemplate.update(DELETE_QUERY, user.getId());
    }

    @Override
    public List<User> list() {
        List<User> users = jdbcTemplate.query(LIST_QUERY, new UserMapper());
        for (User user : users) {
            List<String> authorities = jdbcTemplate.queryForList(READ_AUTHORITY_QUERY, String.class, user.getUsername());
            user.setAdmin(authorities.contains("ROLE_ADMIN"));
        }
        return users;
    }

    private class UserMapper implements RowMapper<User> {

        @Override
        public User mapRow(ResultSet resultSet, int i) throws SQLException {

            User user = new User();

            user.setId(resultSet.getInt("id"));
            user.setUsername(resultSet.getString("username"));
            user.setEnabled(resultSet.getBoolean("enabled"));
            user.setFirstName(resultSet.getString("first_name"));
            user.setLastName(resultSet.getString("last_name"));

            return user;
        }
    }

}
