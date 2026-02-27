package com.mikaelfrancoeur.springbootstreamsdemo.domain;

import com.mikaelfrancoeur.springbootstreamsdemo.inbound.Orders;
import com.mikaelfrancoeur.springbootstreamsdemo.inbound.RefundRequests;
import com.mikaelfrancoeur.springbootstreamsdemo.outbound.RefundProcessor;
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
    private final RefundProcessor refundProcessor;

    public List<RefundRequest> execute(int refundsToProcess, String startCursor) {
        List<RefundRequest> collected = orders.all(startCursor)
                .filter(Order::isCompleted)
                .map(Order::id)
                .gather(Gatherers.windowFixed(BATCH_SIZE))
                .flatMap(refundRequests::forOrders)
                .limit(refundsToProcess)
                .toList();

        refundProcessor.process(collected);
        return collected;
    }
}
