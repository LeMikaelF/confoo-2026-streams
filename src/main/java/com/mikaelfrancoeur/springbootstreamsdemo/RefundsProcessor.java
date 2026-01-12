package com.mikaelfrancoeur.springbootstreamsdemo;

import java.util.Collection;

class RefundsProcessor {

    void processRefunds(Collection<RefundRequest> refunds) {
        refunds.forEach(refund ->
            System.out.println("Processing refund: " + refund.id() + " for order: " + refund.orderId())
        );
    }
}
