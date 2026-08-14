package com.finsight.finsight_ai.Controller;

import com.finsight.finsight_ai.Service.AccountService;
import com.finsight.finsight_ai.dto.AccountRequest;
import com.finsight.finsight_ai.dto.AccountResponse;
import com.finsight.finsight_ai.entity.User;
import com.finsight.finsight_ai.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts") // Base URL handles the routing
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;

    // Leave this blank! It now maps perfectly to POST /api/v1/accounts
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody AccountRequest request,
            @AuthenticationPrincipal UserPrincipal principal)
    {
        AccountResponse response = accountService.createAccount(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Leave this blank! It now maps perfectly to GET /api/v1/accounts
    @GetMapping
    public ResponseEntity<List<AccountResponse>> getUserAccounts(@AuthenticationPrincipal UserPrincipal principal){
        List<AccountResponse> accounts = accountService.getAllUserAccounts(principal.getUserId());
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{id}")
    public ResponseEntity<String> getAccountBalance(
            @PathVariable("id") UUID accountId,
            @AuthenticationPrincipal UserPrincipal principal) {

        String Balance = accountService.getAccountBalance(accountId, principal.getUserId());

        return ResponseEntity.ok("Your Balance is " + Balance);
    }
}
