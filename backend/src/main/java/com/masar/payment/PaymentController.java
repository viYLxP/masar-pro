package com.masar.payment;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masar.payment.PaymentDtos.CheckoutPage;
import com.masar.payment.PaymentDtos.SandboxRequest;

/**
 * The payment page's endpoints. No login: the random payment id in the link is the key, like a gateway's own link.
 * روابط صفحة الدفع. بدون دخول: رقم الدفعة العشوائي في الرابط هو المفتاح، مثل رابط البوابة نفسها.
 */
@RestController
@RequestMapping("/api/public/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping("/{id}")
    public CheckoutPage get(@PathVariable UUID id) {
        return payments.page(id);
    }

    @PostMapping("/{id}/sandbox")
    public CheckoutPage paySandbox(@PathVariable UUID id, @Valid @RequestBody SandboxRequest body) {
        return payments.payInSandbox(id, body);
    }
}
