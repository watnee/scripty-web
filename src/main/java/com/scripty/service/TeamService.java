package com.scripty.service;

import com.scripty.dto.Team;
import java.util.List;

public interface TeamService {
    List<Team> list();
    Team read(Integer id);
    Team create(String name);
    void update(Integer id, String name, List<Integer> projectIds);
    void delete(Integer id);
}
