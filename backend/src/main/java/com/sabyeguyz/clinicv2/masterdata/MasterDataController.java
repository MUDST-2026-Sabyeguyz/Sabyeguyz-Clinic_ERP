package com.sabyeguyz.clinicv2.masterdata;

import com.sabyeguyz.clinicv2.auth.CurrentActor;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Matches frontend/src/api/client.ts's masterDataApi (/api/master-data),
 * flattening v2's two-table Master_data_type/Master_data_value model back
 * into the old flat {category, code, name} shape. v2's Master_data_value
 * has no "code" column (just value_name) - code is always null here.
 */
@RestController
@RequestMapping("/api/master-data")
public class MasterDataController {
    private final MasterDataService service;
    private final MasterDataTypeRepository types;
    private final MasterDataValueRepository values;
    private final CurrentActor actor;

    public MasterDataController(MasterDataService service, MasterDataTypeRepository types,
                                 MasterDataValueRepository values, CurrentActor actor) {
        this.service = service;
        this.types = types;
        this.values = values;
        this.actor = actor;
    }

    public record Request(@NotBlank String category, String code, @NotBlank String name) {}
    public record Response(Long id, String category, String code, String name, boolean active) {}

    @GetMapping
    public List<Response> list() {
        return values.findAll().stream().map(MasterDataController::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@RequestBody Request request) {
        MasterDataType type = resolveOrCreateType(request.category());
        MasterDataValue value = service.createValue(type, request.name(), null, actor.currentId());
        return toResponse(value);
    }

    @PutMapping("/{id}")
    public Response update(@PathVariable Long id, @RequestBody Request request) {
        MasterDataValue value = values.findById(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบข้อมูล"));
        value.setValueName(request.name());
        value.setValueUpdateAt(Instant.now());
        value.setValueUpdateBy(actor.currentId());
        return toResponse(values.save(value));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        service.deactivateValue(id, actor.currentId());
    }

    @PatchMapping("/{id}/activate")
    public Response activate(@PathVariable Long id) {
        MasterDataValue value = values.findById(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบข้อมูล"));
        value.setValueStatus(true);
        return toResponse(values.save(value));
    }

    private MasterDataType resolveOrCreateType(String category) {
        return types.findByTypeName(category.trim())
                .orElseGet(() -> service.createType(category.trim(), actor.currentId()));
    }

    private static Response toResponse(MasterDataValue value) {
        return new Response(value.getId(), value.getType().getTypeName(), null, value.getValueName(), value.isValueStatus());
    }
}
