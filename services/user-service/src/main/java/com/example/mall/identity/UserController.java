package com.example.mall.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserService.UserView create(@Valid @RequestBody CreateUserRequest request) {
        return service.create(new UserService.CreateUser(request.username(), request.password(), request.phone(), request.role()));
    }

    @GetMapping("/{id}")
    public UserService.UserView find(@PathVariable long id) {
        return service.find(id);
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 50) String username,
            @NotBlank String password,
            @Size(max = 20) String phone,
            @NotBlank @Pattern(regexp = "buyer|seller|admin") String role) {}
}
