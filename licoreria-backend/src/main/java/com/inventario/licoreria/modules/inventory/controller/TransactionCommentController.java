package com.inventario.licoreria.modules.inventory.controller;

import com.inventario.licoreria.modules.inventory.dto.TransactionCommentDTO;
import com.inventario.licoreria.modules.inventory.model.TransactionComment;
import com.inventario.licoreria.modules.inventory.service.TransactionCommentService;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/transactions/{transactionId}/comments")
public class TransactionCommentController {

    private final TransactionCommentService transactionCommentService;

    public TransactionCommentController(TransactionCommentService transactionCommentService) {
        this.transactionCommentService = transactionCommentService;
    }

    @GetMapping
    public ResponseEntity<TransactionCommentDTO> getCommentByTransaction(@PathVariable @NonNull Long transactionId) {
        TransactionCommentDTO dto = transactionCommentService.findByTransactionId(transactionId)
            .map(this::toDTO)
            .orElse(new TransactionCommentDTO(null, transactionId, "", null, null));
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    public ResponseEntity<TransactionCommentDTO> saveComment(@PathVariable @NonNull Long transactionId,
                                                             @RequestBody Map<String, String> body) {
        String comment = body.get("comment");
        if (comment == null) {
            comment = "";
        }

        TransactionComment saved = transactionCommentService.saveOrUpdateComment(transactionId, comment);
        return ResponseEntity.ok(toDTO(saved));
    }

    private TransactionCommentDTO toDTO(TransactionComment comment) {
        return new TransactionCommentDTO(
            comment.getId(),
            comment.getTransaction().getId(),
            comment.getComment(),
            comment.getCreatedAt(),
            comment.getUpdatedAt()
        );
    }
}
