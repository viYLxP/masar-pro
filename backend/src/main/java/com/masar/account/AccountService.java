package com.masar.account;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masar.account.AccountDtos.PasswordChange;
import com.masar.account.AccountDtos.ProfileRequest;
import com.masar.account.AccountDtos.RegisterRequest;
import com.masar.common.ApiException;
import com.masar.common.ErrorCode;
import com.masar.common.Phones;
import com.masar.common.Text;
import com.masar.payment.Payment;
import com.masar.payment.PaymentDtos.CheckoutResponse;
import com.masar.payment.PaymentService;
import com.masar.request.BookingService;
import com.masar.request.ReferenceGenerator;
import com.masar.request.RequestDtos.AccountRequestRow;
import com.masar.request.RequestDtos.BookingCreated;
import com.masar.request.RequestDtos.BookingRequest;
import com.masar.request.RequestDtos.PublicView;
import com.masar.request.ServiceRequest;
import com.masar.request.ServiceRequestRepository;
import com.masar.catalog.ServiceTypeDtos.ServiceRef;
import com.masar.user.AppUser;
import com.masar.user.AppUserRepository;
import com.masar.user.CurrentUserService;
import com.masar.user.Role;
import com.masar.user.UserDtos.UserResponse;

/**
 * The customer's own account: sign-up, profile, their requests, and paying them.
 * حساب العميل نفسه: التسجيل، والملف الشخصي، وطلباته، ودفعها.
 *
 * <p>Every lookup is by the signed-in account, so a customer can never open another customer's request,
 * even by guessing a reference.</p>
 * <p>كل بحث يكون بالحساب المسجّل، فالعميل ما يقدر يفتح طلب عميل ثاني أبدًا، حتى لو خمّن الرقم المرجعي.</p>
 */
@Service
public class AccountService {

    private final AppUserRepository users;
    private final ServiceRequestRepository requests;
    private final BookingService bookings;
    private final PaymentService payments;
    private final CurrentUserService currentUser;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AppUserRepository users, ServiceRequestRepository requests, BookingService bookings,
                          PaymentService payments, CurrentUserService currentUser, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.requests = requests;
        this.bookings = bookings;
        this.payments = payments;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a customer account. The normalised mobile number becomes the username.
     * ينشئ حساب عميل. رقم الجوال بالشكل الموحّد يصير اسم المستخدم.
     */
    @Transactional
    public AppUser register(RegisterRequest request) {
        String phone = Phones.require(request.phone(), "phone");
        if (users.existsByUsernameIgnoreCase(phone)) {
            throw new ApiException(ErrorCode.PHONE_REGISTERED);
        }
        AppUser user = new AppUser(phone, request.fullName().trim(), passwordEncoder.encode(request.password()),
                Role.CUSTOMER);
        user.setPhone(phone);
        user.setEmail(Text.blankToNull(request.email()));
        return users.save(user);
    }

    @Transactional
    public UserResponse updateProfile(ProfileRequest request) {
        AppUser me = currentUser.require();
        me.setFullName(request.fullName().trim());
        me.setEmail(Text.blankToNull(request.email()));
        return UserResponse.from(me);
    }

    @Transactional
    public void changePassword(PasswordChange request) {
        AppUser me = currentUser.require();
        if (!passwordEncoder.matches(request.currentPassword(), me.getPasswordHash())) {
            throw new ApiException(ErrorCode.WRONG_PASSWORD);
        }
        me.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional(readOnly = true)
    public List<AccountRequestRow> myRequests() {
        AppUser me = currentUser.require();
        return requests.findByCustomerOrderByCreatedAtDesc(me).stream()
                .map(r -> new AccountRequestRow(r.getReference(), r.getStatus(), ServiceRef.of(r.getServiceType()),
                        r.getCity(), r.getDistrict(), r.getPreferredDate(), r.getPreferredSlot(),
                        r.getScheduledStart(),
                        r.getQuote() == null || r.getQuote().getSentAt() == null ? null : r.getQuote().getTotal(),
                        r.getPaymentStatus(), payments.isOnlinePayable(r), r.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicView get(String reference) {
        return bookings.view(mine(reference));
    }

    @Transactional
    public BookingCreated book(BookingRequest request) {
        return bookings.createForCustomer(request, currentUser.require());
    }

    @Transactional
    public PublicView accept(String reference) {
        return bookings.accept(mine(reference));
    }

    @Transactional
    public PublicView reject(String reference, String reason) {
        return bookings.reject(mine(reference), reason);
    }

    @Transactional
    public CheckoutResponse pay(String reference, Payment.Method method) {
        return payments.startOnline(mine(reference), method);
    }

    /**
     * Adds a request booked as a guest to the account. Allowed only when the request was made with the account's
     * own mobile number: the same proof the public tracking page asks for, so nothing new is revealed.
     * يضيف طلب انحجز كزائر للحساب. مسموح بس لو الطلب انعمل برقم جوال الحساب نفسه: نفس الإثبات اللي تطلبه صفحة
     * التتبع العامة، فما ينكشف شي جديد.
     */
    @Transactional
    public PublicView claim(String reference) {
        AppUser me = currentUser.require();
        ServiceRequest request = requests.findByReference(ReferenceGenerator.clean(reference))
                .filter(r -> r.getPhone().equals(me.getPhone()))
                .filter(r -> r.getCustomer() == null || r.getCustomer().getId().equals(me.getId()))
                .orElseThrow(() -> ApiException.notFound("request", ReferenceGenerator.clean(reference)));
        request.setCustomer(me);
        return bookings.view(request);
    }

    // A wrong or someone else's reference both give NOT_FOUND. / الرقم الغلط ورقم عميل ثاني الاثنين يرجعون NOT_FOUND.
    private ServiceRequest mine(String reference) {
        AppUser me = currentUser.require();
        return requests.findByReference(ReferenceGenerator.clean(reference))
                .filter(r -> r.getCustomer() != null && r.getCustomer().getId().equals(me.getId()))
                .orElseThrow(() -> ApiException.notFound("request", ReferenceGenerator.clean(reference)));
    }
}
