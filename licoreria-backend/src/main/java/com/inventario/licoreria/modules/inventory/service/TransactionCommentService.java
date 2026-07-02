package com.inventario.licoreria.modules.inventory.service;

import com.inventario.licoreria.modules.inventory.model.Transaction;
import com.inventario.licoreria.modules.inventory.model.TransactionComment;
import com.inventario.licoreria.modules.inventory.repository.TransactionCommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.lang.NonNull;

import java.util.Optional;

@Service
public class TransactionCommentService {

    private final TransactionCommentRepository transactionCommentRepository;
    private final TransactionService transactionService;

    public TransactionCommentService(TransactionCommentRepository transactionCommentRepository, TransactionService transactionService) {
        this.transactionCommentRepository = transactionCommentRepository;
        this.transactionService = transactionService;
    }

    public Optional<TransactionComment> findByTransactionId(@NonNull Long transactionId) {
        return transactionCommentRepository.findByTransactionId(transactionId);
    }

    @Transactional
    public TransactionComment saveOrUpdateComment(@NonNull Long transactionId, String comment) {
        final Transaction transaction = transactionService.findById(transactionId);
        final TransactionComment existing = transactionCommentRepository.findByTransactionId(transactionId).orElse(null);

        if (existing != null) {
            existing.setComment(comment);
            return transactionCommentRepository.save(existing);
        }

        final TransactionComment transactionComment = new TransactionComment();
        transactionComment.setTransaction(transaction);
        transactionComment.setComment(comment);
        return transactionCommentRepository.save(transactionComment);
    }
}
