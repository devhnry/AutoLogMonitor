package org.remita.autologmonitor.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.remita.autologmonitor.entity.Token;
import org.remita.autologmonitor.repository.TokenRepository;
import org.remita.autologmonitor.service.JWTService;
import org.remita.autologmonitor.service.UserDetailService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class JWTSecurityFilter extends OncePerRequestFilter {

    private final UserDetailService userDetailService;
    private final JWTService jwtService;
    private final TokenRepository tokenRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException{
        final String authHeader = request.getHeader("Authorization");
        final String jwtToken;
        final String userEmail;
        if(authHeader == null || authHeader.isBlank()){
            filterChain.doFilter(request,response);
            return;
        }
        jwtToken = authHeader.substring(7);
        userEmail = jwtService.extractUsername(jwtToken);

        if(userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null){
            UserDetails userDetails = userDetailService.loadUserByUsername(userEmail);

            Function<Token, Boolean> validateToken = t -> !t.getExpired().equals(true) && !t.getRevoked().equals(true);

            var isTokenValid = tokenRepository.findByToken(jwtToken).map(
                    validateToken
            ).orElse(false);


            if(jwtService.isTokenValid(jwtToken, userDetails) && isTokenValid){
                SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
                UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                );
                token.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                securityContext.setAuthentication(token);
                SecurityContextHolder.setContext(securityContext);
            }
        }
        filterChain.doFilter(request, response);
    }
}

