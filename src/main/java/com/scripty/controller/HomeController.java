/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.controller;

import com.scripty.dto.User;
import com.scripty.service.UserService;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 *
 * @author chris
 */
@Controller
public class HomeController {
    
    @Autowired
    private UserService userService;
    
    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String index(Principal principal) {
        if (principal != null) {
            User currentUser = userService.readByUsername(principal.getName());
            if (currentUser != null) {
                if (currentUser.getDefaultProjectId() != null) {
                    return "redirect:/project/show?id=" + currentUser.getDefaultProjectId();
                } else {
                    return "redirect:/project/list";
                }
            }
        }
        return "index";
    }

    @RequestMapping(value = "/help", method = RequestMethod.GET)
    public String help() {
        return "help";
    }
    
}
