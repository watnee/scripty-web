package com.scripty.dao;

import com.scripty.dto.User;
import java.util.List;

public interface UserDao {

    public User create(User user);
    public User read(Integer id);
    public void update(User user);
    public void delete(User user);
    public List<User> list();

}
