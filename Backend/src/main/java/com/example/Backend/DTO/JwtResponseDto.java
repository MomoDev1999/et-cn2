package com.example.Backend.DTO;

public class JwtResponseDto {
    
    private String token;
    public JwtResponseDto(String accessToken){
        this.token = accessToken;
    }

    public JwtResponseDto(){}

    public String gettoken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
