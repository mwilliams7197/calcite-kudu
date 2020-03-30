package com.twilio.raas.sql;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Queue;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.linq4j.AbstractEnumerable;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calcite implementation layer that represents a result set of a scan.
 */
public final class CalciteKuduEnumerable extends AbstractEnumerable<CalciteRow> {
    private static final Logger logger = LoggerFactory.getLogger(CalciteKuduEnumerable.class);

    private CalciteScannerMessage<CalciteRow> next = null;

    private final BlockingQueue<CalciteScannerMessage<CalciteRow>> rowResults;
    private final AtomicBoolean shouldStop;

    int closedScansCounter = 0;
    boolean finished = false;

    /**
     * Create Enumerable with a Queue of results, a shared integer for scans
     * that have finished and a boolean switch indicating the scan should
     * complete.
     *
     * @param rowResults  shared queue to consume from for all the results
     * @param shouldStop    shared boolean that indicates termination of all scans.
     */
    public CalciteKuduEnumerable(final BlockingQueue<CalciteScannerMessage<CalciteRow>> rowResults,
                                 final AtomicBoolean shouldStop) {
        this.rowResults = rowResults;
        this.shouldStop = shouldStop;
    }

    @Override
    public Enumerator<CalciteRow> enumerator() {
        return new Enumerator<CalciteRow>() {
            @Override
            public boolean moveNext() {
                if (finished) {
                    logger.info("returning finished");
                    return false;
                }
                CalciteScannerMessage<CalciteRow> iterationNext;
                do {
                    try {
                        iterationNext = rowResults.poll(350, TimeUnit.MILLISECONDS);
                    }
                    catch (InterruptedException interrupted) {
                        logger.info("Interrupted during poll, closing scanner");
                        iterationNext = CalciteScannerMessage.createEndMessage();
                    }
                    if (iterationNext != null) {
                        switch (iterationNext.type) {
                        case CLOSE:
                            logger.info("Closing scanner");
                            break;
                        case ERROR:
                            logger.error("Scanner has a failure",
                                iterationNext.failure.get());
                            break;
                        case ROW:
                            logger.trace("Scanner found a row: {}",
                                iterationNext.row.get());
                            break;
                        case BATCH_COMPLETED:
                            logger.info("Batch completed for a scanner. Getting next batch");
                            iterationNext.callback.get().nextBatch();
                        }
                    }

                } while (iterationNext == null ||
                    (iterationNext.type != CalciteScannerMessage.MessageType.CLOSE &&
                        iterationNext.type != CalciteScannerMessage.MessageType.ROW));

                if (iterationNext.type == CalciteScannerMessage.MessageType.CLOSE) {
                    logger.info("No more results in queue, exiting");
                    finished = true;
                    return false;
                }
                next = iterationNext;

                return true;
            }

            @Override
            public CalciteRow current() {
                switch (next.type) {
                case ROW:
                    return next.row.get();
                case ERROR:
                    throw new RuntimeException(next.failure.get());
                case CLOSE:
                    throw new RuntimeException("Calling current() where next is CLOSE message. This should never happen");
                case BATCH_COMPLETED:
                    throw new RuntimeException("Calling current() after receiving a BATCH_COMPLETED message. This should never happen");
                }
                throw new RuntimeException("Fell out of current(), this should not happen");
            }

            @Override
            public void reset() {
                throw new IllegalStateException("Cannot reset Kudu Enumerable");
            }

            @Override
            public void close() {
                shouldStop.set(true);
            }
        };
    }
}
