package com.finsight.finsight_ai.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;


//DTO object.
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private LocalDateTime timeStamp; //time and date for the error.
    private int status; //to know the status code for the error.
    private String error;
    private String message;
    private Map<String, String> validationErrors;

    public ErrorResponse() {}
    public ErrorResponse(LocalDateTime timeStamp, int status, String error, String message, Map<String, String> validationErrors) {
        this.timeStamp = timeStamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.validationErrors = validationErrors;
    }

    public LocalDateTime getTimeStamp() { return timeStamp; }
    public void setTimeStamp(LocalDateTime timeStamp) { this.timeStamp = timeStamp; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Map<String, String> getValidationErrors() { return validationErrors; }
    public void setValidationErrors(Map<String, String> validationErrors) { this.validationErrors = validationErrors; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private LocalDateTime timeStamp;
        private int status;
        private String error;
        private String message;
        private Map<String, String> validationErrors;

        public Builder timeStamp(LocalDateTime timeStamp) { this.timeStamp = timeStamp; return this; }
        public Builder status(int status) { this.status = status; return this; }
        public Builder error(String error) { this.error = error; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder validationErrors(Map<String, String> validationErrors) { this.validationErrors = validationErrors; return this; }
        public ErrorResponse build() { return new ErrorResponse(timeStamp, status, error, message, validationErrors); }
    }
}