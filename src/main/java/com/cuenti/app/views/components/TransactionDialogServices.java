package com.cuenti.app.views.components;

import com.cuenti.app.service.AccountService;
import com.cuenti.app.service.AssetService;
import com.cuenti.app.service.CategoryService;
import com.cuenti.app.service.PayeeService;
import com.cuenti.app.service.ScheduledTransactionService;
import com.cuenti.app.service.TagService;
import com.cuenti.app.service.TransactionService;
import com.cuenti.app.service.VehicleReportService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The services {@link TransactionDialog} and {@link ScheduledTransactionDialog} need, injected as one bean into every view that opens it. */
@Component
@Getter
@RequiredArgsConstructor
public class TransactionDialogServices {
    private final TransactionService transactionService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final AssetService assetService;
    private final PayeeService payeeService;
    private final TagService tagService;
    private final VehicleReportService vehicleReportService;
    private final ScheduledTransactionService scheduledTransactionService;
}
