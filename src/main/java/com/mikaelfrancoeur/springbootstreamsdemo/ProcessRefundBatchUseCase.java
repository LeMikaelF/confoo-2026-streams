package com.mikaelfrancoeur.springbootstreamsdemo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Gatherers;

@Service
@RequiredArgsConstructor
public class ProcessRefundBatchUseCase {

    public static final int BATCH_SIZE = 5;

    private final Orders orders;
    private final RefundRequests refundRequests;
    private final Refunds refunds;

    public List<RefundRequest> execute(int refundsToProcess, String startCursor) {
        List<RefundRequest> collected = orders.all(startCursor)
            .map(Order::id)
            .gather(Gatherers.windowFixed(BATCH_SIZE))
            .flatMap(refundRequests::forOrders)
            .limit(refundsToProcess)
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
