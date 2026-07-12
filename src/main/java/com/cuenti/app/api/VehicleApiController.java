package com.cuenti.app.api;

import com.cuenti.app.api.dto.VehicleReportDTO;
import com.cuenti.app.model.User;
import com.cuenti.app.service.SecurityUtil;
import com.cuenti.app.service.UserService;
import com.cuenti.app.service.VehicleReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleApiController {

    private final VehicleReportService vehicleReportService;
    private final UserService userService;

    @GetMapping("/report")
    public ResponseEntity<?> getReport(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();
        User user = userService.findByUsername(username);

        Long effectiveCategoryId = categoryId != null ? categoryId : user.getDefaultVehicleCategoryId();
        if (effectiveCategoryId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "categoryId required (no default vehicle category set)"));
        }

        LocalDate now = LocalDate.now();
        LocalDate effectiveStart = start != null ? start : now.withDayOfYear(1);
        LocalDate effectiveEnd = end != null ? end : now.with(TemporalAdjusters.lastDayOfYear());

        VehicleReportService.VehicleReport report =
                vehicleReportService.getReport(user, effectiveCategoryId, effectiveStart, effectiveEnd);
        return ResponseEntity.ok(toDTO(report));
    }

    private VehicleReportDTO toDTO(VehicleReportService.VehicleReport r) {
        return VehicleReportDTO.builder()
                .entries(r.getEntries().stream().map(e -> VehicleReportDTO.FuelEntryDTO.builder()
                        .date(e.getDate())
                        .odometer(e.getOdometer())
                        .liters(e.getLiters())
                        .amount(e.getAmount())
                        .currency(e.getCurrency())
                        .station(e.getStation())
                        .memo(e.getMemo())
                        .fullTank(e.isFullTank())
                        .distance(e.getDistance())
                        .pricePerLiter(e.getPricePerLiter())
                        .consumption(e.getConsumption())
                        .build()).collect(Collectors.toList()))
                .totalCost(r.getTotalCost())
                .totalLiters(r.getTotalLiters())
                .totalDistance(r.getTotalDistance())
                .avgConsumption(r.getAvgConsumption())
                .avgPricePerLiter(r.getAvgPricePerLiter())
                .currency(r.getCurrency())
                .build();
    }
}
