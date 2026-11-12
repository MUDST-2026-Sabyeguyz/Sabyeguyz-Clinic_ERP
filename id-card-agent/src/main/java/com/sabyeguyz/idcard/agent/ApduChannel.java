package com.sabyeguyz.idcard.agent;

import java.io.IOException;

/** One command/response exchange with the card (a PC/SC channel in production, a table in tests). */
interface ApduChannel {
    record Response(byte[] data, int sw) {}

    Response transmit(byte[] command) throws IOException;
}
