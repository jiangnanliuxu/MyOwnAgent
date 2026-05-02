package com.agentdesk.backend.security;

import com.agentdesk.backend.auth.AuthRepository;
import com.agentdesk.backend.auth.UserAccount;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final AuthRepository authRepository;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            AuthRepository authRepository,
            AuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.jwtTokenService = jwtTokenService;
        this.authRepository = authRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring("Bearer ".length()).trim();
        try {
            JwtAccessTokenClaims claims = jwtTokenService.verifyAccessToken(token);
            UserAccount user = authRepository.findUserById(claims.userId())
                    .orElseThrow(() -> new org.springframework.security.core.AuthenticationException("User not found.") {
                    });
            AuthenticatedUser principal = new AuthenticatedUser(user.id(), user.email(), user.name());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    token,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (org.springframework.security.core.AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, exception);
        }
    }
}
