package com.scripty.controller;

import com.scripty.commandmodel.team.TeamCommandModel;
import com.scripty.dto.Team;
import com.scripty.dto.Project;
import com.scripty.service.TeamService;
import com.scripty.repository.ProjectRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping(value = "/team")
public class TeamController {

    @Autowired
    private TeamService teamService;

    @Autowired
    private ProjectRepository projectRepository;

    @RequestMapping(value = "/list")
    public String list(Model model) {
        List<Team> teams = teamService.list();
        List<Project> allProjects = projectRepository.findAllByOrderByTitleAsc();
        Map<Integer, List<Project>> productionsByTeamId = new HashMap<>();

        for (Team team : teams) {
            List<Project> assigned = new ArrayList<>();
            for (Project project : allProjects) {
                if (project.isAssignedToTeam(team)) {
                    assigned.add(project);
                }
            }
            productionsByTeamId.put(team.getId(), assigned);
        }

        model.addAttribute("teams", teams);
        model.addAttribute("allProjects", allProjects);
        model.addAttribute("productionsByTeamId", productionsByTeamId);
        return "team/list";
    }

    @RequestMapping(value = "/assignProductions", method = RequestMethod.POST)
    public String assignProductions(@RequestParam Integer teamId,
                                    @RequestParam(value = "projectIds", required = false) List<Integer> projectIds) {
        Team team = teamService.read(teamId);
        if (team == null) {
            return "redirect:/team/list";
        }
        if (projectIds == null) {
            projectIds = new ArrayList<>();
        }
        teamService.update(teamId, team.getName(), projectIds);
        return "redirect:/team/list";
    }

    @RequestMapping(value = "/create")
    public String create(Model model) {
        TeamCommandModel commandModel = new TeamCommandModel();
        model.addAttribute("commandModel", commandModel);
        return "team/create";
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String saveCreate(@Valid @ModelAttribute("commandModel") TeamCommandModel commandModel, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return "team/create";
        }
        try {
            teamService.create(commandModel.getName());
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("name", "error.team", e.getMessage());
            return "team/create";
        }
        return "redirect:/team/list";
    }

    @RequestMapping(value = "/edit")
    public String edit(@RequestParam Integer id, Model model) {
        Team team = teamService.read(id);
        if (team == null) {
            return "redirect:/team/list";
        }
        TeamCommandModel commandModel = new TeamCommandModel();
        commandModel.setId(team.getId());
        commandModel.setName(team.getName());

        model.addAttribute("commandModel", commandModel);
        
        List<Project> allProjects = projectRepository.findAllByOrderByTitleAsc();
        model.addAttribute("team", team);
        model.addAttribute("allProjects", allProjects);

        return "team/edit";
    }

    @RequestMapping(value = "/edit", method = RequestMethod.POST)
    public String saveEdit(@Valid @ModelAttribute("commandModel") TeamCommandModel commandModel, 
                           BindingResult bindingResult, 
                           @RequestParam(value = "projectIds", required = false) List<Integer> projectIds, 
                           Model model) {
        if (bindingResult.hasErrors()) {
            List<Project> allProjects = projectRepository.findAllByOrderByTitleAsc();
            model.addAttribute("team", teamService.read(commandModel.getId()));
            model.addAttribute("allProjects", allProjects);
            return "team/edit";
        }
        try {
            if (projectIds == null) {
                projectIds = new ArrayList<>();
            }
            teamService.update(commandModel.getId(), commandModel.getName(), projectIds);
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("name", "error.team", e.getMessage());
            List<Project> allProjects = projectRepository.findAllByOrderByTitleAsc();
            model.addAttribute("team", teamService.read(commandModel.getId()));
            model.addAttribute("allProjects", allProjects);
            return "team/edit";
        }
        return "redirect:/team/list";
    }

    @RequestMapping(value = "/delete")
    public String delete(@RequestParam Integer id) {
        teamService.delete(id);
        return "redirect:/team/list";
    }
}
