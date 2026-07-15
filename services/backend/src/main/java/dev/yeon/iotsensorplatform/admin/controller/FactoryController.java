package dev.yeon.iotsensorplatform.admin.controller;

import dev.yeon.iotsensorplatform.admin.dto.FactoryRequest;
import dev.yeon.iotsensorplatform.admin.dto.FactoryResponse;
import dev.yeon.iotsensorplatform.admin.service.FactoryService;
import dev.yeon.iotsensorplatform.global.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/factories")
@RequiredArgsConstructor
public class FactoryController {

    private final FactoryService factoryService;

    @PostMapping
    public ResponseEntity<FactoryResponse> create(@RequestBody @Valid FactoryRequest request) {
        return ResponseEntity.ok(factoryService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<FactoryResponse>> getAll() {
        return ResponseEntity.ok(factoryService.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<FactoryResponse> update(@PathVariable Long id,
                                               @RequestBody @Valid FactoryRequest request) {
        return ResponseEntity.ok(factoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Long id) {
        factoryService.delete(id);
        return ResponseEntity.ok(new MessageResponse("공장이 삭제됐어요"));
    }
}
