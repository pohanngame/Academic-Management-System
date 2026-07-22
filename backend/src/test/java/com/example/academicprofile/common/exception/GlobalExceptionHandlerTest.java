package com.example.academicprofile.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new StatusController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @ParameterizedTest
    @ValueSource(ints = { 502, 503, 504 })
    void preservesBusinessExceptionHttpStatus(int statusCode) throws Exception {
        mockMvc.perform(get("/test/status/{statusCode}", statusCode))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("controlled failure"));
    }

    @RestController
    static class StatusController {

        @GetMapping("/test/status/{statusCode}")
        void fail(@PathVariable int statusCode) {
            throw new BusinessException(HttpStatus.valueOf(statusCode), "controlled failure");
        }
    }
}
