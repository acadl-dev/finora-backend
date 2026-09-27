package com.acadl.finora.auth.dto;


import jakarta.validation.constraints.NotBlank;

public record RegisterDTO (
    @NotBlank(message = "Nome é obrigatório")
    String name,

    @NotBlank(message = "Email é obrigatório")
    String email,

    @NotBlank(message = "Senha é obrigatória")
    String password
){

}
