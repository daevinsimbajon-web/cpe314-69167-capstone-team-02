package com.triviagame.online.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean DefaultCookieSerializer cookieSerializer(@Value("${app.cookie-secure:false}") boolean secure) {
        DefaultCookieSerializer cookie = new DefaultCookieSerializer();
        cookie.setCookieName("QUIZ_SESSION"); cookie.setUseHttpOnlyCookie(true);
        // DefaultCookieSerializer follows request.isSecure() when not forced.
        // Trusted forwarding makes public HTTPS secure while local HTTP still works.
        if(secure)cookie.setUseSecureCookie(true);
        cookie.setSameSite("Lax"); cookie.setCookiePath("/");
        return cookie;
    }
    @Bean CorsConfigurationSource cors(@Value("${app.allowed-origins}") String configured) {
        List<String> origins = Arrays.stream(configured.split(",")).map(String::strip).toList();
        if (origins.stream().anyMatch(o -> o.contains("*"))) throw new IllegalArgumentException("Explicit CORS origins required");
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins); cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type","X-CSRF-TOKEN"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors); return source;
    }
    @Bean SecurityFilterChain security(HttpSecurity http, ObjectMapper json, LoginRateLimiter limiter,
            CorsConfigurationSource cors, Accounts accounts) throws Exception {
        OncePerRequestFilter rateLimit = new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain chain) throws ServletException, IOException {
                if (request.getMethod().equals("POST") && request.getServletPath().equals("/api/auth/login")
                        && !limiter.acquire(request.getRemoteAddr())) {
                    response.setStatus(429); response.setHeader("Retry-After", "300");
                    write(json, response, "Too many login attempts. Try again in five minutes."); return;
                }
                chain.doFilter(request, response);
            }
        };
        OncePerRequestFilter activeAccount = new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain chain) throws ServletException, IOException {
                var authentication=SecurityContextHolder.getContext().getAuthentication();
                if(authentication!=null&&authentication.isAuthenticated()&&!(authentication instanceof AnonymousAuthenticationToken)
                        &&!accounts.isActive(java.util.UUID.fromString(authentication.getName()))) {
                    new SecurityContextLogoutHandler().logout(request,response,authentication);
                    response.setStatus(401);write(json,response,"Please sign in to continue");return;
                }
                chain.doFilter(request,response);
            }
        };
        http.cors(c -> c.configurationSource(cors)).csrf(c -> {})
            .authorizeHttpRequests(a -> a.requestMatchers("/api/auth/csrf", "/api/auth/register", "/api/auth/username-availability", "/api/auth/login", "/api/auth/email/**", "/api/auth/password/**", "/api/health", "/error").permitAll()
                    .anyRequest().authenticated())
            .requestCache(c -> c.disable())
            .formLogin(f -> f.loginProcessingUrl("/api/auth/login")
                .successHandler((req,res,auth) -> { limiter.succeeded(req.getRemoteAddr());
                    if(!accounts.isActive(java.util.UUID.fromString(auth.getName()))) {
                        new SecurityContextLogoutHandler().logout(req,res,auth);
                        res.setStatus(401);write(json,res,"Invalid username/email or password");return;
                    }
                    res.setContentType("application/json"); json.writeValue(res.getWriter(), Map.of("authenticated",true)); })
                .failureHandler((req,res,error) -> { res.setStatus(401); write(json,res,"Invalid username/email or password"); }))
            .logout(l -> l.logoutUrl("/api/auth/logout").invalidateHttpSession(true).clearAuthentication(true)
                .deleteCookies("QUIZ_SESSION").logoutSuccessHandler((req,res,auth) -> res.setStatus(204)))
            .exceptionHandling(e -> e.authenticationEntryPoint((req,res,error) -> {
                    res.setStatus(401); write(json,res,"Please sign in to continue"); })
                .accessDeniedHandler((req,res,error) -> { res.setStatus(403); write(json,res,"Access denied or security token expired"); }))
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
            .addFilterBefore(rateLimit, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(activeAccount, AnonymousAuthenticationFilter.class);
        return http.build();
    }
    private static void write(ObjectMapper json, HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json"); json.writeValue(response.getWriter(), Map.of("message",message));
    }
}
