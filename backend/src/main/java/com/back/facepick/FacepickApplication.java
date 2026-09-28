package com.back.facepick;

import com.back.facepick.global.error.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import(GlobalExceptionHandler.class)
@SpringBootApplication
public class FacepickApplication {
    public static void main(String[] args) {
        SpringApplication.run(FacepickApplication.class, args);
    }
}
