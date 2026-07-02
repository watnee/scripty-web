package com.scripty.service;

import com.scripty.dto.Team;
import com.scripty.dto.Project;
import com.scripty.dto.User;
import com.scripty.repository.TeamRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional
public class TeamServiceImpl implements TeamService {

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public List<Team> list() {
        return teamRepository.findAllByOrderByNameAsc();
    }

    @Override
    public Team read(Integer id) {
        return teamRepository.findById(id).orElse(null);
    }

    @Override
    public Team create(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Team name cannot be empty");
        }
        String trimmed = name.trim();
        if (teamRepository.findByName(trimmed).isPresent()) {
            throw new IllegalArgumentException("Team with this name already exists");
        }
        Team team = new Team();
        team.setName(trimmed);
        return teamRepository.save(team);
    }

    @Override
    public void update(Integer id, String name, List<Integer> projectIds) {
        Team team = teamRepository.findById(id).orElse(null);
        if (team == null) {
            return;
        }
        String oldName = team.getName();
        String newName = name.trim();

        if (newName.isEmpty()) {
            throw new IllegalArgumentException("Team name cannot be empty");
        }

        // 1. Rename team on projects and users if changed
        if (!oldName.equals(newName)) {
            // Check if name already exists
            if (teamRepository.findByName(newName).isPresent()) {
                throw new IllegalArgumentException("Team with this name already exists");
            }
            team.setName(newName);
            teamRepository.save(team);

            // Update users
            List<User> users = userRepository.findAll();
            for (User u : users) {
                if (oldName.equals(u.getTeam())) {
                    u.setTeam(newName);
                    userRepository.save(u);
                }
            }

            // Update projects currently assigned to this team
            List<Project> projects = projectRepository.findAll();
            for (Project p : projects) {
                if (oldName.equals(p.getTeam())) {
                    p.setTeam(newName);
                    projectRepository.save(p);
                }
            }
        }

        // 2. Handle project assignments
        List<Project> allProjects = projectRepository.findAll();
        for (Project p : allProjects) {
            boolean isTarget = projectIds != null && projectIds.contains(p.getId());
            if (isTarget) {
                p.setTeam(newName);
                projectRepository.save(p);
            } else if (newName.equals(p.getTeam())) {
                p.setTeam(null);
                projectRepository.save(p);
            }
        }
    }

    @Override
    public void delete(Integer id) {
        Team team = teamRepository.findById(id).orElse(null);
        if (team == null) {
            return;
        }
        String teamName = team.getName();

        // Update projects
        List<Project> projects = projectRepository.findAll();
        for (Project p : projects) {
            if (teamName.equals(p.getTeam())) {
                p.setTeam(null);
                projectRepository.save(p);
            }
        }

        // Update users
        List<User> users = userRepository.findAll();
        for (User u : users) {
            if (teamName.equals(u.getTeam())) {
                u.setTeam(null);
                userRepository.save(u);
            }
        }

        teamRepository.delete(team);
    }
}
