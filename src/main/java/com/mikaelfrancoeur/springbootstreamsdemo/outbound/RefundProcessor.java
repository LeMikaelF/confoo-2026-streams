package com.mikaelfrancoeur.springbootstreamsdemo.outbound;

import com.mikaelfrancoeur.springbootstreamsdemo.domain.RefundRequest;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class RefundProcessor {

    public void process(Collection<RefundRequest> refunds) {
        refunds.forEach(refund ->
            System.out.println("Processing refund: " + refund.id() + " for order: " + refund.orderId())
        );
    }
}
