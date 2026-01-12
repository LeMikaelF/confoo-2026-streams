package com.mikaelfrancoeur.springbootstreamsdemo;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Gatherers;

@Service
public class RefundProcessor {

    private static final int REFUND_LIMIT = 10;
    private static final int BATCH_SIZE = 5;

    private final Orders orders;
    private final RefundRequests refundRequests;
    private final RefundsRepository refundsRepository;

    public RefundProcessor(Orders orders, RefundRequests refundRequests, RefundsRepository refundsRepository) {
        this.orders = orders;
        this.refundRequests = refundRequests;
        this.refundsRepository = refundsRepository;
    }

    public List<RefundRequest> processFirstNRefunds(String startCursor) {
        List<RefundRequest> collected = orders.all(startCursor)
            .map(Order::id)
            .gather(Gatherers.windowFixed(BATCH_SIZE))
            .flatMap(batch -> refundRequests.forOrders(batch))
            .limit(REFUND_LIMIT)
            .reduce(
                new ArrayList<>(),
                (acc, refund) -> {
                    acc.add(refund);
                    return acc;
                },
                (left, right) -> {
                    left.addAll(right);
                    return left;
                }
            );

        refundsRepository.processRefunds(collected);
        return collected;
    }
}
