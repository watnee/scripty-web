package com.scripty.service;

import com.scripty.dao.UserDao;
import com.scripty.dto.User;
import java.util.List;
import javax.inject.Inject;

public class UserServiceImpl implements UserService {

    UserDao userDao;

    @Inject
    public UserServiceImpl(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public User create(User user) {
        return userDao.create(user);
    }

    @Override
    public User read(Integer id) {
        return userDao.read(id);
    }

    @Override
    public void update(User user) {
        userDao.update(user);
    }

    @Override
    public void delete(User user) {
        userDao.delete(user);
    }

    @Override
    public List<User> list() {
        return userDao.list();
    }

}
