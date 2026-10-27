package com.masar.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request shapes for customer accounts.
 * أشكال الطلب لحسابات العملاء.
 */
public final class AccountDtos {

    private AccountDtos() {
    }

    // The mobile number is the login name: Saudis remember their number better than a username.
    // رقم الجوال هو اسم الدخول: الناس يتذكرون أرقامهم أكثر من اسم مستخدم.
    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Size(max = 20) String phone,
            @Email @Size(max = 150) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {

        @Override
        public String toString() {
            return "RegisterRequest[" + phone + "]";
        }
    }

    public record ProfileRequest(@NotBlank @Size(max = 120) String fullName, @Email @Size(max = 150) String email) {
    }

    public record PasswordChange(@NotBlank @Size(max = 72) String currentPassword,
                                 @NotBlank @Size(min = 8, max = 72) String newPassword) {

        @Override
        public String toString() {
            return "PasswordChange[****]";
        }
    }
}
