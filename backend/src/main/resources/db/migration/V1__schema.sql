-- Masar Pro schema. Flyway runs this once on an empty database; later changes go in V2__..., V3__...
-- مخطط Masar Pro. Flyway يشغّله مرة وحدة على قاعدة بيانات فاضية؛ والتعديلات اللاحقة تكون في V2__ و V3__...
-- CHECK constraints repeat the most important business rules, so bad data is refused even if a bug slips past the API.
-- قيود CHECK تكرر أهم قواعد العمل، فالبيانات الخاطئة تُرفض حتى لو فلت خطأ من الـ API.

-- Staff accounts. Technicians also have a city (where they work) and a phone the customer may see.
-- حسابات الموظفين. الفني له كمان مدينة (مكان شغله) وجوال ممكن يشوفه العميل.
CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    full_name     VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'DISPATCHER', 'TECHNICIAN')),
    phone         VARCHAR(12),
    city          VARCHAR(20) CHECK (city IN ('RIYADH', 'JEDDAH', 'DAMMAM', 'KHOBAR', 'MAKKAH', 'MADINAH')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- A technician without a city could never be matched to a job, so the database refuses it.
    -- الفني بدون مدينة ما ينفع يتعيّن على أي طلب، فقاعدة البيانات ترفضه.
    CONSTRAINT ck_technician_city CHECK (role <> 'TECHNICIAN' OR city IS NOT NULL)
);

-- The service catalogue shown on the public site (AC maintenance, cleaning, ...), with names in both languages.
-- قائمة الخدمات اللي تظهر في الموقع العام (صيانة مكيفات، تنظيف، ...)، بأسماء باللغتين.
CREATE TABLE service_type (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(30)   NOT NULL UNIQUE,
    name_ar          VARCHAR(100)  NOT NULL,
    name_en          VARCHAR(100)  NOT NULL,
    description_ar   VARCHAR(500),
    description_en   VARCHAR(500),
    -- "Starting from" price shown to customers; the real price is set in the quote.
    -- السعر "يبدأ من" اللي يشوفه العميل؛ والسعر الفعلي يتحدد في عرض السعر.
    base_price       NUMERIC(10, 2) NOT NULL CHECK (base_price >= 0),
    -- How long one visit takes; used to block the technician's calendar.
    -- مدة الزيارة الوحدة؛ تُستخدم لحجز وقت الفني في جدوله.
    duration_minutes INT           NOT NULL CHECK (duration_minutes BETWEEN 30 AND 480),
    sort_order       INT           NOT NULL DEFAULT 0,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version          BIGINT        NOT NULL DEFAULT 0
);

-- Which services each technician is qualified for. Deleting a (never booked) service also removes it from skills.
-- الخدمات اللي كل فني مؤهل لها. حذف خدمة (ما انحجزت أبدًا) يشيلها كمان من المهارات.
CREATE TABLE technician_skill (
    technician_id   BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    service_type_id BIGINT NOT NULL REFERENCES service_type (id) ON DELETE CASCADE,
    PRIMARY KEY (technician_id, service_type_id)
);
CREATE INDEX idx_skill_service ON technician_skill (service_type_id);

