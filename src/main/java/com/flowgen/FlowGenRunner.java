package com.flowgen;

import com.flowgen.scanner.SpringFlowGen;

public class FlowGenRunner {

    public static void main(String[] args) throws Exception {

        SpringFlowGen.scan("src/main/java")
            .addOutbound("kafkaTemplate")
            .addOutbound("dynamoDbClient")
            .addOutbound("notificationService")
            .addOutbound("auditService")
            .print()
            .outputAll("docs/flows.md")
            .outputEach("docs/flows/individual/");
    }
}
