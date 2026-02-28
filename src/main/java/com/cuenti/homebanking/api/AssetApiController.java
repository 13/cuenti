package com.cuenti.homebanking.api;

import com.cuenti.homebanking.api.dto.AssetDTO;
import com.cuenti.homebanking.api.dto.DtoMapper;
import com.cuenti.homebanking.model.Asset;
import com.cuenti.homebanking.service.AssetService;
import com.cuenti.homebanking.service.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetApiController {

    private final AssetService assetService;

    @GetMapping
    public ResponseEntity<List<AssetDTO>> getAssets(@RequestParam(required = false) String search) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        List<Asset> assets = search != null
                ? assetService.searchAssets(search)
                : assetService.getAllAssets();

        return ResponseEntity.ok(assets.stream()
                .map(DtoMapper::toAssetDTO)
                .collect(Collectors.toList()));
    }

    @PostMapping
    public ResponseEntity<AssetDTO> createAsset(@RequestBody AssetDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Asset asset = Asset.builder()
                .symbol(dto.getSymbol())
                .name(dto.getName())
                .type(dto.getType())
                .currency(dto.getCurrency())
                .build();

        Asset saved = assetService.saveAsset(asset);
        return ResponseEntity.ok(DtoMapper.toAssetDTO(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AssetDTO> updateAsset(@PathVariable Long id, @RequestBody AssetDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Asset asset = assetService.getAllAssets().stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (asset == null) return ResponseEntity.notFound().build();

        asset.setSymbol(dto.getSymbol());
        asset.setName(dto.getName());
        asset.setType(dto.getType());
        asset.setCurrency(dto.getCurrency());

        Asset saved = assetService.saveAsset(asset);
        return ResponseEntity.ok(DtoMapper.toAssetDTO(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAsset(@PathVariable Long id) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Asset asset = assetService.getAllAssets().stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (asset == null) return ResponseEntity.notFound().build();

        try {
            assetService.deleteAsset(asset);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/refresh-price")
    public ResponseEntity<AssetDTO> refreshPrice(@PathVariable Long id) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Asset asset = assetService.getAllAssets().stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (asset == null) return ResponseEntity.notFound().build();

        assetService.updatePrice(asset);
        return ResponseEntity.ok(DtoMapper.toAssetDTO(asset));
    }
}
