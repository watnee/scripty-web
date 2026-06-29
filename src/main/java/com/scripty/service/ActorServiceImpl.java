/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.service;

import com.scripty.dao.ActorDao;
import com.scripty.dto.Actor;
import java.util.List;
import javax.inject.Inject;

/**
 *
 * @author chris
 */
public class ActorServiceImpl implements ActorService {

    ActorDao actorDao;

    @Inject
    public ActorServiceImpl(ActorDao actorDao) {
        this.actorDao = actorDao;
    }

    @Override
    public Actor create(Actor actor) {
        return actorDao.create(actor);
    }

    @Override
    public Actor read(Integer id) {
        return actorDao.read(id);
    }

    @Override
    public void update(Actor actor) {
        actorDao.update(actor);
    }

    @Override
    public void delete(Actor actor) {
        actorDao.delete(actor);
    }

    @Override
    public List<Actor> list() {
        return actorDao.list();
    }
    
}
