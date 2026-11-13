package com.sabyeguyz.idcard.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class CardReaderWorker {
    private CardReaderWorker() {}

    public static void main(String[] args) throws Exception {
        var output = new java.io.PrintWriter(System.out, true, StandardCharsets.UTF_8);
        var source = new PcscCardSource();
        if (args.length == 1 && "presence".equals(args[0])) {
            output.println(source.readerPresent() ? "CONNECTED" : "DISCONNECTED");
            return;
        }
        if (args.length != 2 || !"read".equals(args[0])) {
            System.exit(2);
            return;
        }
        var result = source.readWhenPresent(Duration.ofMillis(Long.parseLong(args[1])),
                () -> output.println("READING"));
        var json = new ObjectMapper().registerModule(new JavaTimeModule());
        output.println("RESULT " + json.writeValueAsString(result));
    }
}
