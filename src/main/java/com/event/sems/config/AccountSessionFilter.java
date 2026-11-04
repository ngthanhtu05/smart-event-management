package com.event.sems.config;

import com.event.sems.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AccountSessionFilter extends OncePerRequestFilter {
  private final UserAccountRepository users;

  public AccountSessionFilter(UserAccountRepository users) {
    this.users = users;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/api/v1/auth/")
        || (!path.startsWith("/api/v1/") && !path.startsWith("/ws/dashboard/"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof UserDetails principal) {
      Object sessionHash =
          request.getSession(false) == null
              ? null
              : request.getSession(false).getAttribute("SEMS_PASSWORD_HASH");
      boolean valid =
          users
              .findByUsernameIgnoreCase(principal.getUsername())
              .map(
                  user ->
                      user.isActive()
                          && (user.getPasswordHash().equals(sessionHash)
                              || (sessionHash == null && principal.getPassword() != null)))
              .orElse(false);
      if (!valid) {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response
            .getWriter()
            .write("{\"message\":\"Phiên đăng nhập đã hết hiệu lực. Vui lòng đăng nhập lại.\"}");
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
