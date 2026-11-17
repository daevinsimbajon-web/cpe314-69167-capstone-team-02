package com.triviagame.online.auth;
import jakarta.validation.constraints.*;
public record EmailCodeRequest(@NotBlank @Size(max=64) String ticket,@NotNull @Pattern(regexp="[0-9]{6}") String code) { }
