package com.greate.community.config;

import com.greate.community.util.CommunityConstant;
import com.greate.community.util.CommunityUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter implements CommunityConstant {

    /**
     * 放行静态资源
     * @param web
     * @throws Exception
     */
    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers("/resources/**");
    }

    // 认证环节我们使用自己的代码 LoginController，绕过 Spring Security 的


    /**
     * 授权
     * @param http
     * @throws Exception
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                .antMatchers(
                    "/user/setting",
                        "/user/upload",
                        "/discuss/add",
                        "/discuss/publish",
                        "/comment/add/**",
                        "/letter/**",
                        "/notice/**",
                        "/like",
                        "/follow",
                        "/unfollow"
                )
                .hasAnyAuthority(
                        AUTHORITY_USER,
                        AUTHORITY_ADMIN,
                        AUTHORITY_MODERATOR
                )

                .antMatchers(
                    "/discuss/top",
                        "/discuss/wonderful"
                )
                .hasAnyAuthority(
                        AUTHORITY_MODERATOR
                )

                .antMatchers(
                        "/discuss/delete",
                        "/discuss/delete/",
                        "/data/**"
                )
                .hasAnyAuthority(
                        AUTHORITY_ADMIN
                )

                .anyRequest().permitAll()

                .and().csrf().disable();//除了以上的模块都允许无需用户登录便可访问，并关闭csrf保护

        // 权限不够时的处理
        http.exceptionHandling()
                // 1. 未登录时的处理(两种情况：1.异步请求 2.普通请求)
                .authenticationEntryPoint(new AuthenticationEntryPoint() {
                    @Override
                    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e) throws IOException, ServletException {
                        String xRequestedWith = request.getHeader("x-requested-with");
                        if ("XMLHttpRequest".equals(xRequestedWith)) {
                            // 异步请求(因为异步请求无法自动响应重定向)
                            response.setContentType("application/plain;charset=utf-8");//设置响应内容
                            PrintWriter writer = response.getWriter();
                            writer.write(CommunityUtil.getJSONString(403, "你还没有登录"));//写入响应内容
                        }
                        else {
                            // 普通请求，直接重定向
                            response.sendRedirect(request.getContextPath() + "/login");
                        }
                    }
                })
                // 2. 权限不够时的处理
                .accessDeniedHandler(new AccessDeniedHandler() {
                    @Override
                    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) throws IOException, ServletException {
                        String xRequestedWith = request.getHeader("x-requested-with");
                        if ("XMLHttpRequest".equals(xRequestedWith)) {
                            // 异步请求
                            response.setContentType("application/plain;charset=utf-8");
                            PrintWriter writer = response.getWriter();
                            writer.write(CommunityUtil.getJSONString(403, "你没有访问该功能的权限"));
                        }
                        else {
                            // 普通请求
                            response.sendRedirect(request.getContextPath() + "/denied");
                        }
                    }
                });

        // Security 底层会默认拦截 /logout 请求，进行退出处理
        // 此处赋予它一个根本不存在的退出路径，使得程序能够执行到我们自己编写的退出代码
        http.logout().logoutUrl("/securitylogout");
        //浏览器只允许页面在相同的域中加载嵌入式框架。
        http.headers().frameOptions().sameOrigin();
    }
}
