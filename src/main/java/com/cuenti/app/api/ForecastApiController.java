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

@RestController
@RequestMapping("/api/forecasts")
@RequiredArgsConstructor
public class ForecastApiController {

    private final ForecastService forecastService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<ForecastDTO> getForecast(@RequestParam(required = false) Integer year) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();
        User user = userService.findByUsername(username);

        int forecastYear = year != null ? year : Year.now().getValue();
        return ResponseEntity.ok(forecastService.getForecast(user, forecastYear));
    }
}
