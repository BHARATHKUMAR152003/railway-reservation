package com.portfolio.railway.web;

import com.portfolio.railway.model.Models.UserView;
import com.portfolio.railway.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public record RegisterRequest(@NotBlank @Size(max = 80) String name,
                                  @NotBlank @Email @Size(max = 120) String email,
                                  @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(@NotBlank @Size(max = 120) String email, @NotBlank @Size(max = 72) String password) {
    }

    public record LoginResponse(String token, UserView user) {
    }

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse register(@Valid @RequestBody RegisterRequest req) {
        auth.register(req.name(), req.email(), req.password());
        AuthService.Session s = auth.login(req.email(), req.password());
        return new LoginResponse(s.token(), s.user().view());
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        AuthService.Session s = auth.login(req.email(), req.password());
        return new LoginResponse(s.token(), s.user().view());
    }

    @GetMapping("/me")
    public Map<String, UserView> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return Map.of("user", auth.require(authorization).view());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.logout(authorization);
    }
}
