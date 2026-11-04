package com.event.sems.config;

import com.event.sems.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {
  @Bean
  BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  FilterRegistrationBean<AccountSessionFilter> accountSessionRegistration(
      AccountSessionFilter filter) {
    FilterRegistrationBean<AccountSessionFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  SecurityFilterChain filterChain(
      HttpSecurity http,
      ObjectMapper json,
      AccountSessionFilter accountSessions,
      UserAccountRepository users)
      throws Exception {
    var csrfHandler = new CsrfTokenRequestAttributeHandler();
    csrfHandler.setCsrfRequestAttributeName(null);
    http.csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(csrfHandler))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/",
                        "/index.html",
                        "/about.html",
                        "/event.html",
                        "/login.html",
                        "/signup.html",
                        "/forgot-password.html",
                        "/reset-password.html",
                        "/verify-email.html",
                        "/pages/**",
                        "/css/**",
                        "/images/**",
                        "/js/**",
                        "/vendor/**",
                        "/favicon.ico",
                        "/api/v1/auth/csrf",
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/confirm-registration",
                        "/api/v1/auth/resend-registration-code",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password",
                        "/api/v1/auth/verify-email")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET, "/api/v1/public/events", "/api/v1/public/events/*")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/events", "/api/v1/events/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/venues")
                    .hasAnyRole("ADMIN", "ORGANIZER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/venues")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/venues/*")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/venues/*")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/venues/*/active")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/events")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/events/*")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/events/*/submit")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/events/*/withdraw")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/events/*")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/events/*/cancel")
                    .hasAnyRole("ADMIN", "ORGANIZER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/admin/events/*/review")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/admin/events/*/reconsider")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/events/*/reviews")
                    .hasAnyRole("ADMIN", "ORGANIZER")
                    .requestMatchers(
                        "/api/v1/admin/events/*/staff", "/api/v1/admin/events/*/staff/*")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/admin/mail-settings", "/api/v1/admin/mail-settings/*")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/tickets/mine")
                    .hasRole("ATTENDEE")
                    .requestMatchers(HttpMethod.GET, "/api/v1/feedback/mine")
                    .hasRole("ATTENDEE")
                    .requestMatchers(HttpMethod.GET, "/api/v1/tickets/*", "/api/v1/tickets/*/qr")
                    .hasAnyRole("ADMIN", "ATTENDEE")
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/events/*/registrations",
                        "/api/v1/tickets/*/feedback",
                        "/api/v1/tickets/*/cancel")
                    .hasRole("ATTENDEE")
                    .requestMatchers("/ws/dashboard/**")
                    .hasAnyRole("ADMIN", "ORGANIZER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/events/*/check-ins")
                    .hasRole("STAFF")
                    .requestMatchers(HttpMethod.GET, "/api/v1/events/*/check-ins/search")
                    .hasRole("STAFF")
                    .requestMatchers(HttpMethod.GET, "/api/v1/events/*/reports/*")
                    .hasAnyRole("ADMIN", "ORGANIZER")
                    .requestMatchers(HttpMethod.GET, "/api/v1/events/*/registrations")
                    .hasAnyRole("ADMIN", "ORGANIZER")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/events/*/dashboard",
                        "/api/v1/events/*/feedback",
                        "/api/v1/events/*/feedback/items",
                        "/api/v1/events/*/tickets")
                    .hasAnyRole("ADMIN", "ORGANIZER")
                    .requestMatchers(HttpMethod.GET, "/api/v1/me")
                    .authenticated()
                    .requestMatchers("/api/v1/account/**")
                    .authenticated()
                    .requestMatchers("/api/v1/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .formLogin(
            form ->
                form.loginProcessingUrl("/api/v1/auth/login")
                    .successHandler(
                        (request, response, authentication) -> {
                          users
                              .findByUsernameIgnoreCase(authentication.getName())
                              .ifPresent(
                                  user ->
                                      request
                                          .getSession()
                                          .setAttribute(
                                              "SEMS_PASSWORD_HASH", user.getPasswordHash()));
                          response.setContentType("application/json;charset=UTF-8");
                          json.writeValue(
                              response.getWriter(),
                              Map.of(
                                  "username",
                                  authentication.getName(),
                                  "roles",
                                  authentication.getAuthorities()));
                        })
                    .failureHandler(
                        (request, response, exception) ->
                            writeError(
                                response,
                                json,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "Sai tài khoản, mật khẩu hoặc tài khoản đã bị khóa.")))
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/v1/auth/logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
                    .logoutSuccessHandler(
                        (request, response, authentication) -> {
                          response.setContentType("application/json;charset=UTF-8");
                          json.writeValue(response.getWriter(), Map.of("message", "Đã đăng xuất."));
                        }))
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            writeError(
                                response,
                                json,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "Cần đăng nhập."))
                    .accessDeniedHandler(
                        (request, response, exception) ->
                            writeError(
                                response,
                                json,
                                HttpServletResponse.SC_FORBIDDEN,
                                "Bạn không có quyền thực hiện thao tác này.")))
        .addFilterBefore(accountSessions, AuthorizationFilter.class);
    return http.build();
  }

  private static void writeError(
      HttpServletResponse response, ObjectMapper json, int status, String message)
      throws java.io.IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    json.writeValue(response.getWriter(), Map.of("message", message));
  }
}
