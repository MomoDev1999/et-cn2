package com.example.Backend.Exceptions;

public class NotFoundException extends RuntimeException{
    
    private static final long serialVersionUID = 1;

    public NotFoundException(String message){
        super(message);
    }
}