-- A customer's request. It moves NEW → QUOTED → ACCEPTED → SCHEDULED → IN_PROGRESS → COMPLETED,
-- or ends as REJECTED (customer said no to the quote) or CANCELLED.
-- طلب العميل. يمشي NEW ← QUOTED ← ACCEPTED ← SCHEDULED ← IN_PROGRESS ← COMPLETED،
-- أو ينتهي REJECTED (العميل رفض العرض) أو CANCELLED (ملغي).
CREATE TABLE service_request (
    id               BIGSERIAL PRIMARY KEY,
    -- Random public reference (e.g. MS-7K3QX9PA). Random, not sequential, so it cannot be guessed.
    -- رقم مرجعي عشوائي (مثل MS-7K3QX9PA). عشوائي وليس متسلسل، حتى ما ينخمّن.
    reference        VARCHAR(12)  NOT NULL UNIQUE,
    customer_name    VARCHAR(120) NOT NULL,
    -- Saudi mobile stored in one normalised form (9665XXXXXXXX) so lookups always match.
    -- جوال سعودي محفوظ بشكل موحّد (9665XXXXXXXX) حتى البحث يطابق دائمًا.
    phone            VARCHAR(12)  NOT NULL CHECK (phone ~ '^9665[0-9]{8}$'),
    email            VARCHAR(150),
    city             VARCHAR(20)  NOT NULL CHECK (city IN ('RIYADH', 'JEDDAH', 'DAMMAM', 'KHOBAR', 'MAKKAH', 'MADINAH')),
    district         VARCHAR(100) NOT NULL,
    address          VARCHAR(300),
    property_type    VARCHAR(20)  NOT NULL CHECK (property_type IN ('APARTMENT', 'VILLA', 'OFFICE', 'RETAIL')),
    service_type_id  BIGINT       NOT NULL REFERENCES service_type (id),
    preferred_date   DATE         NOT NULL,
    preferred_slot   VARCHAR(20)  NOT NULL CHECK (preferred_slot IN ('MORNING', 'AFTERNOON', 'EVENING')),
    details          VARCHAR(2000),
    status           VARCHAR(20)  NOT NULL DEFAULT 'NEW'
        CHECK (status IN ('NEW', 'QUOTED', 'ACCEPTED', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'CANCELLED')),
    technician_id    BIGINT REFERENCES app_user (id),
    scheduled_start  TIMESTAMPTZ,
    scheduled_end    TIMESTAMPTZ,
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    completion_note  VARCHAR(1000),
    reject_reason    VARCHAR(255),
    cancel_reason    VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0,
    -- A booked job always has a technician and a time window that ends after it starts.
    -- الشغل المحجوز دائمًا له فني ووقت ينتهي بعد ما يبدأ.
    CONSTRAINT ck_request_booked CHECK (
        status NOT IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED')
        OR (technician_id IS NOT NULL AND scheduled_start IS NOT NULL AND scheduled_end > scheduled_start)),
    CONSTRAINT ck_request_completed CHECK (status <> 'COMPLETED' OR completed_at IS NOT NULL),
    CONSTRAINT ck_request_cancelled CHECK (status <> 'CANCELLED' OR cancel_reason IS NOT NULL),
    CONSTRAINT ck_request_rejected CHECK (status <> 'REJECTED' OR reject_reason IS NOT NULL)
);
CREATE INDEX idx_request_status ON service_request (status, created_at DESC);
-- The calendar query: "what does this technician have around this time?"
-- استعلام الجدول: "وش عند هذا الفني حول هذا الوقت؟"
CREATE INDEX idx_request_technician_time ON service_request (technician_id, scheduled_start);
CREATE INDEX idx_request_phone ON service_request (phone);

-- One quote per request. Totals are stored (not recomputed) so a sent price never changes behind the customer's back.
-- عرض سعر واحد لكل طلب. الإجماليات محفوظة (ما تنعاد حسابها) حتى السعر المرسل ما يتغير من ورا العميل.
CREATE TABLE quote (
    id          BIGSERIAL PRIMARY KEY,
    request_id  BIGINT        NOT NULL UNIQUE REFERENCES service_request (id) ON DELETE CASCADE,
    valid_until DATE          NOT NULL,
    subtotal    NUMERIC(12, 2) NOT NULL CHECK (subtotal >= 0),
    vat         NUMERIC(12, 2) NOT NULL CHECK (vat >= 0),
    total       NUMERIC(12, 2) NOT NULL CHECK (total = subtotal + vat),
    notes       VARCHAR(1000),
    sent_at     TIMESTAMPTZ,
    responded_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE quote_line (
    id          BIGSERIAL PRIMARY KEY,
    quote_id    BIGINT         NOT NULL REFERENCES quote (id) ON DELETE CASCADE,
    position    INT            NOT NULL,
    description VARCHAR(200)   NOT NULL,
    quantity    INT            NOT NULL CHECK (quantity BETWEEN 1 AND 999),
    unit_price  NUMERIC(10, 2) NOT NULL CHECK (unit_price >= 0),
    line_total  NUMERIC(12, 2) NOT NULL CHECK (line_total = quantity * unit_price),
    UNIQUE (quote_id, position)
);

-- Append-only history of status changes. actor is a staff username, or 'customer' for actions on the public site.
-- سجل تغييرات الحالة (يُضاف له فقط). actor هو اسم الموظف، أو 'customer' للعمليات من الموقع العام.
CREATE TABLE request_event (
    id          BIGSERIAL PRIMARY KEY,
    request_id  BIGINT       NOT NULL REFERENCES service_request (id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    actor       VARCHAR(50)  NOT NULL,
    note        VARCHAR(1000),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_event_request ON request_event (request_id, created_at);
