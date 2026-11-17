package com.triviagame.online.auth;
import jakarta.validation.constraints.*;
public record PasswordResetRequest(@NotBlank @Size(min=8,max=64) String password,@NotBlank @Size(max=64) String confirmPassword) { }
