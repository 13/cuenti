package com.cuenti.app.api;

import com.cuenti.app.api.dto.ForecastDTO;
import com.cuenti.app.model.User;
import com.cuenti.app.service.ForecastService;
import com.cuenti.app.service.SecurityUtil;
import com.cuenti.app.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Year;
import java.util.Map;

@RestController
@RequestMapping("/api/forecasts")
@RequiredArgsConstructor
public class ForecastApiController {

    private final ForecastService forecastService;
    private final UserService userService;

    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR_OFFSET = 50;

    @GetMapping
    public ResponseEntity<?> getForecast(@RequestParam(required = false) Integer year) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();
        User user = userService.findByUsername(username);

        int currentYear = Year.now().getValue();
        int forecastYear = year != null ? year : currentYear;
        int maxYear = currentYear + MAX_YEAR_OFFSET;
        if (forecastYear < MIN_YEAR || forecastYear > maxYear) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "year must be between " + MIN_YEAR + " and " + maxYear));
        }
        return ResponseEntity.ok(forecastService.getForecast(user, forecastYear));
    }
}
