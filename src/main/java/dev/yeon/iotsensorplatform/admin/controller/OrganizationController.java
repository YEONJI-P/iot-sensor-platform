package dev.yeon.iotsensorplatform.admin.controller;

import dev.yeon.iotsensorplatform.admin.dto.OrgRequest;
import dev.yeon.iotsensorplatform.admin.dto.OrgResponse;
import dev.yeon.iotsensorplatform.admin.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    public ResponseEntity<OrgResponse> create(@RequestBody @Valid OrgRequest request) {
        return ResponseEntity.ok(organizationService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<OrgResponse>> getAll() {
        return ResponseEntity.ok(organizationService.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrgResponse> update(@PathVariable Long id,
                                               @RequestBody @Valid OrgRequest request) {
        return ResponseEntity.ok(organizationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        organizationService.delete(id);
        return ResponseEntity.ok("조직이 삭제됐어요");
    }
}
