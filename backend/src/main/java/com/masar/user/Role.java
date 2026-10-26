package com.masar.user;

/**
 * Staff roles. Customers do not have accounts; they use the public booking and tracking pages.
 * أدوار الموظفين. العملاء ما لهم حسابات، يستخدمون صفحات الحجز والتتبع العامة.
 *
 * <p>ADMIN: everything, including the service catalogue and user accounts.
 * DISPATCHER: operations; handles requests, writes quotes and books technicians.
 * TECHNICIAN: sees only the jobs assigned to them, and starts and completes them.</p>
 * <p>ADMIN: كل شي، بما فيها قائمة الخدمات وحسابات المستخدمين.
 * DISPATCHER: العمليات؛ يستقبل الطلبات ويكتب عروض الأسعار ويحجز الفنيين.
 * TECHNICIAN: يشوف بس الأعمال المعيّنة له، ويبدأها وينهيها.</p>
 */
public enum Role {
    ADMIN,
    DISPATCHER,
    TECHNICIAN
}
