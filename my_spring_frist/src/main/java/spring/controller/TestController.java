package spring.controller;

import spring.annitation.FoTenController;
import spring.annitation.FoTenRequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@FoTenController
@FoTenRequestMapping
public class TestController {

    @FoTenRequestMapping("/test")
    public void test(HttpServletRequest request, HttpServletResponse response,String name){
        System.out.println("success....");
        try {
            response.getWriter().write("success...");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
