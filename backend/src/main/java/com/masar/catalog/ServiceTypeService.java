package com.masar.catalog;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masar.common.ApiException;
import com.masar.common.ErrorCode;
import com.masar.common.Text;
import com.masar.common.Versions;
import com.masar.catalog.ServiceTypeDtos.ServiceTypeRequest;
import com.masar.catalog.ServiceTypeDtos.ServiceTypeResponse;
import com.masar.request.ServiceRequestRepository;

/**
 * Business rules for the service catalogue (changes are admin-only, see SecurityConfig).
 * قواعد قائمة الخدمات (التعديل للمدير فقط، شوف SecurityConfig).
 */
@Service
public class ServiceTypeService {

    private final ServiceTypeRepository services;
    private final ServiceRequestRepository requests;

    public ServiceTypeService(ServiceTypeRepository services, ServiceRequestRepository requests) {
        this.services = services;
        this.requests = requests;
    }

    // Everything, including switched-off services, for the staff screens. / الكل بما فيها الموقوفة، لشاشات الموظفين.
    @Transactional(readOnly = true)
    public List<ServiceTypeResponse> listAll() {
        return services.findAllByOrderBySortOrderAscIdAsc().stream().map(ServiceTypeResponse::from).toList();
    }

    // Only what customers can book. / فقط اللي يقدر العميل يحجزه.
    @Transactional(readOnly = true)
    public List<ServiceTypeResponse> listActive() {
        return services.findByActiveTrueOrderBySortOrderAscIdAsc().stream().map(ServiceTypeResponse::from).toList();
    }

    @Transactional
    public ServiceTypeResponse create(ServiceTypeRequest request) {
        if (services.existsByCodeIgnoreCase(request.code())) {
            throw new ApiException(ErrorCode.CODE_EXISTS);
        }
        ServiceType service = new ServiceType(request.code());
        apply(service, request);
        return ServiceTypeResponse.from(services.save(service));
    }

    @Transactional
    public ServiceTypeResponse update(Long id, ServiceTypeRequest request) {
        ServiceType service = find(id);
        // The version is required on edit so two admins cannot overwrite each other silently.
        // رقم النسخة مطلوب في التعديل حتى ما يمسح مديرين تعديل بعض بصمت.
        if (request.version() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("version", "NotNull")));
        }
        Versions.check(service.getVersion(), request.version());
        if (services.existsByCodeIgnoreCaseAndIdNot(request.code(), id)) {
            throw new ApiException(ErrorCode.CODE_EXISTS);
        }
        service.setCode(request.code());
        apply(service, request);
        return ServiceTypeResponse.from(service);
    }

    /**
     * Deletes a service that was never booked. A used service is switched off instead, so old requests keep their name.
     * يحذف خدمة ما انحجزت أبدًا. الخدمة المستخدمة توقَف بدل الحذف، حتى الطلبات القديمة تحتفظ باسمها.
     */
    @Transactional
    public void delete(Long id) {
        ServiceType service = find(id);
        long used = requests.countByServiceType(service);
        if (used > 0) {
            throw new ApiException(ErrorCode.IN_USE, Map.of("requests", used));
        }
        services.delete(service);
    }

    public ServiceType find(Long id) {
        return services.findById(id).orElseThrow(() -> ApiException.notFound("service", id));
    }

    private static void apply(ServiceType service, ServiceTypeRequest request) {
        service.setNameAr(request.nameAr().trim());
        service.setNameEn(request.nameEn().trim());
        service.setDescriptionAr(Text.blankToNull(request.descriptionAr()));
        service.setDescriptionEn(Text.blankToNull(request.descriptionEn()));
        service.setBasePrice(request.basePrice());
        // Round up to the next 30 minutes so every visit fits the calendar grid.
        // نقرّب لأعلى لأقرب 30 دقيقة حتى كل زيارة تركب على شبكة الجدول.
        service.setDurationMinutes((request.durationMinutes() + 29) / 30 * 30);
        service.setSortOrder(request.sortOrder());
        service.setActive(request.active());
    }
}
