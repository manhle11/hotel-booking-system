package com.hanbay.hotel.payment.service.impl;

import com.hanbay.hotel.common.query.DashboardTableQuery;
import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.common.validation.PaginationValidator;
import com.hanbay.hotel.payment.domain.Payment;
import com.hanbay.hotel.payment.domain.PaymentRefund;
import com.hanbay.hotel.payment.domain.PaymentRefundStatus;
import com.hanbay.hotel.payment.domain.PaymentTransaction;
import com.hanbay.hotel.payment.domain.PaymentTransactionStatus;
import com.hanbay.hotel.payment.domain.PaymentTransactionType;
import com.hanbay.hotel.payment.dto.request.ConfirmRefundRequest;
import com.hanbay.hotel.payment.dto.response.RefundRequestResponse;
import com.hanbay.hotel.payment.exception.InvalidRefundStateException;
import com.hanbay.hotel.payment.exception.RefundNotFoundException;
import com.hanbay.hotel.payment.repository.PaymentRefundRepository;
import com.hanbay.hotel.payment.service.RefundManagementService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundManagementServiceImpl implements RefundManagementService {

    private static final int MAX_PAGE_SIZE = 100;
    private final PaymentRefundRepository refundRepository;
    private final Clock clock;
    private final DashboardTableQuery tableQuery;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RefundRequestResponse> getPendingRefunds(int page, int size) {
        PaginationValidator.validate(page, size, MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "requestedAt"));
        Page<PaymentRefund> refunds = refundRepository.findAllByStatus(PaymentRefundStatus.PENDING, pageable);
        return PageResponse.from(refunds.map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RefundRequestResponse> getPendingRefunds(int page, int size, String sort) {
        if (sort == null || sort.isBlank()) return getPendingRefunds(page, size);
        return PageResponse.from(tableQuery
                .findAll(
                        DashboardTableQuery.Table.REFUNDS,
                        sort,
                        page,
                        size,
                        refundRepository::findAllById,
                        PaymentRefund::getId)
                .map(this::toResponse));
    }

    @Override
    @Transactional
    public RefundRequestResponse confirmRefund(UUID refundId, ConfirmRefundRequest request) {
        PaymentRefund refund = refundRepository.findByIdForUpdate(refundId).orElseThrow(RefundNotFoundException::new);
        if (refund.getStatus() != PaymentRefundStatus.PENDING) {
            throw new InvalidRefundStateException("Yêu cầu hoàn tiền đã được xử lý");
        }

        Payment payment = refund.getPayment();
        Instant now = clock.instant();
        String bankReference = request.normalizedBankReference();
        PaymentTransaction transaction = payment.getTransactions().stream()
                .filter(item -> item.getType() == PaymentTransactionType.REFUND)
                .filter(item -> item.getStatus() == PaymentTransactionStatus.PENDING)
                .filter(item -> item.getAmount() == refund.getAmount().longValueExact())
                .findFirst()
                .orElseThrow(() -> new InvalidRefundStateException("Không tìm thấy giao dịch hoàn tiền đang chờ"));

        refund.markSuccessful(bankReference, now);
        transaction.markSuccessful(bankReference, now);
        payment.completeRefund();
        return toResponse(refund);
    }

    private RefundRequestResponse toResponse(PaymentRefund refund) {
        Payment payment = refund.getPayment();
        return new RefundRequestResponse(
                refund.getId(),
                payment.getBooking().getBookingCode(),
                payment.getPaymentCode(),
                refund.getAmount(),
                refund.getCurrency(),
                refund.getReason(),
                refund.getStatus(),
                refund.getProviderRefundId(),
                refund.getRequestedAt(),
                refund.getRefundedAt(),
                refund.getBankName(),
                refund.getAccountHolderName(),
                refund.getAccountNumber());
    }
}
