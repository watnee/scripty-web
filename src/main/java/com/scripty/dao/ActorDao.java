/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.dao;

import com.scripty.dto.Actor;
import java.util.List;

/**
 *
 * @author chris
 */
public interface ActorDao {
    
    public Actor create(Actor actor);
    public Actor read(Integer id);
    public void update(Actor actor);
    public void delete(Actor actor);
    public List<Actor> list();
    
}
