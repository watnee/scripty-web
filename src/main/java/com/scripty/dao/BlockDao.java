/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.dao;

import com.scripty.dto.Block;
import com.scripty.dto.Scene;
import java.util.List;

/**
 *
 * @author chris
 */
public interface BlockDao {
    
    public Block create(Block block);
    public Block createBelow(Block block);
    public Block read(Integer id);
    public void update(Block block);
    public void delete(Block block);
    public void moveUp(Block block);
    public void moveDown(Block block);
    public void moveTo(Block block, int newOrder);
    public List<Block> list();
    public List<Block> getBlocksByScene(Scene scene);
    
}
