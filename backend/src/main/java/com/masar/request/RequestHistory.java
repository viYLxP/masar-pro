package com.masar.request;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.masar.common.CurrentUser;
import com.masar.common.Text;

/**
 * Writes the status history. Every status change goes through here, so the history cannot miss one.
 * يكتب سجل الحالات. كل تغيير حالة يمر من هنا، فما يفوت السجل أي تغيير.
 */
@Component
public class RequestHistory {

    private final RequestEventRepository events;

    public RequestHistory(RequestEventRepository events) {
        this.events = events;
    }

    // A change made by a logged-in staff member. / تغيير سواه موظف مسجّل دخول.
    public void staff(ServiceRequest request, RequestStatus from, String note, Instant at) {
        events.save(new RequestEvent(request, from, request.getStatus(), CurrentUser.username(),
                Text.blankToNull(note), at));
    }

    // A change made by the customer on the public site. / تغيير سواه العميل من الموقع العام.
    public void customer(ServiceRequest request, RequestStatus from, String note, Instant at) {
        events.save(new RequestEvent(request, from, request.getStatus(), RequestEvent.CUSTOMER,
                Text.blankToNull(note), at));
    }
}
