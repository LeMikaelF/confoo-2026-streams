package com.mikaelfrancoeur.springbootstreamsdemo;

record RefundRequest(String id, String orderId, double amount, String reason) {}
