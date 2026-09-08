package com.taskmaster.repository;

import java.util.List;

import com.taskmaster.domain.Attachment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByTaskIdOrderByUploadedAtAsc(Long taskId);

    List<Attachment> findByTaskId(Long taskId);
}
