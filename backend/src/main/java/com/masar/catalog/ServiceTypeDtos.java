package com.masar.catalog;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request/response shapes for the service catalogue.
 * أشكال الطلب والرد لقائمة الخدمات.
 */
public final class ServiceTypeDtos {

    private ServiceTypeDtos() {
    }

    // Code: upper-case letters, digits and underscore (e.g. "AC", "PEST_CONTROL"). / الرمز: حروف كبيرة وأرقام وشرطة سفلية.
    public record ServiceTypeRequest(
            @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Z0-9_]+$") String code,
            @NotBlank @Size(max = 100) String nameAr,
            @NotBlank @Size(max = 100) String nameEn,
            @Size(max = 500) String descriptionAr,
            @Size(max = 500) String descriptionEn,
            @NotNull @DecimalMin("0") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal basePrice,
            // Visits are booked in 30-minute steps between half an hour and a full working day.
            // الزيارات تنحجز بخطوات 30 دقيقة، من نص ساعة لين يوم عمل كامل.
            @NotNull @Min(30) @Max(480) Integer durationMinutes,
            @Min(0) @Max(999) int sortOrder,
            boolean active,
            Long version) {
    }

    public record ServiceTypeResponse(Long id, String code, String nameAr, String nameEn, String descriptionAr,
                                      String descriptionEn, BigDecimal basePrice, int durationMinutes, int sortOrder,
                                      boolean active, Long version) {

        public static ServiceTypeResponse from(ServiceType s) {
            return new ServiceTypeResponse(s.getId(), s.getCode(), s.getNameAr(), s.getNameEn(), s.getDescriptionAr(),
                    s.getDescriptionEn(), s.getBasePrice(), s.getDurationMinutes(), s.getSortOrder(), s.isActive(),
                    s.getVersion());
        }
    }

    // The short form shown on requests and jobs. / الشكل المختصر اللي يظهر على الطلبات والأعمال.
    public record ServiceRef(Long id, String code, String nameAr, String nameEn) {

        public static ServiceRef of(ServiceType s) {
            return new ServiceRef(s.getId(), s.getCode(), s.getNameAr(), s.getNameEn());
        }
    }
}
