package com.mikaelfrancoeur.springbootstreamsdemo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Gatherers;

@Service
@RequiredArgsConstructor
public class RefundProcessor {

    private static final int REFUND_LIMIT = 10;

    private final Orders orders;
    private final RefundRequests refundRequests;
    private final Refunds refunds;

    public List<RefundRequest> processRefundBatch(int batchSize, String startCursor) {
        List<RefundRequest> collected = orders.all(startCursor)
            .map(Order::id)
            .gather(Gatherers.windowFixed(batchSize))
            .flatMap(refundRequests::forOrders)
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

        refunds.process(collected);
        return collected;
    }
}
