package com.example.demo.controller;

import com.alibaba.fastjson.JSON;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
public class HelloController {
    @ResponseBody
    @RequestMapping(value = "/hi", produces="application/json;charset=UTF-8")
    public String hello(){
        return "{\"name\": \"Kenny\"}";
    }

    /*default value*/
    /*带默认值的*/
    @ResponseBody
    @RequestMapping("/goods")
    public String showGoods(@RequestParam(defaultValue = "Potato") String productName){
        return "product name:"+productName;
    }

    @ResponseBody
    @RequestMapping("/user")
    public SecurityProperties.User getUser(){
        return new SecurityProperties.User();
    }

    @ResponseBody
    @RequestMapping("/login")
    public SecurityProperties.User login(SecurityProperties.User user){
        user.setName(user.getName()+" Wong");
        return user;
    }

    @ResponseBody
    @RequestMapping("/login2")
    public void login2(SecurityProperties.User user, HttpServletResponse response){
        try {
            response.getWriter().write(JSON.toJSONString(user));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}