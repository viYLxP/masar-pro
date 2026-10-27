-- Customer accounts and payments.
-- حسابات العملاء والمدفوعات.

-- Customers get accounts too. They log in with their mobile number, which is also their username.
-- العملاء صار لهم حسابات. يدخلون برقم الجوال، وهو نفسه اسم المستخدم.
ALTER TABLE app_user DROP CONSTRAINT app_user_role_check;
ALTER TABLE app_user ADD CONSTRAINT app_user_role_check
    CHECK (role IN ('ADMIN', 'DISPATCHER', 'TECHNICIAN', 'CUSTOMER'));
ALTER TABLE app_user ADD COLUMN email VARCHAR(150);
-- One account per mobile number: a second sign-up with the same number is refused.
-- حساب واحد لكل رقم جوال: التسجيل الثاني بنفس الرقم ينرفض.
CREATE UNIQUE INDEX ux_customer_phone ON app_user (phone) WHERE role = 'CUSTOMER';
ALTER TABLE app_user ADD CONSTRAINT ck_customer_phone CHECK (role <> 'CUSTOMER' OR phone IS NOT NULL);

-- The account that made (or claimed) the request. Empty for guest bookings and calls taken by the office.
-- الحساب اللي سوّى الطلب (أو ضافه لحسابه). فاضي لحجوزات الزوار والمكالمات اللي يدخلها المكتب.
ALTER TABLE service_request ADD COLUMN customer_id BIGINT REFERENCES app_user (id);
CREATE INDEX idx_request_customer ON service_request (customer_id, created_at DESC);

-- Kept on the request so lists and filters do not need to read the payment table.
-- محفوظة على الطلب حتى القوائم والفلاتر ما تحتاج تقرأ جدول المدفوعات.
ALTER TABLE service_request ADD COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID'
    CHECK (payment_status IN ('UNPAID', 'PAID', 'REFUNDED'));

-- Every payment attempt. The id is a random UUID because it appears in the payment page links.
-- كل محاولة دفع. الرقم UUID عشوائي لأنه يظهر في روابط صفحة الدفع.
CREATE TABLE payment (
    id             UUID          PRIMARY KEY,
    request_id     BIGINT        NOT NULL REFERENCES service_request (id) ON DELETE CASCADE,
    -- How the customer chose to pay: card (incl. mada), Apple Pay, instalments with Tabby or Tamara, or cash.
    -- طريقة الدفع اللي اختارها العميل: بطاقة (ومعها مدى)، أو Apple Pay، أو تقسيط مع تابي أو تمارا، أو كاش.
    method         VARCHAR(20)   NOT NULL CHECK (method IN ('CARD', 'APPLE_PAY', 'TABBY', 'TAMARA', 'CASH')),
    -- Who processed it. SANDBOX is the built-in test checkout; the others are the real gateways (test or live keys).
    -- مين عالجها. SANDBOX صفحة الدفع التجريبية المدمجة؛ والباقي البوابات الحقيقية (بمفاتيح تجريبية أو حقيقية).
    provider       VARCHAR(20)   NOT NULL
        CHECK (provider IN ('SANDBOX', 'MOYASAR', 'STRIPE', 'TABBY', 'TAMARA', 'CASH')),
    provider_ref   VARCHAR(200),
    checkout_url   VARCHAR(1000),
    amount         NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency       VARCHAR(3)    NOT NULL DEFAULT 'SAR',
    status         VARCHAR(20)   NOT NULL CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED')),
    failure_reason VARCHAR(255),
    -- Only the brand and last 4 digits are kept, never the card number. / نحفظ نوع البطاقة وآخر 4 أرقام بس، أبدًا رقم البطاقة.
    card_brand     VARCHAR(20),
    card_last4     VARCHAR(4),
    recorded_by    VARCHAR(50),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    paid_at        TIMESTAMPTZ,
    refunded_at    TIMESTAMPTZ,
    CONSTRAINT ck_payment_paid CHECK (status NOT IN ('PAID', 'REFUNDED') OR paid_at IS NOT NULL)
);
CREATE INDEX idx_payment_request ON payment (request_id, created_at);
-- A request can be paid only once. Two payments racing to "paid" cannot both win.
-- الطلب ينخلص دفعه مرة وحدة بس. لو دفعتين وصلوا "مدفوع" بنفس الوقت، وحدة بس تنجح.
CREATE UNIQUE INDEX ux_payment_one_paid ON payment (request_id) WHERE status = 'PAID';
