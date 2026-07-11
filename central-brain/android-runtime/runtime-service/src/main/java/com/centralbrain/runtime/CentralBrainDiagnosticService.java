package com.centralbrain.runtime;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import com.centralbrain.sdk.CentralBrainSdk;
import com.centralbrain.sdk.diagnostics.DiagnosticPage;
import com.centralbrain.sdk.diagnostics.DiagnosticQuery;
import com.centralbrain.sdk.diagnostics.DiagnosticRecord;
import com.centralbrain.sdk.diagnostics.ICentralBrainDiagnostics;
import com.centralbrain.sdk.production.ICentralBrainRuntime;

/** Read-only R2 diagnostic Binder. Req IDs: XSC-005, XSC-006, NV-G-007, NV-P-002. */
public final class CentralBrainDiagnosticService extends Service {
    public static final String ACCESS_PERMISSION =
            "com.centralbrain.permission.ACCESS_DIAGNOSTICS";

    private static final String TAG = "CentralBrainDiagnostic";

    private final ICentralBrainDiagnostics.Stub binder = new ICentralBrainDiagnostics.Stub() {
        @Override
        public int getProtocolVersion() {
            return ICentralBrainDiagnostics.INTERFACE_VERSION;
        }

        @Override
        public String getProtocolHash() {
            return ICentralBrainDiagnostics.INTERFACE_HASH;
        }

        @Override
        public DiagnosticPage getPage(DiagnosticQuery query) {
            if (query == null || query.schemaVersion != 1) {
                throw new IllegalArgumentException("DiagnosticQuery schemaVersion=1 is required");
            }

            int pageSize = Math.max(1, Math.min(
                    query.pageSize,
                    ICentralBrainDiagnostics.MAX_PAGE_SIZE));
            DiagnosticRecord[] records = records();
            int start = parseCursor(query.cursor, records.length);
            int end = Math.min(records.length, start + pageSize);
            DiagnosticRecord[] pageRecords = new DiagnosticRecord[end - start];
            System.arraycopy(records, start, pageRecords, 0, pageRecords.length);

            DiagnosticPage page = new DiagnosticPage();
            page.records = pageRecords;
            page.hasMore = end < records.length;
            page.nextCursor = page.hasMore ? Integer.toString(end) : "";
            page.generatedAtElapsedRealtimeMs = SystemClock.elapsedRealtime();
            return page;
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "diagnostic binder requested hardware_accessed=false");
        return binder;
    }

    private static int parseCursor(String cursor, int recordCount) {
        if (cursor == null || cursor.isEmpty()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(cursor);
            if (value < 0 || value > recordCount) {
                throw new NumberFormatException("cursor out of range");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid diagnostic cursor");
        }
    }

    private static DiagnosticRecord[] records() {
        return new DiagnosticRecord[] {
                record("protocol", "production", "version=1", ICentralBrainRuntime.INTERFACE_HASH, 1),
                record("protocol", "diagnostic", "version=1", ICentralBrainDiagnostics.INTERFACE_HASH, 2),
                record(
                        "runtime",
                        "maturity",
                        CentralBrainSdk.MATURITY,
                        "hardware_accessed=false;driver_development_triggered=false",
                        3)
        };
    }

    private static DiagnosticRecord record(
            String type,
            String id,
            String summary,
            String detail,
            long sequence) {
        DiagnosticRecord record = new DiagnosticRecord();
        record.recordType = type;
        record.recordId = id;
        record.summary = summary;
        record.detail = detail;
        record.sequence = sequence;
        record.observedAtElapsedRealtimeMs = SystemClock.elapsedRealtime();
        return record;
    }
}
