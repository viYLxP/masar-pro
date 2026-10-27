package com.masar.user;

/**
 * Account roles. Visitors need no account: they can book and track with the reference and phone.
 * أدوار الحسابات. الزائر ما يحتاج حساب: يقدر يحجز ويتتبع بالرقم المرجعي والجوال.
 *
 * <p>ADMIN: everything, including the service catalogue and user accounts.
 * DISPATCHER: operations; handles requests, writes quotes and books technicians.
 * TECHNICIAN: sees only the jobs assigned to them, and starts and completes them.
 * CUSTOMER: signs up on the site; sees only their own requests, answers quotes and pays online.</p>
 * <p>ADMIN: كل شي، بما فيها قائمة الخدمات وحسابات المستخدمين.
 * DISPATCHER: العمليات؛ يستقبل الطلبات ويكتب عروض الأسعار ويحجز الفنيين.
 * TECHNICIAN: يشوف بس الأعمال المعيّنة له، ويبدأها وينهيها.
 * CUSTOMER: يسجّل من الموقع؛ يشوف بس طلباته، ويرد على العروض ويدفع أونلاين.</p>
 */
public enum Role {
    ADMIN,
    DISPATCHER,
    TECHNICIAN,
    CUSTOMER;

    // Staff are everyone who works in the company; customers are the only outside role.
    // الموظفين هم كل اللي يشتغلون في الشركة؛ والعميل هو الدور الوحيد من برا.
    public boolean isStaff() {
        return this != CUSTOMER;
    }
}
