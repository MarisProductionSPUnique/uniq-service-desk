package com.ibs;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@RestController public class SupportPortalController {
static boolean authorized(HttpServletRequest r){HttpSession s=r.getSession(false);return s!=null&&s.getAttribute("until") instanceof Long t&&t>System.currentTimeMillis();}
@PostMapping("/api/support/login") public void login(HttpServletRequest r,@RequestHeader("X-Support-Key") String key){String secret=System.getenv("UNIQ_SERVICE_DESK_KEY");if(secret==null||secret.isBlank()||!MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),key.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);HttpSession s=r.getSession();r.changeSessionId();s.setAttribute("until",System.currentTimeMillis()+1800000);}
@PostMapping("/api/support/logout") public void logout(HttpServletRequest r){HttpSession s=r.getSession(false);if(s!=null)s.invalidate();}
}
