package com.inventario.licoreria.modules.payment_notifications.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.inventario.licoreria.modules.payment_notifications.model.PaymentNotification;

public interface PaymentNotificationRepository extends JpaRepository<PaymentNotification, Long> {

    @Query("SELECT pn FROM PaymentNotification pn WHERE pn.storeId = :storeId ORDER BY pn.receivedAt DESC")
    List<PaymentNotification> findByStoreIdOrderByReceivedAtDesc(@Param("storeId") Long storeId);

    @Query("SELECT pn FROM PaymentNotification pn WHERE pn.storeId = :storeId AND pn.status = :status ORDER BY pn.receivedAt DESC")
    List<PaymentNotification> findByStoreIdAndStatus(@Param("storeId") Long storeId, @Param("status") String status);
}
