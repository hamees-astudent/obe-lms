package com.lms.infrastructure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The advice adds a catch-all {@code Exception} handler, which must not
 * intercept the {@link ResponseStatusException}s the services throw — turning
 * every 404 and 409 into a 500 would be a far worse failure than the opaque
 * validation errors it was added to fix.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/not-found")
        String notFound() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found: 42");
        }

        @GetMapping("/conflict")
        String conflict() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quiz already submitted");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("connection pool exhausted at line 42");
        }

        @PostMapping("/validated")
        String validated(@RequestBody @Valid Payload payload) {
            return "ok";
        }

        record Payload(@NotBlank String title, @Positive Integer marks) {}
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("a 404 from a service stays a 404, with its message")
    void notFoundIsPreserved() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Quiz not found: 42"));
    }

    @Test
    @DisplayName("a 409 from a service stays a 409, with its message")
    void conflictIsPreserved() throws Exception {
        mvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Quiz already submitted"));
    }

    @Test
    @DisplayName("validation failures list the offending fields")
    void validationErrorsAreItemised() throws Exception {
        mvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"marks\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.marks").exists());
    }

    @Test
    @DisplayName("an unexpected failure is a 500 that leaks nothing")
    void unexpectedErrorsAreOpaque() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail")
                        .value("Something went wrong on our side. Please try again."));
    }
}
