package com.masar.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for the service catalogue.
 * الوصول لقائمة الخدمات في قاعدة البيانات.
 */
public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long> {

    List<ServiceType> findAllByOrderBySortOrderAscIdAsc();

    List<ServiceType> findByActiveTrueOrderBySortOrderAscIdAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
