package com.masar.account;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.masar.account.AccountDtos.PasswordChange;
import com.masar.account.AccountDtos.ProfileRequest;
import com.masar.payment.PaymentDtos.CheckoutResponse;
import com.masar.payment.PaymentDtos.PayRequest;
import com.masar.request.RequestDtos.AccountRequestRow;
import com.masar.request.RequestDtos.BookingCreated;
import com.masar.request.RequestDtos.BookingRequest;
import com.masar.request.RequestDtos.ClaimRequest;
import com.masar.request.RequestDtos.PublicView;
import com.masar.request.RequestDtos.RejectRequest;
import com.masar.user.UserDtos.UserResponse;

/**
 * Customer endpoints (role CUSTOMER, see SecurityConfig).
 * روابط العميل (دور CUSTOMER، شوف SecurityConfig).
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService account;

    public AccountController(AccountService account) {
        this.account = account;
    }

    @PutMapping("/profile")
    public UserResponse updateProfile(@Valid @RequestBody ProfileRequest body) {
        return account.updateProfile(body);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody PasswordChange body) {
        account.changePassword(body);
    }

    @GetMapping("/requests")
    public List<AccountRequestRow> requests() {
        return account.myRequests();
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingCreated book(@Valid @RequestBody BookingRequest body) {
        return account.book(body);
    }

    @PostMapping("/requests/claim")
    public PublicView claim(@Valid @RequestBody ClaimRequest body) {
        return account.claim(body.reference());
    }

    @GetMapping("/requests/{reference}")
    public PublicView get(@PathVariable String reference) {
        return account.get(reference);
    }

    @PostMapping("/requests/{reference}/accept")
    public PublicView accept(@PathVariable String reference) {
        return account.accept(reference);
    }

    @PostMapping("/requests/{reference}/reject")
    public PublicView reject(@PathVariable String reference, @Valid @RequestBody RejectRequest body) {
        return account.reject(reference, body.reason());
    }

    @PostMapping("/requests/{reference}/pay")
    public CheckoutResponse pay(@PathVariable String reference, @Valid @RequestBody PayRequest body) {
        return account.pay(reference, body.method());
    }
}
