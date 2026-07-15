package dev.yeon.iotsensorplatform.admin.service;

import dev.yeon.iotsensorplatform.admin.dto.FactoryRequest;
import dev.yeon.iotsensorplatform.admin.dto.FactoryResponse;
import dev.yeon.iotsensorplatform.factory.entity.Factory;
import dev.yeon.iotsensorplatform.factory.repository.FactoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FactoryService {

    private final FactoryRepository factoryRepository;

    @Transactional
    public FactoryResponse create(FactoryRequest request) {
        Factory org = Factory.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        return new FactoryResponse(factoryRepository.save(org));
    }

    @Transactional(readOnly = true)
    public List<FactoryResponse> getAll() {
        return factoryRepository.findAll().stream()
                .map(FactoryResponse::new).toList();
    }

    @Transactional
    public FactoryResponse update(Long id, FactoryRequest request) {
        Factory org = get(id);
        org.update(request.getName(), request.getDescription());
        return new FactoryResponse(org);
    }

    @Transactional
    public void delete(Long id) {
        factoryRepository.delete(get(id));
    }

    private Factory get(Long id) {
        return factoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공장이에요"));
    }
}
