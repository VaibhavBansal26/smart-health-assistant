package com.healthcare.app.message;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByAppointmentIdAndIdGreaterThanOrderByIdAsc(Long appointmentId, Long afterId);

    long countByAppointmentId(Long appointmentId);
}
