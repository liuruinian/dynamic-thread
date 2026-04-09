package com.dynamic.thread.server.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.dynamic.thread.server.config.ServerProperties;
import com.dynamic.thread.server.security.LoginAttemptLimiter;
import javax.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication controller for login/logout.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final ServerProperties properties;
    private final LoginAttemptLimiter loginAttemptLimiter;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        Map<String, Object> result = new HashMap<>();
        
        String ip = getClientIp(httpRequest);
        String username = request.getUsername();
        
        // Check if account is locked
        if (!loginAttemptLimiter.isAllowed(username, ip)) {
            long lockoutExpiry = loginAttemptLimiter.getLockoutExpiry(username, ip);
            long remainingSeconds = (lockoutExpiry - System.currentTimeMillis()) / 1000;
            
            result.put("success", false);
            result.put("message", "Account temporarily locked. Try again in " + remainingSeconds + " seconds");
            result.put("locked", true);
            result.put("lockoutRemaining", remainingSeconds);
            return result;
        }
        
        String configUsername = properties.getAuth().getUsername();
        String configPassword = properties.getAuth().getPassword();
        
        if (configUsername.equals(username) 
                && configPassword.equals(request.getPassword())) {
            // Login success - clear failed attempts
            loginAttemptLimiter.recordSuccess(username, ip);
            
            StpUtil.login(username);
            
            result.put("success", true);
            result.put("message", "Login successful");
            result.put("token", StpUtil.getTokenValue());
            result.put("username", username);
        } else {
            // Login failed - record failure
            loginAttemptLimiter.recordFailure(username, ip);
            int remaining = loginAttemptLimiter.getRemainingAttempts(username, ip);
            
            result.put("success", false);
            result.put("message", "Invalid username or password");
            if (remaining >= 0) {
                result.put("remainingAttempts", remaining);
            }
        }
        
        return result;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout() {
        StpUtil.logout();
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Logout successful");
        return result;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> result = new HashMap<>();
        
        if (StpUtil.isLogin()) {
            result.put("isLogin", true);
            result.put("username", StpUtil.getLoginIdAsString());
            result.put("token", StpUtil.getTokenValue());
        } else {
            result.put("isLogin", false);
        }
        
        return result;
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
    
    /**
     * Extract client IP address from request.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            // Take the first IP if there are multiple
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.trim().isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
