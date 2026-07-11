package com.centralbrain.sdk.diagnostics;

import com.centralbrain.sdk.diagnostics.DiagnosticPage;
import com.centralbrain.sdk.diagnostics.DiagnosticQuery;

// Diagnostic interface: bounded read-only pages; never accepts task-control commands.
interface ICentralBrainDiagnostics {
    const int INTERFACE_VERSION = 1;
    const String INTERFACE_HASH = "319aaf93eebc5b35b9466952bf97a05bd67465808ed2e3b2ff5bc6cc13342386";
    const int MAX_PAGE_SIZE = 100;

    int getProtocolVersion();
    String getProtocolHash();
    DiagnosticPage getPage(in DiagnosticQuery query);
}
