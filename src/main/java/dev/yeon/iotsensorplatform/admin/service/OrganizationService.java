package dev.yeon.iotsensorplatform.admin.service;

import dev.yeon.iotsensorplatform.admin.dto.OrgRequest;
import dev.yeon.iotsensorplatform.admin.dto.OrgResponse;
import dev.yeon.iotsensorplatform.organization.entity.Organization;
import dev.yeon.iotsensorplatform.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    @Transactional
    public OrgResponse create(OrgRequest request) {
        Organization org = Organization.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        return new OrgResponse(organizationRepository.save(org));
    }

    @Transactional(readOnly = true)
    public List<OrgResponse> getAll() {
        return organizationRepository.findAll().stream()
                .map(OrgResponse::new).toList();
    }

    @Transactional
    public OrgResponse update(Long id, OrgRequest request) {
        Organization org = get(id);
        org.update(request.getName(), request.getDescription());
        return new OrgResponse(org);
    }

    @Transactional
    public void delete(Long id) {
        organizationRepository.delete(get(id));
    }

    private Organization get(Long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 조직이에요"));
    }
}
